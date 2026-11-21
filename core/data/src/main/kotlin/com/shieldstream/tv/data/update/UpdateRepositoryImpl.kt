package com.shieldstream.tv.data.update

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.shieldstream.tv.domain.AppUpdate
import com.shieldstream.tv.domain.AppVersion
import com.shieldstream.tv.domain.UpdateRepository
import com.shieldstream.tv.domain.UpdateState
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    base: OkHttpClient,
    private val json: Json,
    private val version: AppVersion,
    private val texts: Texts,
) : UpdateRepository {

    private val client = base.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<UpdateState>(if (version.isDebug) UpdateState.Unsupported else UpdateState.Idle)
    override val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _promptDismissed = MutableStateFlow(false)
    override val promptDismissed: StateFlow<Boolean> = _promptDismissed.asStateFlow()

    private var job: Job? = null

    init {
        scope.launch { cleanInstalledApks() }
    }

    override fun check(manual: Boolean) {
        if (version.isDebug) return
        if (job?.isActive == true) return
        val now = System.currentTimeMillis()
        if (!manual && now - prefs.getLong(KEY_LAST_CHECK, 0) < AUTO_CHECK_INTERVAL_MS) return
        job = scope.launch {
            if (manual) _state.value = UpdateState.Checking
            runCatching { fetchLatest() }
                .onSuccess { latest ->
                    prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
                    if (latest != null && isNewer(latest.version, version.name)) {
                        if (manual) _promptDismissed.value = false
                        _state.value = UpdateState.Available(version.name, latest)
                    } else {
                        cleanInstalledApks()
                        _state.value = UpdateState.UpToDate(version.name)
                    }
                }
                .onFailure {
                    Log.w(TAG, "No se pudo comprobar: ${it.message}")
                    _state.value = if (manual) {
                        UpdateState.Failed(texts.get(StringsR.string.data_couldn_t_check_for_a_new_version), null)
                    } else {
                        UpdateState.Idle
                    }
                }
        }
    }

    override fun downloadAndInstall() {
        val currentState = _state.value
        val update = when (currentState) {
            is UpdateState.Available -> currentState.update
            is UpdateState.NeedsPermission -> currentState.update
            is UpdateState.Installing -> currentState.update
            is UpdateState.Failed -> currentState.update ?: return
            else -> return
        }
        if (job?.isActive == true) return
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(update)
            return
        }
        job = scope.launch {
            val cachedApk = apkFile(update).takeIf {
                currentState is UpdateState.Installing && it.isFile && it.length() > 0L
            }
            val apk = if (cachedApk != null) {
                cachedApk
            } else {
                runCatching { download(update) }.getOrElse {
                    Log.w(TAG, "Descarga fallida: ${it.message}")
                    _state.value = UpdateState.Failed(
                        texts.get(StringsR.string.data_couldn_t_download_the_update_try_again),
                        update,
                    )
                    return@launch
                }
            }
            _state.value = UpdateState.Installing(update)
            runCatching { install(apk) }.onFailure {
                _state.value = UpdateState.Failed(
                    texts.get(StringsR.string.data_couldn_t_open_the_installer),
                    update,
                )
            }
        }
    }

    override fun dismissPrompt() {
        _promptDismissed.value = true
    }

    // ------------------------------------------------------------------ GitHub

    private fun fetchLatest(): AppUpdate? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${version.updateRepo}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "OpenReel/${version.name}")
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            check(response.isSuccessful) { "GitHub HTTP ${response.code}" }
            response.body?.string().orEmpty()
        }
        val release = json.parseToJsonElement(body) as? JsonObject ?: return null
        if (release["draft"]?.jsonPrimitive?.contentOrNull == "true") return null
        val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        val assets = (release["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val apk = assets.firstOrNull { it.string("name") == APK_NAME }
            ?: assets.firstOrNull { it.string("name")?.endsWith(".apk", ignoreCase = true) == true }
            ?: return null
        return AppUpdate(
            version = tag.removePrefix("v"),
            notes = release["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            apkUrl = apk.string("browser_download_url") ?: return null,
            sizeBytes = apk["size"]?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }

    private fun updatesDir(): File = File(context.cacheDir, UPDATES_DIR)

    private fun apkFile(update: AppUpdate): File = File(updatesDir(), "OpenReel-${update.version}.apk")

    private fun cleanInstalledApks() {
        runCatching {
            val dir = updatesDir()
            if (!dir.isDirectory) return
            dir.listFiles()?.forEach { file ->
                val candidateVersion = file.name
                    .removePrefix("OpenReel-")
                    .removeSuffix(".apk")
                    .takeIf { file.name.startsWith("OpenReel-") && file.name.endsWith(".apk") }
                if (candidateVersion == null || !isNewer(candidateVersion, version.name)) {
                    file.delete()
                }
            }
        }
    }

    private fun download(update: AppUpdate): File {
        val dir = updatesDir().apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // APK de actualizaciones anteriores
        val file = apkFile(update)
        _state.value = UpdateState.Downloading(update, 0f)
        val request = Request.Builder().url(update.apkUrl).header("User-Agent", "OpenReel/${version.name}").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Descarga HTTP ${response.code}" }
            val body = checkNotNull(response.body)
            val total = body.contentLength().takeIf { it > 0 } ?: update.sizeBytes
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    var lastShown = 0f
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0) {
                            val progress = (read.toFloat() / total).coerceIn(0f, 1f)
                            if (progress - lastShown >= 0.01f) {
                                lastShown = progress
                                _state.value = UpdateState.Downloading(update, progress)
                            }
                        }
                    }
                }
            }
        }
        return file
    }

    private fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                Log.w(TAG, "No se pudo abrir el instalador: ${it.message}")
                throw it
            }
    }

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

    companion object {
        private const val TAG = "Update"
        private const val KEY_LAST_CHECK = "last_check"
        private const val AUTO_CHECK_INTERVAL_MS = 6 * 3_600_000L
        private const val UPDATES_DIR = "updates"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val APK_NAME = "OpenReel.apk"

        /** "1.0.10" > "1.0.9"; ignora sufijos ("-pruebas"). */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
