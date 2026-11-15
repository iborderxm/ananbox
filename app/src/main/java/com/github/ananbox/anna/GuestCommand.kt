package com.github.ananbox.anna

import java.io.File

/**
 * Builds the proot invocation used to run a command inside the guest.
 *
 * This mirrors the binds/env of the ROM's `run.sh` so the second proot
 * instance sees the same rootfs, sockets and binder device as the booted
 * system, which lets it reach the guest's running services.
 */
object GuestCommand {

    const val GUEST_SHELL = "/system/bin/sh"

    data class Spec(
        val executable: String,
        val args: List<String>,
        val env: Map<String, String>,
    )

    fun build(
        rootfs: File,
        prootBinary: File,
        tmpDir: File,
        command: String,
        seccompEnabled: Boolean = false,
    ): Spec {
        val dir = rootfs.parentFile ?: rootfs
        val binds = listOf(
            "/dev",
            "/proc",
            "/sys",
            "${rootfs.path}/dev/kmsg:/dev/kmsg",
            "${rootfs.path}/dev/pmsg0:/dev/pmsg0",
            "${rootfs.path}/system/vendor:/vendor",
            "${rootfs.path}/dev/__properties__:/dev/__properties__",
            "${rootfs.path}/dev/socket:/dev/socket",
            "/dev/binder:/dev/binder",
            "/dev/ashmem:/dev/ashmem",
            "${dir.path}/qemu_pipe:/dev/qemu_pipe",
            "${rootfs.path}/dev/input:/dev/input",
            "${rootfs.path}/mnt/user/0:/storage/self"
        )

        val args = mutableListOf(prootBinary.path, "--kill-on-exit", "-r", rootfs.path, "-0", "-w", "/")
        for (bind in binds) {
            args += "-b"
            args += bind
        }
        args += GUEST_SHELL
        args += "-c"
        args += command

        val env = mutableMapOf(
            "PATH" to "/sbin:/system/bin:/system/sbin:/system/xbin:/system/vendor/bin",
            "ANDROID_ASSETS" to "/assets",
            "ANDROID_DATA" to "/data",
            "ANDROID_ROOT" to "/system",
            "ANDROID_STORAGE" to "/storage",
            "ASEC_MOUNTPOINT" to "/mnt/asec",
            "EXTERNAL_STORAGE" to "/sdcard",
            "PROOT_TMP_DIR" to tmpDir.path
        )
        // PRoot's seccomp accelerator stacks a second seccomp-bpf filter on
        // top of the one Android already installs for every app process. On
        // hosts such as 5.10.43 it fails with EPERM and delivers SIGSYS to the
        // tracee ("bad syscall ... Permission denied"), killing zygote during
        // class preload. This setting is shared with the container's proot,
        // which receives it through the host process environment.
        if (!seccompEnabled) env["PROOT_NO_SECCOMP"] = "1"

        return Spec(prootBinary.path, args, env)
    }
}
