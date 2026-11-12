# Preview runtime isolation decision

Status: required architecture gate; not yet implemented.

## Decision

Generated preview code must never execute in the editor's normal application process. A DexClassLoader changes class lookup; it does not create a security boundary. Android's security guidance states that dynamically loaded code runs with the hosting application's permissions. The editor must therefore use a dedicated Android service declared with android:isolatedProcess="true", android:exported="false", and a narrow Binder contract.

The isolated service has no permissions of its own. It cannot be treated as another process with ordinary access to the editor's private workspace. The editor must explicitly transfer only the inputs needed for one preview request. An open read-only file descriptor or bounded IPC stream can be used for the compiled artifact; never pass workspace paths and assume the isolated UID can open them.

## Required request boundary

The future IPC request should contain only:
- A unique build/request ID and source snapshot hash.
- A read-only handle or bounded transfer for the DEX artifact.
- The selected generated preview class and entry-point identifier.
- A small, versioned set of preview configuration values.
- Explicit size, time and cancellation limits.

It must not expose arbitrary file APIs, shell execution, network access, editor preferences, credentials, or unrestricted workspace access. The host must reject stale request IDs and validate every response before applying it to the visible preview.

## DEX file handling

The app currently targets API 36. For apps targeting Android 14 / API 34 or newer, dynamically loaded DEX/JAR/APK files must be read-only before they are loaded; otherwise Android can reject loading. The D8 output should therefore be written to a temporary build artifact, copied into its final protected internal location using a write path that marks the destination read-only before writing its bytes, closed, and integrity-checked before transfer to the isolated service. Do not use external/shared storage for executable artifacts.

A hash detects accidental corruption and supports stale-artifact checks; it is not a substitute for a trusted producer or a sandbox.

## Lifecycle and resource controls

- Bind only from the owning editor process; keep the service non-exported.
- Treat service death as a failed preview, not a reason to retry in the editor process.
- Enforce a single active compile/load request initially; cancel superseded requests.
- Bound DEX size, IPC payloads, preview dimensions, execution time and memory use.
- Do not allow preview code to invoke arbitrary Android components or host callbacks.
- Return structured diagnostics and render output through a narrow host-controlled surface protocol.
- Verify behavior on API 26 and the current target API, including Android 14+ read-only DEX enforcement.

## Consequences

This is more work than loading DEX in-process, and transferring code and preview surfaces needs a deliberate protocol. That cost is intentional: user-authored Kotlin is executable code. Until the isolated service, IPC contract and device tests exist, the compiler-backed runtime stage must remain unavailable. The bounded source interpreter remains the only active preview path.

## References

- Android dynamic code loading security: https://developer.android.com/privacy-and-security/risks/dynamic-code-loading
- Android security checklist (same-permission behavior of dynamically loaded code): https://developer.android.com/privacy-and-security/security-tips
- Android service manifest and isolatedProcess: https://developer.android.com/guide/topics/manifest/service-element
- Android 14 safer dynamic code loading behavior: https://developer.android.com/about/versions/14/behavior-changes-14#safer-dynamic-code-loading
- DexClassLoader API reference: https://developer.android.com/reference/dalvik/system/DexClassLoader