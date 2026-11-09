package com.mesut.tiktokmanager

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Bitmap
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.media.MediaMetadataRetriever
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.BackgroundColorSpan
import android.text.style.RelativeSizeSpan
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.Size
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.tiktok.open.sdk.auth.AuthApi
import com.tiktok.open.sdk.auth.AuthRequest
import com.tiktok.open.sdk.auth.utils.PKCEUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@UnstableApi
class MainActivity : Activity() {
    private val ink = Color.rgb(24, 29, 43)
    private val muted = Color.rgb(105, 112, 128)
    private val accent = Color.rgb(0, 166, 145)
    private val prefs: SharedPreferences by lazy { getSharedPreferences("mesut_creator", Context.MODE_PRIVATE) }
    private val imageUris = mutableListOf<Uri>()
    private var audioUri: Uri? = null
    private var voiceoverUri: Uri? = null
    private lateinit var titleInput: EditText
    private lateinit var captionInput: EditText
    private lateinit var overlayTextInput: EditText
    private lateinit var narrationInput: EditText
    private lateinit var imageInfo: TextView
    private lateinit var audioInfo: TextView
    private lateinit var voiceoverInfo: TextView
    private lateinit var statusInfo: TextView
    private lateinit var exportButton: Button
    private lateinit var rootContent: LinearLayout
    private lateinit var sceneListContainer: LinearLayout
    private lateinit var durationSpinner: Spinner
    private lateinit var qualitySpinner: Spinner
    private lateinit var transitionSpinner: Spinner
    private lateinit var textSizeSpinner: Spinner
    private lateinit var textPositionSpinner: Spinner
    private lateinit var musicVolumeSeek: SeekBar
    private lateinit var voiceVolumeSeek: SeekBar
    private lateinit var musicVolumeLabel: TextView
    private lateinit var voiceVolumeLabel: TextView
    private var tiktokAccountInfo: TextView? = null
    private var lastExportUri: Uri? = null

    companion object {
        private const val PICK_IMAGES = 101
        private const val PICK_AUDIO = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadDraft()
        showStudio()
        handleTikTokAuthIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTikTokAuthIntent(intent)
    }

    private fun loadDraft() {
        val saved = prefs.getString("images", "[]") ?: "[]"
        try {
            val json = JSONArray(saved)
            for (i in 0 until json.length()) imageUris.add(Uri.parse(json.getString(i)))
        } catch (_: Exception) { imageUris.clear() }
        audioUri = prefs.getString("audio", null)?.let { Uri.parse(it) }
        voiceoverUri = prefs.getString("voiceover", null)?.let { Uri.parse(it) }
        lastExportUri = prefs.getString("last_export_uri", null)?.let { Uri.parse(it) }
    }

    private fun saveDraft() {
        val json = JSONArray()
        imageUris.forEach { json.put(it.toString()) }
        prefs.edit().putString("images", json.toString())
            .putString("audio", audioUri?.toString()).putString("voiceover", voiceoverUri?.toString()).apply()
    }




