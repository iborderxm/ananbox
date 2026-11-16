---
name: PRoot seccomp SIGSYS 修复的来龙去脉
description: 宿主 seccomp 拦截 set*id 导致 Zygote 反复重启的根因、修复方案位置与验证指标
type: project
---

**根因**：Android 宿主给 app 进程装了 seccomp filter，它在 PRoot 自己的 filter **之前**求值。平台拒绝的 syscall（arm64 `setregid`=143、`setreuid`=145、`setresgid`=149）在 zygote 预加载 class 阶段触发 `SIGSYS`（`si_code == SYS_SECCOMP`）。PRoot 原本把 SIGSYS 原样转发，导致 Zygote 被杀、init 无限重启（曾观测到 31 次重启、155 次 signal 31 终止）。

**修复位置**：不在本仓库源码，而在 PRoot 上游 `Ananbox/proot`（pin 在 `GIT_TAG 8c60d02`）的 `src/tracee/event.c`。本仓库通过 `app/src/main/cpp/patches/proot-sigsys.patch` + `Proot.cmake` 里的幂等 `PATCH_COMMAND` 应用。

**修复逻辑**：`handle_seccomp_trap()` 接管 SIGSYS → `fetch_regs` → `poke_reg(SYSARG_RESULT, ...)` → `push_regs` → 吞掉信号并 `PTRACE_CONT`。凭证类 set*id 伪造返回 **0**（fake root 下应成功），其余 syscall 返回 **-ENOSYS**。同时把误导性的 `note(ERROR, SYSTEM, "bad syscall...")` 改为中性 INFO——旧写法会附上残留 errno，看起来像 "Permission denied"，其实与真实原因无关。

**关键实现细节**：`detranslate_sysnum(get_abi(tracee), PR_setregid)` 做跨架构比对，不能硬编码 syscall 号。

**验证指标**（每轮回归必查）：
- `grep -c 'seccomp trap for syscall' files/proot.log` → 小非零值（实测 26~27 次）
- `grep -c 'terminated with signal 31'` → **0**
- `grep -c 'proot error'` → **0**
- `grep -c 'START com.android.internal.os.ZygoteInit'` → **1**（注意：直接 grep `ZygoteInit` 会多算异常堆栈里的 `ZygoteInit$MethodAndArgsCaller.run`，实测 7 次里有 6 次是假阳性）
- `grep -c 'Boot is finished'` → 1

**How to apply**: 升级 PRoot 的 `GIT_TAG` 后必须重跑上述指标；若上游已合入该修复，`grep -q handle_seccomp_trap` 守卫会自动跳过补丁，无需改 `Proot.cmake`。
