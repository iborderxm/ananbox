---
name: ananbox-fork 项目背景与目标
description: Ananbox fork（rootless Android 容器）项目的目标、当前进展与未完成事项
type: project
---

`d:\git\ananbox-fork` 是 Ananbox 的 fork（远端 `git@github.com:iborderxm/ananbox.git`），目标是在**真实 Android 手机上无 root 运行 Android 容器**（guest AOSP ROM + PRoot 隔离 + 修改版 Anbox 提供 Binder/图形/输入）。

**Why:** 目标是"能在自己手机上跑起来的、源码完全可控（FOSS）的 Android 容器"，并且要能用 Anna HTTP 网关远程控制、投屏、装应用。

**当前状态（截至 2026-11-16）：**
- guest Android 已**首次成功完整启动**：Zygote 只启动 1 次、`Boot is finished`、`sys.boot_completed=1`、`appCount=68`。
- 关键阻断项是宿主 seccomp 拦截 PRoot 模拟的 `setregid/setreuid/setresgid` → Zygote 反复重启（31 次）→ 已用 `app/src/main/cpp/patches/proot-sigsys.patch` 修复并验证。
- 已合入上游 Ananbox 快照 `f66af098`（新增 `android/wifi_hal/`，ROM 打开 `android.hardware.wifi` feature）。

**未完成事项（用户尚未确认是否要继续）：**
1. `app/src/main/cpp/anbox/scripts/run.sh` 里 PRoot 用 `-v 255`，`proot.log` 几分钟涨到 >100 MB，建议降级。注意该脚本被打包进 ROM 的 `/root/run.sh`，改源码后还需重打 ROM 才在设备生效。
2. 模拟 Wi-Fi 驱动未实现 `BSS RANGE` 命令 → guest 扫描结果为空，`WifiConnectivityManager` 反复触发看门狗重扫。
3. ROM 发布地址与 fork 不一致：`RomCatalog.RELEASE_BASE` 指向 `rongyaoidea/ananbox-fork` 的 `rom-v0.0.2` 资源，而本 fork 远端是 `iborderxm/ananbox`。

**How to apply:** 涉及"启动失败/zygote 重启/日志暴涨/WiFi 扫描"时先查上面的已知项；提交前确认是否需要同步改 ROM 侧（`app/src/main/cpp/anbox/`）而不只是 App 侧。
