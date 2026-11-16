---
name: 交付与验证偏好
description: 用户在补丁、提交、日志归档等方面的既定做法——幂等补丁、不擅自提交、证据化验收
type: feedback
---

**1. 补丁要幂等，且不硬编码易变量。**
**Why:** PRoot 的 `GIT_TAG` 会随上游升级变动，补丁必须在上游已含该修复时自动跳过。
**How to apply:** 在 `Proot.cmake` 的 `PATCH_COMMAND` 里用 `grep -q <symbol> <file> || patch -p1 --forward < ...` 守卫；新增补丁时沿用此模式。

**2. 不擅自 `git commit` / 不擅自改写历史。**
**Why:** 提交与历史改写是用户的决定；`git reset --soft HEAD~1` 重写未推送的 commit 需用户确认。
**How to apply:** 准备好索引状态（`git add`）并把提交命令写给用户执行；涉及重写历史时明确说明"因尚未推送，重写安全"并给出回退方式。

**3. 验收要给出可复现的量化指标，而不是"看起来正常"。**
**Why:** 该项目历史上有"启动失败但日志看着有 activity"的误判；`ZygoteInit` 计数就曾因堆栈假阳性把 1 次误读成 7 次。
**How to apply:** 每轮给"修复前/修复后"对照表，指标用 grep 计数（如 `Boot is finished`、`terminated with signal 31`、`seccomp trap for syscall`），并说明每项的期望值。

**4. 设备日志导出到被 git 忽略的目录，不进版本库。**
**Why:** 曾误提交 19801 行日志进仓库，需要重写提交清理。
**How to apply:** 导出到 `logs-export/vN/`；新增此类产物时同步在 `.gitignore` 加规则。

**5. 主动指出工程卫生问题。**
**Why:** 用户接受这类建议（如清理误提交、提示 `run.sh` 权限位、提示 ROM 侧改动需重打包才生效）。
**How to apply:** 汇报时列出"待处理项"并给出选项，而不是自行扩大改动范围。