    private fun startTikTokPublishFlow() {
        val session = readTikTokSessionToken()
        if (session.isNullOrBlank()) {
            Toast.makeText(this, "اربط حساب TikTok أولاً.", Toast.LENGTH_LONG).show()
            return
        }
        val grantedScopes = prefs.getString("tiktok_granted_scopes", "").orEmpty()
        if (!grantedScopes.split(",").map { it.trim() }.contains("video.publish")) {
            Toast.makeText(this, "الحساب غير مخوّل بصلاحية video.publish. وافق على الصلاحية في TikTok Developer Portal ثم أعد الربط.", Toast.LENGTH_LONG).show()
            return
        }
        if (lastExportUri == null) {
            Toast.makeText(this, "أنشئ فيديو MP4 أولاً.", Toast.LENGTH_LONG).show()
            return
        }
        Thread {
            try {
                val result = callTikTokWorker("/creator-info", session, JSONObject())
                val creator = result.optJSONObject("creator") ?: throw IllegalStateException("لم تصل معلومات حساب TikTok")
                val privacy = creator.optJSONArray("privacy_level_options") ?: JSONArray()
                val options = (0 until privacy.length()).map { privacy.optString(it) }.filter { it.isNotBlank() }
                if (options.isEmpty()) throw IllegalStateException("لم يُرجع TikTok خيارات الخصوصية لهذا الحساب")
                runOnUiThread { showTikTokPublishDialog(session, options) }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "تعذر تجهيز النشر: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun showTikTokPublishDialog(session: String, privacyOptions: List<String>) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        container.addView(label("اختر من يمكنه مشاهدة الفيديو:", 14, ink, true))
        val radioGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val labels = mapOf(
            "PUBLIC_TO_EVERYONE" to "عام للجميع",
            "MUTUAL_FOLLOW_FRIENDS" to "الأصدقاء المتبادلون",
            "FOLLOWER_OF_CREATOR" to "المتابعون",
            "SELF_ONLY" to "أنا فقط"
        )
        var selectedPrivacy = privacyOptions.first()
        privacyOptions.forEachIndexed { index, value ->
            val radio = RadioButton(this).apply {
                text = labels[value] ?: value
                id = View.generateViewId()
                isChecked = index == 0
                setOnClickListener { selectedPrivacy = value }
            }
            if (index == 0) selectedPrivacy = value
            radioGroup.addView(radio)
        }
        container.addView(radioGroup)
        val disableComments = CheckBox(this).apply { text = "تعطيل التعليقات" }
        val disableDuet = CheckBox(this).apply { text = "تعطيل Duet" }
        val disableStitch = CheckBox(this).apply { text = "تعطيل Stitch" }
        val aiGenerated = CheckBox(this).apply { text = "وضع علامة على الفيديو كمحتوى مولّد بالذكاء الاصطناعي" }
        container.addView(disableComments)
        container.addView(disableDuet)
        container.addView(disableStitch)
        container.addView(aiGenerated)
        val caption = captionInput.text.toString().trim().ifBlank { titleInput.text.toString().trim() }.take(2200)
        android.app.AlertDialog.Builder(this)
            .setTitle("تأكيد النشر على TikTok")
            .setMessage("سيتم إرسال آخر فيديو إلى حسابك عبر واجهة TikTok الرسمية. هذا إجراء حقيقي لا يمكن التراجع عنه بعد اكتماله. الوصف: ${caption.ifBlank { "بدون وصف" }}")
            .setView(container)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("تأكيد وبدء النشر") { _, _ ->
                if (caption.isBlank()) {
                    Toast.makeText(this, "أضف عنواناً أو وصفاً قبل النشر.", Toast.LENGTH_LONG).show()
                } else {
                    uploadAndPublishToTikTok(session, selectedPrivacy, caption, disableComments.isChecked,
                        disableDuet.isChecked, disableStitch.isChecked, aiGenerated.isChecked)
                }
            }
            .show()
    }

    private fun copyLastVideoToCache(): File {
        val uri = lastExportUri ?: throw IllegalStateException("لا يوجد فيديو أخير")
        val file = File(cacheDir, "mesut_tiktok_upload.mp4")
        val input = contentResolver.openInputStream(uri) ?: throw IllegalStateException("تعذر قراءة ملف الفيديو")
        input.use { source -> FileOutputStream(file).use { target -> source.copyTo(target) } }
        if (!file.exists() || file.length() <= 0L) throw IllegalStateException("ملف الفيديو فارغ")
        return file
    }

    private fun uploadAndPublishToTikTok(
        session: String,
        privacy: String,
        caption: String,
        disableComments: Boolean,
        disableDuet: Boolean,
        disableStitch: Boolean,
        isAiGenerated: Boolean
    ) {
        exportButton.isEnabled = false
        statusInfo.text = "جارٍ تجهيز الفيديو للنشر على TikTok..."
        Thread {
            var uploadFile: File? = null
            try {
                uploadFile = copyLastVideoToCache()
                val minimumChunkSize = 5L * 1024L * 1024L
                val chunkSize = if (uploadFile.length() < minimumChunkSize) uploadFile.length().toInt() else 10 * 1024 * 1024
                val totalChunks = maxOf(1, (uploadFile.length() / chunkSize).toInt())
                val initPayload = JSONObject()
                    .put("title", caption)
                    .put("privacy_level", privacy)
                    .put("video_size", uploadFile.length())
                    .put("chunk_size", chunkSize)
                    .put("total_chunk_count", totalChunks)
                    .put("disable_comment", disableComments)
                    .put("disable_duet", disableDuet)
                    .put("disable_stitch", disableStitch)
                    .put("is_aigc", isAiGenerated)
                val init = callTikTokWorker("/publish/init", session, initPayload)
                if (!init.optBoolean("ok")) throw IllegalStateException(init.optString("error", "فشل تهيئة النشر"))
                val publishId = init.getString("publish_id")
                val uploadUrl = init.getString("upload_url")
                prefs.edit().putString("last_tiktok_publish_id", publishId).apply()
                uploadFileChunksToTikTok(uploadFile, uploadUrl, chunkSize)
                var finalStatus = "PROCESSING_UPLOAD"
                var completed = false
                for (attempt in 0 until 3) {
                    val statusResult = callTikTokWorker("/publish/status", session, JSONObject().put("publish_id", publishId))
                    val statusObject = statusResult.optJSONObject("status") ?: JSONObject()
                    finalStatus = statusObject.optString("status", "UNKNOWN")
                    if (finalStatus == "PUBLISH_COMPLETE") {
                        completed = true
                        break
                    }
                    if (finalStatus == "FAILED") {
                        throw IllegalStateException(statusObject.optString("fail_reason", "رفض TikTok نشر الفيديو"))
                    }
                    Thread.sleep(10_000)
                }
                runOnUiThread {
                    statusInfo.text = if (completed) {
                        "تم نشر الفيديو على TikTok بنجاح."
                    } else {
                        "اكتمل رفع الفيديو، وحالته لدى TikTok: $finalStatus. استخدم متابعة آخر عملية نشر للتحقق لاحقاً."
                    }
                    Toast.makeText(this, if (completed) "تم النشر بنجاح" else "تم الرفع وجارٍ المعالجة لدى TikTok", Toast.LENGTH_LONG).show()
                    exportButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusInfo.text = "تعذر النشر على TikTok: ${e.localizedMessage}"
                    Toast.makeText(this, "فشل النشر: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    exportButton.isEnabled = true
                }
            } finally {
                uploadFile?.delete()
            }
        }.start()
    }

    private fun uploadFileChunksToTikTok(file: File, uploadUrl: String, chunkSize: Int) {
        val totalSize = file.length()
        FileInputStream(file).use { input ->
            var start = 0L
            val totalChunks = maxOf(1, (totalSize / chunkSize).toInt())
            for (chunkIndex in 0 until totalChunks) {
                val length = if (chunkIndex == totalChunks - 1) (totalSize - start).toInt() else chunkSize
                val bytes = ByteArray(length)
                var offset = 0
                while (offset < length) {
                    val count = input.read(bytes, offset, length - offset)
                    if (count < 0) throw IllegalStateException("انتهى ملف الفيديو قبل اكتمال الرفع")
                    offset += count
                }
                val end = start + length - 1
                val connection = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    doOutput = true
                    setFixedLengthStreamingMode(length)
                    setRequestProperty("Content-Type", "video/mp4")
                    setRequestProperty("Content-Range", "bytes $start-$end/$totalSize")
                }
                try {
                    connection.outputStream.use { it.write(bytes) }
                    val responseCode = connection.responseCode
                    if (responseCode !in 200..299) {
                        val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                        throw IllegalStateException("فشل رفع جزء الفيديو (HTTP $responseCode): $error")
                    }
                } finally {
                    connection.disconnect()
                }
                start += length
                val percent = (start * 100 / totalSize).toInt()
                runOnUiThread { statusInfo.text = "جارٍ رفع الفيديو إلى TikTok: $percent%" }
            }
        }
    }

    private fun checkLastTikTokPublishStatus() {
        val session = readTikTokSessionToken()
        val publishId = prefs.getString("last_tiktok_publish_id", null)
        if (session.isNullOrBlank() || publishId.isNullOrBlank()) {
            Toast.makeText(this, "لا توجد عملية نشر سابقة لمتابعتها.", Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            try {
                val result = callTikTokWorker("/publish/status", session, JSONObject().put("publish_id", publishId))
                val statusObject = result.optJSONObject("status") ?: JSONObject()
                val status = statusObject.optString("status", "UNKNOWN")
                val detail = statusObject.optString("fail_reason", "")
                runOnUiThread {
                    statusInfo.text = if (detail.isNotBlank()) "حالة النشر: $status — $detail" else "حالة النشر لدى TikTok: $status"
                    Toast.makeText(this, statusInfo.text, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "تعذر جلب حالة النشر: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun connectTikTokAccount() {
        if (BuildConfig.TIKTOK_CLIENT_KEY.isBlank() || BuildConfig.TIKTOK_WORKER_URL.isBlank()) {
            Toast.makeText(this, "يلزم إعداد TIKTOK_CLIENT_KEY و TIKTOK_WORKER_URL في إعدادات البناء أولاً.", Toast.LENGTH_LONG).show()
            return
        }
        if (BuildConfig.TIKTOK_REDIRECT_URI.isBlank()) {
            Toast.makeText(this, "عنوان إعادة التوجيه غير مضبوط.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val verifier = PKCEUtils.generateCodeVerifier()
            val stateBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val state = Base64.encodeToString(stateBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            prefs.edit().putString("tiktok_code_verifier", verifier).putString("tiktok_oauth_state", state).apply()
            val request = AuthRequest(
                clientKey = BuildConfig.TIKTOK_CLIENT_KEY,
                scope = "user.info.basic,video.publish",
                redirectUri = BuildConfig.TIKTOK_REDIRECT_URI,
                codeVerifier = verifier,
                state = state
            )
            AuthApi(activity = this).authorize(request, AuthApi.AuthMethod.TikTokApp)
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر بدء تسجيل الدخول إلى TikTok: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleTikTokAuthIntent(intent: Intent?) {
        if (intent == null || BuildConfig.TIKTOK_CLIENT_KEY.isBlank()) return
        try {
            val response = AuthApi(activity = this).getAuthResponseFromIntent(intent, BuildConfig.TIKTOK_REDIRECT_URI) ?: return
            val expectedState = prefs.getString("tiktok_oauth_state", null)
            if (expectedState.isNullOrBlank() || response.state != expectedState) {
                prefs.edit().remove("tiktok_code_verifier").remove("tiktok_oauth_state").apply()
                Toast.makeText(this, "تم رفض رد تسجيل الدخول لأن قيمة الحماية غير متطابقة.", Toast.LENGTH_LONG).show()
                return
            }
            if (response.authCode.isBlank()) {
                val error = response.authErrorDescription ?: response.errorMsg ?: "تم إلغاء تسجيل الدخول أو رفض الإذن."
                prefs.edit().remove("tiktok_code_verifier").remove("tiktok_oauth_state").apply()
                runOnUiThread { Toast.makeText(this, "TikTok: $error", Toast.LENGTH_LONG).show() }
                return
            }
            val verifier = prefs.getString("tiktok_code_verifier", null)
            if (verifier.isNullOrBlank()) {
                Toast.makeText(this, "انتهت بيانات التحقق الآمن. أعد ربط الحساب.", Toast.LENGTH_LONG).show()
                return
            }
            prefs.edit().remove("tiktok_code_verifier").remove("tiktok_oauth_state").apply()
            exchangeTikTokAuthorizationCode(response.authCode, verifier, response.grantedPermissions)
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر قراءة استجابة TikTok: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun exchangeTikTokAuthorizationCode(code: String, verifier: String, grantedPermissions: String) {
        Thread {
            try {
                val body = JSONObject()
                    .put("code", code)
                    .put("code_verifier", verifier)
                    .put("redirect_uri", BuildConfig.TIKTOK_REDIRECT_URI)
                val result = callTikTokWorker("/oauth/exchange", null, body)
                if (!result.optBoolean("ok")) throw IllegalStateException(result.optString("error", "فشل ربط حساب TikTok"))
                val sessionToken = result.getString("session_token")
                saveTikTokSessionToken(sessionToken)
                prefs.edit().putString("tiktok_granted_scopes", grantedPermissions).apply()
                val creatorResult = callTikTokWorker("/creator-info", sessionToken, JSONObject())
                val creator = creatorResult.optJSONObject("creator")
                val nickname = creator?.optString("creator_nickname").orEmpty()
                val username = creator?.optString("creator_username").orEmpty()
                runOnUiThread {
                    tiktokAccountInfo?.text = if (username.isNotBlank()) "متصل: $nickname (@$username)" else "تم ربط حساب TikTok بنجاح."
                    Toast.makeText(this, "تم ربط حساب TikTok عبر واجهاته الرسمية.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "فشل ربط TikTok: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun callTikTokWorker(path: String, sessionToken: String?, payload: JSONObject): JSONObject {
        val base = BuildConfig.TIKTOK_WORKER_URL.trimEnd('/')
        if (base.isBlank()) throw IllegalStateException("خادم TikTok غير مضبوط")
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (!sessionToken.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $sessionToken")
        }
        return try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (code !in 200..299) throw IllegalStateException(json.optString("error", "خادم TikTok أعاد HTTP $code"))
            json
        } finally {
            connection.disconnect()
        }
    }

    private fun getTikTokSessionKey(): SecretKey {
        val alias = "mesut_tiktok_session_key"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun saveTikTokSessionToken(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getTikTokSessionKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + encrypted
        prefs.edit().putString("tiktok_session_encrypted", Base64.encodeToString(combined, Base64.NO_WRAP)).apply()
    }

    private fun readTikTokSessionToken(): String? {
        val encoded = prefs.getString("tiktok_session_encrypted", null) ?: return null
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size <= 12) return null
            val iv = combined.copyOfRange(0, 12)
            val encrypted = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getTikTokSessionKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }

    private fun disconnectTikTokAccount() {
        val token = readTikTokSessionToken()
        if (token.isNullOrBlank()) {
            prefs.edit().remove("tiktok_session_encrypted").remove("tiktok_granted_scopes").apply()
            tiktokAccountInfo?.text = "لم يتم ربط حساب TikTok."
            return
        }
        Thread {
            try { callTikTokWorker("/oauth/disconnect", token, JSONObject()) } catch (_: Exception) { }
            prefs.edit().remove("tiktok_session_encrypted").apply()
            runOnUiThread {
                tiktokAccountInfo?.text = "تم فصل حساب TikTok."
                Toast.makeText(this, "تم فصل الحساب من التطبيق.", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun savedProjectsFile() = File(filesDir, "mesut_saved_projects.json")

    private fun readSavedProjects(): JSONArray {
        val file = savedProjectsFile()
        if (!file.exists()) return JSONArray()
        return try { JSONArray(FileInputStream(file).bufferedReader().use { it.readText() }) }
        catch (_: Exception) { JSONArray() }
    }

    private fun writeSavedProjects(projects: JSONArray) {
        FileOutputStream(savedProjectsFile()).use { it.write(projects.toString().toByteArray(Charsets.UTF_8)) }
    }

    private fun showSaveProjectDialog() {
        val nameInput = EditText(this).apply {
            hint = "اسم المشروع"
            setSingleLine(true)
            setText(titleInput.text.toString().trim())
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("حفظ المشروع الحالي")
            .setMessage("سيتم حفظ الصور والنصوص والصوت وإعدادات التصدير على هاتفك.")
            .setView(nameInput)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حفظ") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "اكتب اسماً للمشروع", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val projects = readSavedProjects()
                val updated = JSONArray()
                for (i in 0 until projects.length()) {
                    val old = projects.optJSONObject(i) ?: continue
                    if (!old.optString("name").equals(name, ignoreCase = true)) updated.put(old)
                }
                val imageArray = JSONArray().apply { imageUris.forEach { put(it.toString()) } }
                val project = JSONObject()
                    .put("name", name)
                    .put("savedAt", System.currentTimeMillis())
                    .put("images", imageArray)
                    .put("title", titleInput.text.toString())
                    .put("caption", captionInput.text.toString())
                    .put("overlayText", overlayTextInput.text.toString())
                    .put("narration", narrationInput.text.toString())
                    .put("audioUri", audioUri?.toString() ?: JSONObject.NULL)
                    .put("voiceoverUri", voiceoverUri?.toString() ?: JSONObject.NULL)
                    .put("durationIndex", durationSpinner.selectedItemPosition)
                    .put("qualityIndex", qualitySpinner.selectedItemPosition)
                    .put("transitionIndex", transitionSpinner.selectedItemPosition)
                    .put("textSizeIndex", textSizeSpinner.selectedItemPosition)
                    .put("textPositionIndex", textPositionSpinner.selectedItemPosition)
                    .put("musicVolume", musicVolumeSeek.progress)
                    .put("voiceVolume", voiceVolumeSeek.progress)
                updated.put(project)
                writeSavedProjects(updated)
                Toast.makeText(this, "تم حفظ المشروع: $name", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun showSavedProjectsDialog() {
        val projects = readSavedProjects()
        if (projects.length() == 0) {
            Toast.makeText(this, "لا توجد مشاريع محفوظة بعد", Toast.LENGTH_SHORT).show()
            return
        }
        val names = Array(projects.length()) { i -> projects.optJSONObject(i)?.optString("name", "مشروع") ?: "مشروع" }
        android.app.AlertDialog.Builder(this)
            .setTitle("مشاريعي المحفوظة")
            .setItems(names) { _, which ->
                projects.optJSONObject(which)?.let { project ->
                    android.app.AlertDialog.Builder(this)
                        .setTitle(project.optString("name", "مشروع"))
                        .setItems(arrayOf("فتح المشروع", "حذف المشروع")) { _, action ->
                            if (action == 0) loadSavedProject(project) else deleteSavedProject(project.optString("name"))
                        }
                        .setNegativeButton("إلغاء", null)
                        .show()
                }
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun loadSavedProject(project: JSONObject) {
        imageUris.clear()
        val images = project.optJSONArray("images") ?: JSONArray()
        for (i in 0 until images.length()) {
            val raw = images.optString(i)
            try {
                val uri = Uri.parse(raw)
                contentResolver.openFileDescriptor(uri, "r")?.close()
                imageUris.add(uri)
            } catch (_: Exception) { }
        }
        titleInput.setText(project.optString("title"))
        captionInput.setText(project.optString("caption"))
        overlayTextInput.setText(project.optString("overlayText"))
        narrationInput.setText(project.optString("narration"))
        audioUri = project.optString("audioUri").takeIf { it.isNotBlank() && it != "null" }?.let { Uri.parse(it) }
        voiceoverUri = project.optString("voiceoverUri").takeIf { it.isNotBlank() && it != "null" }?.let { Uri.parse(it) }
        durationSpinner.setSelection(project.optInt("durationIndex", 1).coerceIn(0, 5))
        qualitySpinner.setSelection(project.optInt("qualityIndex", 0).coerceIn(0, 1))
        transitionSpinner.setSelection(project.optInt("transitionIndex", 0).coerceIn(0, 3))
        textSizeSpinner.setSelection(project.optInt("textSizeIndex", 1).coerceIn(0, 2))
        textPositionSpinner.setSelection(project.optInt("textPositionIndex", 2).coerceIn(0, 2))
        musicVolumeSeek.progress = project.optInt("musicVolume", 35).coerceIn(0, 100)
        voiceVolumeSeek.progress = project.optInt("voiceVolume", 100).coerceIn(0, 100)
        musicVolumeLabel.text = "${musicVolumeSeek.progress}%"
        voiceVolumeLabel.text = "${voiceVolumeSeek.progress}%"
        saveDraft()
        prefs.edit()
            .putString("title", titleInput.text.toString())
            .putString("caption", captionInput.text.toString())
            .putString("overlay_text", overlayTextInput.text.toString())
            .putString("narration_text", narrationInput.text.toString())
            .apply()
        refreshSelectionLabels()
        Toast.makeText(this, "تم فتح المشروع: ${project.optString("name")}", Toast.LENGTH_LONG).show()
    }

    private fun deleteSavedProject(name: String) {
        val projects = readSavedProjects()
        val updated = JSONArray()
        for (i in 0 until projects.length()) {
            val project = projects.optJSONObject(i) ?: continue
            if (project.optString("name") != name) updated.put(project)
        }
        writeSavedProjects(updated)
        Toast.makeText(this, "تم حذف المشروع", Toast.LENGTH_SHORT).show()
    }

    private fun showStudio() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Color.rgb(246, 248, 251))
            setPadding(dp(18), dp(20), dp(18), dp(14))
        }
        page.addView(label("MESUT  •  TIKTOK AI MANAGER", 12, accent, true))
        page.addView(label("استوديو إنتاج الفيديو", 26, ink, true).apply { setPadding(0, dp(8), 0, dp(3)) })
        page.addView(label("أنشئ فيديو عمودياً 9:16 من صورك واحفظه بصيغة MP4", 14, muted, false))
        val scroll = ScrollView(this)
        rootContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, dp(16), 0, dp(10))
        }
        scroll.addView(rootContent)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        section("1. معلومات الفيديو")
        titleInput = EditText(this).apply {
            hint = "عنوان الفيديو (مثلاً: أغرب 5 حقائق)"
            textSize = 15f
            isSingleLine = true
            setTextColor(ink)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setText(prefs.getString("title", ""))
            setBackgroundColor(Color.WHITE)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        rootContent.addView(titleInput, matchBottom())
        captionInput = EditText(this).apply {
            hint = "الوصف والهاشتاغات (اختياري)"
            textSize = 14f
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP or Gravity.RIGHT
            setTextColor(ink)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setText(prefs.getString("caption", ""))
            setBackgroundColor(Color.WHITE)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        rootContent.addView(captionInput, matchBottom())
        overlayTextInput = EditText(this).apply {
            hint = "النص الذي سيظهر فوق الفيديو (اختياري)"
            textSize = 14f
            minLines = 2
            maxLines = 3
            gravity = Gravity.TOP or Gravity.RIGHT
            setTextColor(ink)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setText(prefs.getString("overlay_text", ""))
            setBackgroundColor(Color.WHITE)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        rootContent.addView(overlayTextInput, matchBottom())
        rootContent.addView(label("حجم النص داخل الفيديو", 13, muted, true))
        textSizeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("صغير", "متوسط", "كبير"))
            setSelection(prefs.getInt("overlay_size", 1).coerceIn(0, 2))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.edit().putInt("overlay_size", position).apply()
                }
            }
        }
        rootContent.addView(textSizeSpinner, matchBottom())
        rootContent.addView(label("موضع النص", 13, muted, true))
        textPositionSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("أعلى الفيديو", "وسط الفيديو", "أسفل الفيديو"))
            setSelection(prefs.getInt("overlay_position", 2).coerceIn(0, 2))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.edit().putInt("overlay_position", position).apply()
                }
            }
        }
        rootContent.addView(textPositionSpinner, matchBottom())

        section("2. الصور والمشاهد")
        imageInfo = label(imageSummary(), 13, muted, false)
        rootContent.addView(imageInfo)
        sceneListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        rootContent.addView(sceneListContainer)
        renderSceneList()
        action("＋  اختيار صور من الهاتف") { pickImages() }
        actionSecondary("مسح الصور المختارة") {
            imageUris.clear()
            saveDraft()
            refreshSelectionLabels()
        }

        section("3. التعليق الصوتي والصوت الخلفي")
        narrationInput = EditText(this).apply {
            hint = "اكتب نص التعليق الصوتي بالعربية ثم أنشئ الصوت"
            textSize = 14f
            minLines = 3
            maxLines = 5
            gravity = Gravity.TOP or Gravity.RIGHT
            setTextColor(ink)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setText(prefs.getString("narration_text", ""))
            setBackgroundColor(Color.WHITE)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        rootContent.addView(narrationInput, matchBottom())
        actionSecondary("إنشاء تعليق صوتي عربي") { generateArabicVoiceover() }
        voiceoverInfo = label(voiceoverSummary(), 13, muted, false)
        rootContent.addView(voiceoverInfo)
        actionSecondary("إزالة التعليق الصوتي") {
            voiceoverUri = null
            saveDraft()
            refreshSelectionLabels()
        }
        audioInfo = label(audioSummary(), 13, muted, false)
        rootContent.addView(audioInfo)
        actionSecondary("اختيار ملف صوتي") { pickAudio() }
        actionSecondary("إزالة الصوت") {
            audioUri = null
            saveDraft()
            refreshSelectionLabels()
        }
        rootContent.addView(label("مستوى صوت الموسيقى الخلفية", 13, muted, true))
        musicVolumeLabel = label("", 12, muted, false)
        rootContent.addView(musicVolumeLabel)
        musicVolumeSeek = SeekBar(this).apply {
            max = 100
            progress = prefs.getInt("music_volume", 35).coerceIn(0, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    musicVolumeLabel.text = "${progress}%"
                    if (fromUser) prefs.edit().putInt("music_volume", progress).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        rootContent.addView(musicVolumeSeek, matchBottom())
        rootContent.addView(label("مستوى صوت التعليق الصوتي", 13, muted, true))
        voiceVolumeLabel = label("", 12, muted, false)
        rootContent.addView(voiceVolumeLabel)
        voiceVolumeSeek = SeekBar(this).apply {
            max = 100
            progress = prefs.getInt("voice_volume", 100).coerceIn(0, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    voiceVolumeLabel.text = "${progress}%"
                    if (fromUser) prefs.edit().putInt("voice_volume", progress).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        rootContent.addView(voiceVolumeSeek, matchBottom())
        musicVolumeLabel.text = "${musicVolumeSeek.progress}%"
        voiceVolumeLabel.text = "${voiceVolumeSeek.progress}%"

        section("4. إعدادات التحرير")
        rootContent.addView(label("مدة كل مشهد", 13, muted, true))
        durationSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("3 ثوانٍ", "4 ثوانٍ", "5 ثوانٍ", "6 ثوانٍ", "8 ثوانٍ", "10 ثوانٍ"))
            setSelection(prefs.getInt("scene_duration_index", 1).coerceIn(0, 5))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.edit().putInt("scene_duration_index", position).apply()
                }
            }
        }
        rootContent.addView(durationSpinner, matchBottom())
        rootContent.addView(label("دقة الفيديو", 13, muted, true))
        qualitySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("1080p — Full HD", "720p — HD"))
            setSelection(prefs.getInt("quality_index", 0).coerceIn(0, 1))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.edit().putInt("quality_index", position).apply()
                }
            }
        }
        rootContent.addView(qualitySpinner, matchBottom())
        rootContent.addView(label("الانتقال بين المشاهد", 13, muted, true))
        transitionSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("بدون انتقال", "تلاشي متداخل — 0.3 ثانية", "تلاشي متداخل — 0.5 ثانية", "تلاشي متداخل — 0.8 ثانية"))
            setSelection(prefs.getInt("transition_index", 0).coerceIn(0, 3))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.edit().putInt("transition_index", position).apply()
                }
            }
        }
        rootContent.addView(transitionSpinner, matchBottom())

        section("5. إنشاء الفيديو")
        cardText("يمكنك الآن إعادة ترتيب المشاهد أو حذف أي صورة، واختيار مدة المشهد ودقة 1080p أو 720p. معدل الإطارات 30 إطاراً في الثانية.")
        statusInfo = label("جاهز لإنشاء فيديو جديد.", 14, muted, false).apply { setPadding(0, dp(12), 0, dp(8)) }
        rootContent.addView(statusInfo)
        exportButton = action("إنشاء فيديو MP4") { exportVideo() }
        actionSecondary("حفظ المشروع الحالي") { showSaveProjectDialog() }
        actionSecondary("مشاريعي المحفوظة") { showSavedProjectsDialog() }
        actionSecondary("مشاركة آخر فيديو") { shareLastVideo() }
        actionSecondary("فتح مجلد فيديوهات MESUT") { openVideoFolder() }

        section("حساب TikTok")
        tiktokAccountInfo = label(if (readTikTokSessionToken() != null) "يوجد حساب TikTok مربوط بهذا التطبيق." else "لم يتم ربط حساب TikTok بعد.", 13, muted, false)
        rootContent.addView(tiktokAccountInfo)
        actionSecondary("ربط حساب TikTok رسمياً") { connectTikTokAccount() }
        actionSecondary("نشر آخر فيديو على TikTok") { startTikTokPublishFlow() }
        actionSecondary("متابعة آخر عملية نشر") { checkLastTikTokPublishStatus() }
        actionSecondary("فصل حساب TikTok") { disconnectTikTokAccount() }

        section("ملاحظة النشر")
        cardText("بعد إنشاء الفيديو، اضغط مشاركة واختر TikTok من قائمة التطبيقات. ستراجع الفيديو وتؤكد النشر داخل TikTok بنفسك. النشر المباشر من داخل التطبيق يحتاج موافقة TikTok الرسمية.")
        page.addView(bottomNav())
        setContentView(page)
    }

    private fun pickImages() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, PICK_IMAGES)
    }

    private fun pickAudio() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, PICK_AUDIO)
    }

    @Deprecated("Uses document picker for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        if (requestCode == PICK_IMAGES) {
            val picked = mutableListOf<Uri>()
            val clip = data.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) picked.add(clip.getItemAt(i).uri)
            } else data.data?.let { picked.add(it) }
            picked.forEach { uri ->
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                if (!imageUris.contains(uri)) imageUris.add(uri)
            }
            saveDraft()
            refreshSelectionLabels()
        } else if (requestCode == PICK_AUDIO) {
            data.data?.let {
                audioUri = it
                try { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                saveDraft()
                refreshSelectionLabels()
            }
        }
    }

    private fun exportVideo() {
        if (imageUris.isEmpty()) {
            Toast.makeText(this, "اختر صورة واحدة على الأقل أولاً", Toast.LENGTH_LONG).show()
            return
        }
        val title = titleInput.text.toString().trim()
        prefs.edit().putString("title", title).putString("caption", captionInput.text.toString()).putString("overlay_text", overlayTextInput.text.toString()).putString("narration_text", narrationInput.text.toString()).apply()
        exportButton.isEnabled = false
        statusInfo.text = "جارٍ تجهيز الفيديو... لا تغلق التطبيق."
        val output = File(cacheDir, "mesut_video_${System.currentTimeMillis()}.mp4")
        val durationMs = listOf(3_000L, 4_000L, 5_000L, 6_000L, 8_000L, 10_000L)[durationSpinner.selectedItemPosition.coerceIn(0, 5)]
        val width = if (qualitySpinner.selectedItemPosition == 1) 720 else 1080
        val height = if (qualitySpinner.selectedItemPosition == 1) 1280 else 1920
        try {
            val videoEffects = mutableListOf<androidx.media3.common.Effect>(
                Presentation.createForWidthAndHeight(width, height, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
            )
            val overlayText = overlayTextInput.text.toString().trim()
            if (overlayText.isNotEmpty()) {
                val styledText = SpannableString(overlayText)
                val sizeScale = listOf(0.82f, 1.0f, 1.28f)[prefs.getInt("overlay_size", 1).coerceIn(0, 2)]
                styledText.setSpan(RelativeSizeSpan(sizeScale), 0, styledText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                styledText.setSpan(ForegroundColorSpan(Color.WHITE), 0, styledText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                styledText.setSpan(BackgroundColorSpan(Color.argb(190, 15, 20, 30)), 0, styledText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val anchorY = listOf(-0.78f, 0f, 0.78f)[prefs.getInt("overlay_position", 2).coerceIn(0, 2)]
                val settings = StaticOverlaySettings.Builder()
                    .setOverlayFrameAnchor(0f, anchorY)
                    .setBackgroundFrameAnchor(0f, anchorY)
                    .build()
                videoEffects.add(OverlayEffect(listOf(TextOverlay.createStaticTextOverlay(styledText, settings))))
            }
            val clips = imageUris.map { uri ->
                val media = MediaItem.Builder().setUri(uri).setImageDurationMs(durationMs).build()
                EditedMediaItem.Builder(media)
                    .setFrameRate(30)
                    .setEffects(Effects(emptyList(), videoEffects))
                    .build()
            }
            val transitionMs = listOf(0L, 300L, 500L, 800L)[transitionSpinner.selectedItemPosition.coerceIn(0, 3)]
            val videoSequences = if (transitionMs > 0L && clips.size > 1) {
                val durationUs = durationMs * 1_000L
                val stepUs = durationUs - transitionMs * 1_000L
                val totalDurationUs = clips.size * durationUs - (clips.size - 1) * transitionMs * 1_000L
                val transparentUri = createTransparentFrameUri()
                clips.mapIndexed { index, clip ->
                    val startUs = index * stepUs
                    val endUs = startUs + durationUs
                    val tailUs = (totalDurationUs - endUs).coerceAtLeast(0L)
                    val sequenceItems = mutableListOf<EditedMediaItem>()
                    if (startUs > 0L) sequenceItems.add(stillImageItem(transparentUri, startUs / 1_000L))
                    sequenceItems.add(clip)
                    if (tailUs > 0L) sequenceItems.add(stillImageItem(transparentUri, tailUs / 1_000L))
                    EditedMediaItemSequence.Builder(sequenceItems).build()
                }
            } else {
                listOf(EditedMediaItemSequence.Builder(clips).build())
            }
            val sequences = videoSequences.toMutableList()
            audioUri?.let { uri ->
                val musicItem = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                    .setEffects(Effects(volumeEffects(musicVolumeSeek.progress), emptyList()))
                    .build()
                sequences.add(EditedMediaItemSequence.Builder(musicItem).setIsLooping(true).build())
            }
            voiceoverUri?.let { uri ->
                val voiceItem = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                    .setEffects(Effects(volumeEffects(voiceVolumeSeek.progress), emptyList()))
                    .build()
                sequences.add(EditedMediaItemSequence.Builder(voiceItem).build())
            }
            val compositionBuilder = Composition.Builder(sequences)
            if (transitionMs > 0L && clips.size > 1) {
                compositionBuilder.setVideoCompositorSettings(SceneCrossfadeSettings(
                    clips.size, durationMs * 1_000L, (durationMs - transitionMs) * 1_000L
                ))
            }
            val composition = compositionBuilder.build()
            val expectedDurationMs = imageUris.size * durationMs - (imageUris.size - 1) * transitionMs
            val transformer = Transformer.Builder(this)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        try {
                            val validationError = verifyExportedVideo(output, expectedDurationMs, width, height)
                            if (validationError != null) {
                                statusInfo.text = validationError
                                exportButton.isEnabled = true
                                output.delete()
                                return
                            }
                            val savedUri = saveToGallery(output)
                            lastExportUri = savedUri
                            prefs.edit().putString("last_export_uri", savedUri.toString()).apply()
                            statusInfo.text = "اكتمل إنشاء الفيديو وحُفظ في مجلد Movies/MESUT TikTok."
                            exportButton.isEnabled = true
                            Toast.makeText(this@MainActivity, "تم إنشاء الفيديو بنجاح", Toast.LENGTH_LONG).show()
                            output.delete()
                        } catch (e: Exception) {
                            statusInfo.text = "اكتمل التصدير لكن تعذر حفظه في المعرض: ${e.localizedMessage}"
                            exportButton.isEnabled = true
                        }
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        statusInfo.text = "فشل إنشاء الفيديو: ${exportException.localizedMessage ?: "خطأ غير معروف"}"
                        exportButton.isEnabled = true
                        output.delete()
                    }
                }).build()
            transformer.start(composition, output.absolutePath)
        } catch (e: Exception) {
            statusInfo.text = "تعذر بدء التصدير: ${e.localizedMessage}"
            exportButton.isEnabled = true
        }
    }




    private fun verifyExportedVideo(file: File, expectedDurationMs: Long, expectedWidth: Int, expectedHeight: Int): String? {
        if (!file.exists() || file.length() < 1_024L) return "فشل التحقق: ملف الفيديو غير موجود أو حجمه غير صالح."
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            if (duration <= 0L) "فشل التحقق: لم يتم العثور على مدة صالحة للفيديو."
            else if (duration < expectedDurationMs * 0.75) "فشل التحقق: مدة الفيديو أقصر بكثير من المدة المطلوبة."
            else if (width < expectedWidth * 0.75 || height < expectedHeight * 0.75) "فشل التحقق: دقة الفيديو أقل من الدقة المطلوبة."
            else null
        } catch (e: Exception) {
            "تعذر التحقق من ملف الفيديو: ${e.localizedMessage ?: "خطأ في قراءة الملف"}"
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun createTransparentFrameUri(): Uri {
        val file = File(cacheDir, "mesut_transparent_frame.png")
        if (!file.exists()) {
            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        return Uri.fromFile(file)
    }

    private fun stillImageItem(uri: Uri, durationMs: Long): EditedMediaItem {
        val media = MediaItem.Builder().setUri(uri).setImageDurationMs(durationMs.coerceAtLeast(1L)).build()
        return EditedMediaItem.Builder(media).setFrameRate(30).build()
    }
    private class SceneCrossfadeSettings(
        private val sceneCount: Int,
        private val sceneDurationUs: Long,
        private val sceneStepUs: Long
    ) : VideoCompositorSettings {
        override fun getOutputSize(inputSizes: List<Size>): Size = inputSizes[0]

        override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
            if (inputId !in 0 until sceneCount) return StaticOverlaySettings.Builder().setAlphaScale(0f).build()
            val startUs = inputId * sceneStepUs
            val endUs = startUs + sceneDurationUs
            val fadeDurationUs = sceneDurationUs - sceneStepUs
            val alpha = when {
                presentationTimeUs < startUs || presentationTimeUs >= endUs -> 0f
                inputId > 0 && presentationTimeUs < startUs + fadeDurationUs ->
                    ((presentationTimeUs - startUs).toFloat() / fadeDurationUs.toFloat()).coerceIn(0f, 1f)
                inputId < sceneCount - 1 && presentationTimeUs >= endUs - fadeDurationUs ->
                    ((endUs - presentationTimeUs).toFloat() / fadeDurationUs.toFloat()).coerceIn(0f, 1f)
                else -> 1f
            }
            return StaticOverlaySettings.Builder().setAlphaScale(alpha).build()
        }
    }
    private fun volumeEffects(percent: Int): List<androidx.media3.common.audio.AudioProcessor> {
        val gain = percent.coerceIn(0, 100) / 100f
        val processor = ChannelMixingAudioProcessor()
        for (channels in 1..6) {
            val coefficients = FloatArray(channels * channels) { index -> if (index / channels == index % channels) gain else 0f }
            val matrix = ChannelMixingMatrix(channels, channels, coefficients)
            processor.putChannelMixingMatrix(matrix)
        }
        return listOf(processor)
    }

    private fun generateArabicVoiceover() {
        val spokenText = narrationInput.text.toString().trim()
        if (spokenText.isEmpty()) {
            Toast.makeText(this, "اكتب نص التعليق الصوتي أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putString("narration_text", spokenText).apply()
        statusInfo.text = "جارٍ إنشاء التعليق الصوتي على الهاتف..."
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(this) { result ->
            if (result != TextToSpeech.SUCCESS) {
                runOnUiThread { statusInfo.text = "تعذر تشغيل محرك تحويل النص إلى كلام." }
                engine.shutdown()
                return@TextToSpeech
            }
            val languageResult = engine.setLanguage(Locale("ar"))
            if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                runOnUiThread { statusInfo.text = "محرك الصوت في هاتفك لا يدعم العربية حالياً. ثبّت بيانات صوت عربية من إعدادات تحويل النص إلى كلام." }
                engine.shutdown()
                return@TextToSpeech
            }
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {}
                override fun onDone(utteranceId: String) {
                    runOnUiThread {
                        voiceoverUri = Uri.fromFile(File(filesDir, "mesut_arabic_voice.wav"))
                        saveDraft()
                        refreshSelectionLabels()
                        statusInfo.text = "تم إنشاء التعليق الصوتي العربي. سيُستخدم تلقائياً عند تصدير الفيديو."
                        engine.shutdown()
                    }
                }
                override fun onError(utteranceId: String) {
                    runOnUiThread {
                        statusInfo.text = "فشل إنشاء التعليق الصوتي. تحقق من محرك تحويل النص إلى كلام."
                        engine.shutdown()
                    }
                }
            })
            val target = File(filesDir, "mesut_arabic_voice.wav")
            val resultCode = engine.synthesizeToFile(spokenText, Bundle(), target, "mesut_arabic_voice")
            if (resultCode == TextToSpeech.ERROR) {
                runOnUiThread { statusInfo.text = "تعذر بدء إنشاء الصوت." }
                engine.shutdown()
            }
        }
    }

    private fun saveToGallery(file: File): Uri {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, "MESUT_${System.currentTimeMillis()}.mp4")
            put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/MESUT TikTok")
            put(android.provider.MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("لم يتمكن Android من إنشاء ملف الفيديو")
        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                FileInputStream(file).use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("تعذر فتح ملف الحفظ")
            val done = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Video.Media.IS_PENDING, 0)
            }
            contentResolver.update(uri, done, null, null)
            return uri
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
    }

    private fun shareLastVideo() {
        val uri = lastExportUri
        if (uri == null) {
            Toast.makeText(this, "أنشئ فيديو أولاً ثم شاركه", Toast.LENGTH_SHORT).show()
            return
        }
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, captionInput.text.toString())
            clipData = ClipData.newUri(contentResolver, "MESUT video", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, "مشاركة الفيديو مع"))
    }

