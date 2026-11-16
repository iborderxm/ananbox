---
name: 设备调试与日志取证流程
description: 通过 adb 无线调试与 Anna 网关定位设备、抓取 guest 日志、验证容器健康状态的完整命令集
type: reference
---

**设备**：华为 LIO-AN00（arm64，Android 10，内核 5.10.43）。adb 无线调试 serial 形如 `192.168.43.1:5555`。

**注意地址**：手机开了热点，`wlan0` 是 `192.168.43.1`，`192.168.43.114` 是 **PC** 的地址。用户常把两者搞反——网关地址要用 `http://192.168.43.1:7749`。手机 IP 会随所连热点变化，每次先确认。

**Anna 网关**（端口 7749，`anna_allow_lan=true` 时绑 `0.0.0.0`）：
- `curl -s http://<手机IP>:7749/v1/status` → `appCount` / `rootfsReady` / `boot` 状态
- `curl -s -X POST http://<手机IP>:7749/v1/exec -H 'Content-Type: application/json' -d '{"command":"<cmd>","timeoutMs":20000}'` → 在 guest 内执行命令
- LAN 不可用时回退：`adb forward tcp:7749 tcp:7749` 后访问 `http://127.0.0.1:7749`

**日志取证**（app 私有目录，需 `run-as`）：
```
adb shell 'run-as com.github.ananbox ls -l files/proot.log files/rootfs/data/system.log'
adb shell "run-as com.github.ananbox grep -c 'proot error' files/proot.log"
adb shell "run-as com.github.ananbox grep -c 'terminated with signal 31' files/proot.log"
adb shell "run-as com.github.ananbox grep -c 'seccomp trap for syscall' files/proot.log"
adb shell "run-as com.github.ananbox grep -c 'START com.android.internal.os.ZygoteInit' files/rootfs/data/system.log"
adb shell "run-as com.github.ananbox grep -c 'Boot is finished' files/rootfs/data/system.log"
```

**PowerShell 陷阱**：本机 shell 是 PowerShell，`cmd /c '...'` 的引号与变量转义经常出错；涉及 adb 重定向到文件时改用 `[System.IO.File]::WriteAllLines($path, (& $adb ...), $utf8NoBom)`。`adb exec-out` 优于 `adb shell`（后者会做 CRLF 转换）。

**导出归档**：拉到 `logs-export/vN/`（已被 git 忽略），v2~v5 对应不同轮次。

**网关启动**：`AnnaService` 未 exported，adb 无法直接 `am start`；若网关未起，先在设置里开启，或确认 `anna_gateway_enabled` 偏好。
