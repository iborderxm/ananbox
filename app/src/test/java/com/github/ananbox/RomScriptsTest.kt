package com.github.ananbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The ROM start script has a single source (`src/main/cpp/anbox/scripts/run.sh`)
 * that is copied both into the ROM image and into the APK assets. This test
 * guards the two copies against drifting apart.
 */
class RomScriptsTest {

    private fun bundledRunSh(): String {
        val loader = javaClass.classLoader
            ?: throw AssertionError("test class loader is unavailable")
        // Depending on the AGP version the assets are exposed either directly
        // or below an "assets/" prefix.
        val stream = loader.getResourceAsStream(RomScripts.RUN_SH_ASSET)
            ?: loader.getResourceAsStream("assets/${RomScripts.RUN_SH_ASSET}")
        assertNotNull("run.sh asset is missing from the APK", stream)
        return stream!!.bufferedReader().use { it.readText() }
    }

    @Test
    fun bundledRunShMatchesTheRomSource() {
        val source = File("src/main/cpp/anbox/scripts/run.sh")
        assertTrue("ROM run.sh source is missing", source.isFile)
        assertEquals(source.readText(), bundledRunSh())
    }

    @Test
    fun bundledRunShLaunchesProotWithTheGuestInit() {
        val bundled = bundledRunSh()
        // The container is started with "sh <rootfs>/run.sh <dir> <proot>",
        // so the script must take both as positional arguments.
        assertTrue("run.sh must consume \$1 as the root directory", bundled.contains("DIR=\$1"))
        assertTrue("run.sh must invoke proot with \$2", bundled.contains("\$2 --kill-on-exit"))
        assertTrue("run.sh must start the guest init", bundled.contains("/init"))
    }
}