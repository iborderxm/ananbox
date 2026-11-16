# AGENT.md

Guidance for AI agents (and humans) working in this repository.

## What this project is

`ananbox-fork` is a fork of [Ananbox](https://github.com/Ananbox/ananbox): a **rootless
Android container running on Android**. A guest Android ROM runs inside the app's
private storage; a modified PRoot provides the filesystem/UID isolation, and a
patched Anbox provides the Binder bridge, graphics (hwcomposer) and input.

Two deliverables come out of one repo:

| Artifact | Built by | Shipped as |
|---|---|---|
| Host app + native layer | Gradle + NDK/CMake | `app/build/outputs/apk/debug/*.apk` |
| Guest ROM | AOSP `make` against `app/src/main/cpp/anbox/products/` | `rootfs_arm64.7z` |

## Repository layout

```
app/src/main/java/com/github/ananbox/     Host app (Kotlin)
  MainActivity.kt        ROM download / extraction, app lifecycle
  SettingsActivity.kt    Gateway + LAN/port/exec preferences
  BinderBridge.kt        Emits localBroadcastIntent / binderBroadcastIntent / trans_code
  RomCatalog.kt          Published ROM assets (URL + SHA-256) for this fork
  RomDownloader.kt       ROM download with size + SHA-256 verification
  StorageProvider.kt     Host storage provider for the guest
  anna/                  "Anna" gateway: HTTP API for remote control
    AnnaCore.kt          Lifecycle, preferences, gateway start/stop
    AnnaRoutes.kt        All /v1/* endpoints
    AnnaHttpServer.kt    Minimal HTTP server (no third-party deps)
    GuestShell.kt        Runs shell commands inside the guest
    GuestInventory.kt    Lists installed guest apps
    ScreenGrabber.kt     Frame capture
    InputInjector.kt     Tap / swipe / text / key injection
    PathGuard.kt         Blocks path traversal on fs endpoints

app/src/main/cpp/
  CMakeLists.txt         Root native build (C++14, cmake 3.22.1)
  anbox/                 Vendored Anbox source (upstream snapshot, periodically upgraded)
  cmake/Proot.cmake      Builds PRoot via ExternalProject + applies local patches
  cmake/Talloc.cmake     talloc ExternalProject (URL list + cache friendly)
  cmake/Unwind.cmake     libunwind ExternalProject
  cmake/Boost.cmake      Boost ExternalProject
  patches/               Patches applied to vendored/external sources at build time
  tests/                 Host-side C++ tests (binder manifest)

tools/anna-mcp/          Python MCP bridge exposing the Anna gateway to agents
logs-export/             Local device log dumps (git-ignored, never commit)
```

`app/src/main/cpp/anbox/external/` is vendored upstream code, kept in-tree.

## Build

```bash
# Full build (both ABIs, needs NDK 23.1.7779620 + cmake 3.22.1)
./gradlew :app:assembleDebug

# Fast feedback: Kotlin/resources/manifest only, no NDK
./gradlew :app:assembleDebug -PskipNative
./gradlew :app:testDebugUnitTest -PskipNative
```

`skipNative` is defined in the root `build.gradle`; it strips `abiFilters` and
`externalNativeBuild` so the APK builds without an NDK toolchain.

ABI filters: `x86_64`, `arm64-v8a`. `compileSdk 33`, `targetSdk 28`, `minSdk 23`.

### PRoot integration (important)

PRoot is **not** vendored. `app/src/main/cpp/cmake/Proot.cmake` fetches it with
`ExternalProject_Add` from `https://github.com/Ananbox/proot.git` at a pinned
`GIT_TAG`, then:

- builds it with the NDK clang as a cross-compile (host swig/python are disabled
  via `HAS_SWIG= HAS_PYTHON_CONFIG=`, otherwise the cross build breaks);
- installs the binary as `libproot.so` (only `lib*.so` can be packed into an APK);
- applies local patches from `app/src/main/cpp/patches/`.

Patches are applied **idempotently** via a `grep` guard in `PATCH_COMMAND`, so
bumping `GIT_TAG` to a revision that already contains the fix stays harmless:

```cmake
PATCH_COMMAND sh -c "grep -q handle_seccomp_trap src/tracee/event.c || patch -p1 --forward < ${CMAKE_SOURCE_DIR}/patches/proot-sigsys.patch"
```

When adding a PRoot patch: keep the `grep -q <symbol>` guard, so the patch is
skipped if upstream later ships the same change.

## CI

`.github/workflows/ci.yml` has four jobs:

| Job | Purpose |
|---|---|
| `compile-check` | `./gradlew :app:assembleDebug -PskipNative` + unit tests (fast) |
| `native-build` | Full NDK build of both ABIs (the one that matters for native changes) |
| `mcp-selftest` | `python3 -m unittest discover -s tools/anna-mcp` |
| `cpp-selftest` | Compiles and runs `app/src/main/cpp/tests/binder_manifest_test.cpp` |

`native-build` caches the Boost source tree and the talloc archive
(`app/src/main/cpp/.cache/downloads`). A native change is only really verified
once `native-build` passes — there is no local NDK/CMake on the dev machine.

## Anna gateway (host-side HTTP API)

Default port **7749**, bound to `127.0.0.1` unless `anna_allow_lan` is on.

| Method | Path | Purpose |
|---|---|---|
| GET | `/v1/ping` | Liveness + API version |
| GET | `/v1/status` | `appCount`, `rootfsReady`, `surfaceAttached`, display size, `exec` |
| GET | `/v1/screen` | Screen capture |
| POST | `/v1/input/{tap,swipe,text,key}` | Input injection |
| GET | `/v1/apps`, `/v1/apps/{pkg}` | Guest app inventory / detail |
| POST | `/v1/apps/install`, `/v1/apps/{pkg}` | Install / lifecycle |
| GET | `/v1/fs/{list,read}` | Guest filesystem browse (path-guarded) |
| POST | `/v1/fs/{file,mkdir,delete}` | Guest filesystem write |
| POST | `/v1/exec` | Run a command inside the guest |
| GET | `/v1/logs` | Tail guest logs |

Preferences (`SharedPreferences`, key `com.github.ananbox_preferences`):
`anna_gateway_enabled`, `anna_allow_lan`, `anna_exec_enabled`, `anna_port`.
`MainActivity` and `AnnaService` both call `AnnaCore.init(...)`; the service is
the one that survives the activity going away.

## Device / debug workflow

Host paths on the device (app-private, readable via `run-as com.github.ananbox`):

```
files/rootfs/                          extracted guest ROM
files/rootfs/data/system.log           guest logcat
files/proot.log                        PRoot's own log
files/rootfs/localBroadcastIntent      BinderBridge dumps (host -> guest)
files/rootfs/binderBroadcastIntent
files/rootfs/trans_code
```

Useful commands:

```bash
adb shell 'run-as com.github.ananbox ls -l files/proot.log'
adb shell "run-as com.github.ananbox grep -c 'proot error' files/proot.log"
adb shell "run-as com.github.ananbox grep -c 'START com.android.internal.os.ZygoteInit' files/rootfs/data/system.log"
adb shell "run-as com.github.ananbox grep -c 'Boot is finished' files/rootfs/data/system.log"
```

Gateway over adb when LAN binding is unavailable:

```bash
adb forward tcp:7749 tcp:7749
curl -s http://127.0.0.1:7749/v1/status
```

Note the guest runs in a **different network namespace** than the host: its
`wlan0` is `192.168.43.1` (a QEMU-style net) and DNS is `10.0.2.3`, which is
unrelated to the phone's real Wi-Fi.

## Known issues and traps

- **Host seccomp vs. PRoot's own filter.** The Android host installs a seccomp
  filter on app processes that is evaluated *before* PRoot's own filter. A
  syscall the platform refuses (arm64 `setregid` = 143, `setreuid` = 145,
  `setresgid` = 149) raises `SIGSYS` with `si_code == SYS_SECCOMP` even though
  PRoot would have emulated it. Without handling it, the guest Zygote dies and
  `init` restarts it in a loop. Fixed by
  `app/src/main/cpp/patches/proot-sigsys.patch`; the handler answers the
  credential calls with success and everything else with `-ENOSYS`, then
  swallows the signal.
  Verify with: `grep -c 'seccomp trap for syscall' files/proot.log` (expect a
  small non-zero count) and `grep -c 'terminated with signal 31'` (expect 0).
- **`proot.log` grows very fast.** The guest launch script
  (`app/src/main/cpp/anbox/scripts/run.sh`, installed to `/root/run.sh` in the
  ROM) runs PRoot with `-v 255`, producing >100 MB of log in minutes. Lower the
  verbosity if storage matters.
- **`WifiHW` E-level noise.** `REDF-LOG ...` lines tagged `E` are the
  simulated Wi-Fi driver's normal event stream, not real failures. The real
  gap is that the `BSS RANGE` command is unimplemented, so the guest's scan
  results stay empty.
- **`libGLES_emulation.so` missing** and the `google.vorbis.decoder`
  `BadParameter` errors are expected in a container without the full emulator
  stack; they are not regressions.
- **Guest `dmesg` fails** with `klogctl: Function not implemented` — the host
  blocks `syslog(2)`. The PRoot patch answers it with `-ENOSYS` instead of
  letting the signal kill the process.

## Conventions

- Commit messages in this repo are written in **Chinese** and describe the fix,
  not the mechanics.
- `logs-export/` is git-ignored; device log dumps are local artifacts and must
  never be committed.
- The vendored `app/src/main/cpp/anbox/` tree is upgraded by replacing it with
  an upstream snapshot and keeping the local deltas minimal; the upstream
  reference commit is cited in the commit message.
- Verify native changes in CI (`native-build`), not locally — the dev machine
  has no NDK/CMake toolchain.
