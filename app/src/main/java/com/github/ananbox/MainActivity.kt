package com.github.ananbox

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.ananbox.anna.AnnaCore
import com.github.ananbox.anna.AnnaService
import com.github.ananbox.databinding.ActivityMainBinding
import com.hzy.libp7zip.P7ZipApi
import java.io.File
import java.io.IOException
import java.lang.String
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.system.exitProcess


class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private val READ_REQUEST_CODE = 2
    private lateinit var mSurfaceView: SurfaceView
    private val mSurfaceCallback: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            val surface = holder.surface
            val windowManager = windowManager
            val defaultDisplay = windowManager.defaultDisplay
            val displayMetrics = DisplayMetrics()
            defaultDisplay.getRealMetrics(displayMetrics)
            val dpi = displayMetrics.densityDpi
            Log.i(TAG, "Runtime initializing..")
            if(Anbox.initRuntime(mSurfaceView.width, mSurfaceView.height, dpi)) {
                Anbox.createSurface(surface)
                AnnaCore.attachSurface(mSurfaceView)
                Anbox.startRuntime()
                Anbox.startContainer(applicationContext.applicationInfo.nativeLibraryDir + "/libproot.so")
            }
            else {
                Anbox.createSurface(surface)
                AnnaCore.attachSurface(mSurfaceView)
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            Log.i(
                TAG,
                "surfaceChanged: " + mSurfaceView.width + "x" + mSurfaceView.height
            )
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
//            Renderer.removeWindow(holder.surface)
            AnnaCore.detachSurface()
            Anbox.destroySurface()
            Log.i(TAG, "surfaceDestroyed!")
        }
    }

    private lateinit var binding: ActivityMainBinding
    private val receiver = BinderReceiver()
    private val handlerThread = HandlerThread("BinderReceiverThread")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handlerThread.start()
        ContextCompat.registerReceiver(this ,receiver,
            IntentFilter("com.github.ananbox.BINDER"),
            null, Handler(handlerThread.looper),
            ContextCompat.RECEIVER_EXPORTED)

        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!File(filesDir, "rootfs").exists()) {
            showRomInstaller()
            return
        }

        Anbox.setPath(filesDir.path)

        AnnaCore.init(applicationContext, File(filesDir, "rootfs"))
        if (File(filesDir, "rootfs").isDirectory) {
            BinderBridge.prepare(File(filesDir, "rootfs"))
        }

        mSurfaceView = SurfaceView(this)
        mSurfaceView.getHolder().addCallback(mSurfaceCallback)
        binding.root.addView(mSurfaceView, 0)

        // put in onResume?
        mSurfaceView.setOnTouchListener(Anbox)
        binding.fab.setOnClickListener {
            startActivity(Intent(applicationContext, SettingsActivity::class.java))
        }

        if (AnnaCore.prefs().getBoolean(AnnaCore.PREF_GATEWAY_ENABLED, false)) {
            AnnaService.start(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1);
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AnnaCore.detachSurface()
        Anbox.stopRuntime()
        unregisterReceiver(receiver)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == READ_REQUEST_CODE) {
            if (resultCode != Activity.RESULT_OK || data == null) {
                finishAffinity()
                return
            }
            val uri = data.data ?: return
            val progressDialog = showExtractingDialog()
            thread {
                val romFile = File(filesDir, "rom-archive")
                contentResolver.openInputStream(uri)?.use { input ->
                    romFile.outputStream().use { output -> input.copyTo(output) }
                }
                extractRom(romFile, progressDialog)
            }
        }
    }

    private fun showRomInstaller() {
        val asset = RomCatalog.forAbi(Build.SUPPORTED_ABIS.firstOrNull())
        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.rom_installer_title))
            .setMessage(
                getString(
                    if (asset != null) R.string.rom_installer_message_download
                    else R.string.rom_installer_message
                )
            )
            .setNeutralButton(R.string.rom_installer_install) { _: DialogInterface, _: Int -> pickRomFile() }
            .setNegativeButton(R.string.cancel) { _: DialogInterface, _: Int ->
                finishAffinity()
                exitProcess(0)
            }
            .setCancelable(false)
        if (asset != null) {
            builder.setPositiveButton(R.string.rom_installer_download) { _: DialogInterface, _: Int ->
                downloadRom(asset)
            }
        }
        builder.show()
    }

    private fun pickRomFile() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                // Both .7z and .tar.gz ROMs are accepted. The archive format is
                // detected from the file content, not from the extension, so a
                // file whose MIME type is reported generically still works.
                setType("*/*")
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf(
                        "application/x-7z-compressed",
                        "application/gzip",
                        "application/x-tar",
                    )
                )
            },
            READ_REQUEST_CODE
        )
    }

    private fun downloadRom(asset: RomCatalog.RomAsset) {
        val progressDialog = ProgressDialog(this).apply {
            setTitle(getString(R.string.rom_installer_downloading_title))
            setMessage(getString(R.string.rom_installer_downloading_msg))
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCanceledOnTouchOutside(false)
            show()
        }
        thread {
            val target = File(filesDir, "rom-archive")
            val ok = RomDownloader.download(asset, target) { progress ->
                runOnUiThread {
                    if (progress.percent >= 0) {
                        progressDialog.progress = progress.percent
                    }
                }
            }
            runOnUiThread {
                progressDialog.dismiss()
                if (ok) {
                    val extractDialog = showExtractingDialog()
                    thread { extractRom(target, extractDialog) }
                } else {
                    showDownloadFailed()
                }
            }
        }
    }

    private fun extractRom(romFile: File, progressDialog: ProgressDialog) {
        val tmpDir = File(filesDir, "tmp")
        val cpu = Runtime.getRuntime().availableProcessors()
        // Unpack into a staging directory first. That lets us tell whether
        // the archive carries a top-level "rootfs/" wrapper, lets a nested
        // .tar be unpacked without polluting filesDir, and keeps a failed
        // extraction from leaving the guest half-updated.
        val staging = File(filesDir, "rom-staging")
        staging.deleteRecursively()
        staging.mkdirs()
        P7ZipApi.executeCommand(
            String.format(
                Locale.US, "7z x -mmt=%d -aoa '%s' '-o%s'",
                cpu, romFile.absolutePath, staging.absolutePath
            )
        )
        // A .tar.gz may end up as a single .tar file when 7-Zip does not chain
        // the gzip and tar decoders. Unpack it in that case.
        unpackNestedTar(staging, cpu)
        val extracted = normalizeRootfs(staging)
        val target = File(filesDir, "rootfs")
        // Keep the guest's writable state across a ROM update: ROM archives
        // usually ship without /data, and the guest's own state lives there.
        val oldData = File(target, "data")
        val newData = File(extracted, "data")
        if (oldData.isDirectory && !newData.exists()) {
            oldData.renameTo(newData)
        }
        target.deleteRecursively()
        if (!extracted.renameTo(target)) {
            Log.e(TAG, "failed to install extracted rom: " + extracted.absolutePath)
            staging.deleteRecursively()
            return
        }
        staging.deleteRecursively()
        // The container is launched with "sh <rootfs>/run.sh", so the ROM
        // archive has to carry that script. A third-party archive may omit
        // it; supply the copy bundled in the APK in that case.
        ensureRunSh(target)
        BinderBridge.prepare(target)
        runOnUiThread {
            progressDialog.dismiss()
            romFile.delete()
            tmpDir.mkdirs()
            recreate()
        }
    }

    /**
     * Makes sure the guest root carries the start script the container is
     * launched with. A ROM that ships its own script keeps it; a ROM
     * without one gets the copy bundled in the APK.
     */
    private fun ensureRunSh(rootfs: File) {
        val runSh = File(rootfs, "run.sh")
        if (runSh.exists()) return
        try {
            runSh.writeText(RomScripts.readRunSh(this))
            Log.i(TAG, "rom has no run.sh, installed the bundled one")
        } catch (e: IOException) {
            Log.e(TAG, "failed to install the bundled run.sh: " + e.message)
        }
    }

    /**
     * If the extraction produced exactly one file and that file is a tar
     * archive, unpack it in place. This covers .tar.gz ROMs where 7-Zip
     * stops after the gzip layer.
     */
    private fun unpackNestedTar(dir: File, cpu: Int) {
        val entries = dir.listFiles() ?: return
        if (entries.size != 1) return
        val only = entries[0]
        if (!only.isFile || !only.name.endsWith(".tar", ignoreCase = true)) return
        P7ZipApi.executeCommand(
            String.format(
                Locale.US, "7z x -mmt=%d -aoa '%s' '-o%s'",
                cpu, only.absolutePath, dir.absolutePath
            )
        )
        only.delete()
    }

    /**
     * The ROM may or may not contain a top-level "rootfs" directory. When it
     * does not, wrap the extracted tree into one so that filesDir/rootfs
     * always holds the guest system.
     */
    private fun normalizeRootfs(staging: File): File {
        val wrapped = File(staging, "rootfs")
        if (wrapped.isDirectory) return wrapped
        val entries = staging.listFiles() ?: emptyArray()
        wrapped.mkdirs()
        entries.forEach { it.renameTo(File(wrapped, it.name)) }
        return wrapped
    }

    private fun showExtractingDialog(): ProgressDialog = ProgressDialog(this).apply {
        setTitle(getString(R.string.rom_installer_extracting_title))
        setMessage(getString(R.string.rom_installer_extracting_msg))
        setProgressStyle(ProgressDialog.STYLE_SPINNER)
        setCanceledOnTouchOutside(false)
        show()
    }

    private fun showDownloadFailed() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.rom_installer_download_failed_title))
            .setMessage(getString(R.string.rom_installer_download_failed_msg))
            .setPositiveButton(R.string.rom_installer_install) { _: DialogInterface, _: Int -> pickRomFile() }
            .setNegativeButton(R.string.cancel) { _: DialogInterface, _: Int ->
                finishAffinity()
                exitProcess(0)
            }
            .setCancelable(false)
            .show()
    }
}