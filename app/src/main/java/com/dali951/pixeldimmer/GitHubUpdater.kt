package com.dali951.pixeldimmer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class ReleaseUpdateInfo(
    val currentVersion: String,
    val latestTag: String,
    val isNewerAvailable: Boolean,
    val apkDownloadUrl: String,
    val releaseNotes: String
)

object GitHubUpdater {

    private const val GITHUB_LATEST_RELEASE_API =
        "https://api.github.com/repos/DALI951/pixel-dimmer/releases/latest"
    private const val FALLBACK_APK_URL =
        "https://github.com/DALI951/pixel-dimmer/releases/latest/download/PixelDimmer.apk"

    private val executor = Executors.newSingleThreadExecutor()

    private fun postToMain(action: () -> Unit) {
        Handler(Looper.getMainLooper()).post(action)
    }

    fun getInstalledVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.1"
        } catch (_: Exception) {
            "1.0.1"
        }
    }

    fun checkLatestRelease(
        context: Context,
        onResult: (Result<ReleaseUpdateInfo>) -> Unit
    ) {
        val currentVersion = getInstalledVersionName(context)
        executor.execute {
            try {
                val conn = (URL(GITHUB_LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "PixelDimmer-Android-Updater")
                }

                val code = conn.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("GitHub API returned HTTP $code")
                }

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tagName = json.optString("tag_name", "v$currentVersion")
                val notes = json.optString("body", "Latest Pixel Dimmer release.")
                var apkUrl = FALLBACK_APK_URL

                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", FALLBACK_APK_URL)
                            if (name.equals("PixelDimmer.apk", ignoreCase = true)) break
                        }
                    }
                }

                val info = ReleaseUpdateInfo(
                    currentVersion = currentVersion,
                    latestTag = tagName,
                    isNewerAvailable = VersionComparator.isRemoteVersionNewer(currentVersion, tagName),
                    apkDownloadUrl = apkUrl,
                    releaseNotes = notes
                )
                postToMain { onResult(Result.success(info)) }
            } catch (e: Exception) {
                postToMain { onResult(Result.failure(e)) }
            }
        }
    }

    fun downloadAndInstallApk(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
            }
            onError("Allow 'Install unknown apps' for Pixel Dimmer, then tap Update again.")
            return
        }

        executor.execute {
            try {
                var currentUrl = apkUrl
                var redirects = 0
                var conn: HttpURLConnection
                while (true) {
                    conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        connectTimeout = 15_000
                        readTimeout = 30_000
                        setRequestProperty("User-Agent", "PixelDimmer-Android-Updater")
                    }
                    val status = conn.responseCode
                    if (status in 300..399 && redirects < 5) {
                        val nextUrl = conn.getHeaderField("Location") ?: break
                        conn.disconnect()
                        currentUrl = nextUrl
                        redirects++
                        continue
                    }
                    break
                }

                if (conn.responseCode !in 200..299) {
                    throw IllegalStateException("Download failed with HTTP ${conn.responseCode}")
                }

                val totalBytes = conn.contentLength.coerceAtLeast(1)
                val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val outFile = File(updateDir, "PixelDimmer-update.apk")
                if (outFile.exists()) outFile.delete()

                conn.inputStream.use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        var read: Int
                        var lastReported = -1
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            val pct = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            if (pct != lastReported) {
                                lastReported = pct
                                postToMain { onProgress(pct) }
                            }
                        }
                        output.flush()
                    }
                }

                postToMain {
                    onProgress(100)
                    launchPackageInstaller(context, outFile, onError)
                }
            } catch (e: Exception) {
                postToMain {
                    onError("Update download failed: ${e.message}")
                }
            }
        }
    }

    private fun launchPackageInstaller(context: Context, apkFile: File, onError: (String) -> Unit) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            onError("Could not launch installer: ${e.message}")
        }
    }
}