    private fun openVideoFolder() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { startActivity(intent) } catch (_: Exception) {
            Toast.makeText(this, "افتح المعرض ثم مجلد Movies / MESUT TikTok", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshSelectionLabels() {
        imageInfo.text = imageSummary()
        audioInfo.text = audioSummary()
        voiceoverInfo.text = voiceoverSummary()
        renderSceneList()
    }

    private fun imageSummary() = if (imageUris.isEmpty()) "لم تختر صوراً بعد." else "تم اختيار ${imageUris.size} صورة. يمكنك تغيير ترتيبها أدناه."
    private fun renderSceneList() {
        if (!::sceneListContainer.isInitialized) return
        sceneListContainer.removeAllViews()
        imageUris.forEachIndexed { index, uri ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPadding(dp(8), dp(5), dp(8), dp(5))
                setBackgroundColor(Color.WHITE)
            }
            val name = TextView(this).apply {
                text = "المشهد ${index + 1}"
                textSize = 13f
                setTextColor(ink)
                setTypeface(typeface, Typeface.BOLD)
            }
            row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(smallSceneButton("↑") {
                if (index > 0) {
                    val item = imageUris.removeAt(index)
                    imageUris.add(index - 1, item)
                    saveDraft()
                    refreshSelectionLabels()
                }
            })
            row.addView(smallSceneButton("↓") {
                if (index < imageUris.lastIndex) {
                    val item = imageUris.removeAt(index)
                    imageUris.add(index + 1, item)
                    saveDraft()
                    refreshSelectionLabels()
                }
            })
            row.addView(smallSceneButton("حذف") {
                imageUris.removeAt(index)
                saveDraft()
                refreshSelectionLabels()
            })
            sceneListContainer.addView(row, matchBottom())
        }
    }

    private fun smallSceneButton(caption: String, onClick: () -> Unit) = Button(this).apply {
        text = caption
        textSize = 11f
        isAllCaps = false
        minWidth = dp(42)
        minHeight = dp(38)
        setPadding(dp(4), 0, dp(4), 0)
        setTextColor(ink)
        setBackgroundColor(Color.rgb(230, 235, 241))
        setOnClickListener { onClick() }
    }
    private fun audioSummary() = if (audioUri == null) "لم تتم إضافة موسيقى خلفية." else "تم اختيار موسيقى خلفية (تتكرر لتغطية مدة الفيديو)."
    private fun voiceoverSummary() = if (voiceoverUri == null) "لم يتم إنشاء تعليق صوتي بعد." else "التعليق الصوتي جاهز وسيُضاف دون تكرار."

    private fun section(text: String) {
        rootContent.addView(label(text, 17, ink, true).apply { setPadding(0, dp(18), 0, dp(8)) })
    }

    private fun cardText(text: String) {
        val view = TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(muted)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            setBackgroundColor(Color.WHITE)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        rootContent.addView(view, matchBottom())
    }

    private fun action(text: String, onClick: () -> Unit): Button {
        val button = Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(ink)
            minHeight = dp(50)
            setOnClickListener { onClick() }
        }
        rootContent.addView(button, matchBottom())
        return button
    }

    private fun actionSecondary(text: String, onClick: () -> Unit) {
        val button = Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 13f
            setTextColor(ink)
            setBackgroundColor(Color.rgb(230, 235, 241))
            minHeight = dp(46)
            setOnClickListener { onClick() }
        }
        rootContent.addView(button, matchBottom())
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(color)
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun bottomNav() = TextView(this).apply {
        text = "MESUT TikTok AI Manager  •  الإصدار 1.9.0"
        textSize = 11f
        gravity = Gravity.CENTER
        setTextColor(muted)
        setPadding(0, dp(10), 0, 0)
    }

    private fun matchBottom() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
