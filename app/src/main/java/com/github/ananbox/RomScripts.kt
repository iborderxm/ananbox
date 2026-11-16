package com.github.ananbox

import android.content.Context
import java.io.IOException

/**
 * Access to the files the App ships for the guest.
 *
 * The ROM start script lives in `src/main/cpp/anbox/scripts/run.sh`: the ROM
 * build copies it to `root/run.sh`, and the build copies the same file into
 * the APK assets so that a ROM archive without one can still boot. See
 * `copyRomRunScript` in app/build.gradle.
 */
object RomScripts {

    const val RUN_SH_ASSET = "run.sh"

    /**
     * Reads the start script bundled in the APK.
     *
     * @throws IOException when the asset is missing from the APK, which
     *         would mean the build did not run `copyRomRunScript`.
     */
    @Throws(IOException::class)
    fun readRunSh(context: Context): String =
        context.assets.open(RUN_SH_ASSET).bufferedReader().use { it.readText() }
}