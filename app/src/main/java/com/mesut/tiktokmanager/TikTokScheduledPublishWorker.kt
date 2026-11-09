package com.mesut.tiktokmanager

import android.net.Uri
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class TikTokScheduledPublishWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val workName = inputData.getString(KEY_WORK_NAME) ?: id.toString()
        var tempFile: File? = null
        updateScheduleRecord(applicationContext, workName, "جارٍ النشر")
        try {
            val session = TikTokSessionStore.read(applicationContext)
                ?: throw IllegalStateException("حساب TikTok غير مربوط أو انتهت الجلسة")
            val uriValue = inputData.getString(KEY_VIDEO_URI)
                ?: throw IllegalStateException("ملف الفيديو المجدول غير موجود")
            val uri = Uri.parse(uriValue)
            val workingFile = File(applicationContext.cacheDir, "scheduled_tiktok_$id.mp4")
            tempFile = workingFile
            val source = applicationContext.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("تعذر فتح ملف الفيديو المجدول")
            source.use { input -> FileOutputStream(workingFile).use { output -> input.copyTo(output) } }
            val videoSize = workingFile.length()
            if (videoSize <= 0L) throw IllegalStateException("ملف الفيديو المجدول فارغ")

            val minChunk = 5L * 1024L * 1024L
            val tenMb = 10L * 1024L * 1024L
            val chunkSize = when {
                videoSize < minChunk -> videoSize.toInt()
                videoSize < tenMb -> minChunk.toInt()
                else -> tenMb.toInt()
            }
            val totalChunks = ((videoSize + chunkSize - 1L) / chunkSize).toInt()
            val payload = JSONObject()
                .put("title", inputData.getString(KEY_CAPTION).orEmpty().take(2200))
                .put("privacy_level", inputData.getString(KEY_PRIVACY) ?: "SELF_ONLY")
                .put("video_size", videoSize)
                .put("chunk_size", chunkSize)
                .put("total_chunk_count", totalChunks)
                .put("disable_comment", inputData.getBoolean(KEY_DISABLE_COMMENTS, false))
                .put("disable_duet", inputData.getBoolean(KEY_DISABLE_DUET, false))
                .put("disable_stitch", inputData.getBoolean(KEY_DISABLE_STITCH, false))
                .put("is_aigc", inputData.getBoolean(KEY_IS_AIGC, false))
            val init = callWorker("/publish/init", session, payload)
            if (!init.optBoolean("ok")) throw IllegalStateException(init.optString("error", "فشل تهيئة النشر المجدول"))
            val publishId = init.getString("publish_id")
            uploadChunks(workingFile, init.getString("upload_url"), chunkSize)

            var status = "PROCESSING_UPLOAD"
            for (attempt in 0 until 3) {
                delay(10_000)
                val result = callWorker("/publish/status", session, JSONObject().put("publish_id", publishId))
                val statusObject = result.optJSONObject("status") ?: JSONObject()
                status = statusObject.optString("status", "UNKNOWN")
                if (status == "PUBLISH_COMPLETE") break
                if (status == "FAILED") throw IllegalStateException(statusObject.optString("fail_reason", "فشل TikTok في نشر الفيديو"))
            }
            val displayStatus = if (status == "PUBLISH_COMPLETE") "تم النشر بنجاح" else "تم الرفع — حالة TikTok: $status"
            updateScheduleRecord(applicationContext, workName, displayStatus, publishId)
            Result.success(workDataOf("publish_id" to publishId, "status" to status))
        } catch (error: Exception) {
            val message = error.localizedMessage ?: "خطأ غير معروف"
            updateScheduleRecord(applicationContext, workName, "فشل: $message")
            Result.failure(workDataOf("error" to message))
        } finally {
            tempFile?.delete()
        }
    }

    private fun callWorker(path: String, session: String, payload: JSONObject): JSONObject {
        val base = BuildConfig.TIKTOK_WORKER_URL.trimEnd('/')
        if (base.isBlank()) throw IllegalStateException("خادم TikTok غير مضبوط")
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $session")
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

    private fun uploadChunks(file: File, uploadUrl: String, chunkSize: Int) {
        val totalSize = file.length()
        FileInputStream(file).use { input ->
            var start = 0L
            val count = ((totalSize + chunkSize - 1L) / chunkSize).toInt()
            for (index in 0 until count) {
                val length = if (index == count - 1) (totalSize - start).toInt() else chunkSize
                val bytes = ByteArray(length)
                var offset = 0
                while (offset < length) {
                    val read = input.read(bytes, offset, length - offset)
                    if (read < 0) throw IllegalStateException("انتهى ملف الفيديو قبل اكتمال الرفع")
                    offset += read
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
                    val code = connection.responseCode
                    if (code !in 200..299) throw IllegalStateException("فشل رفع الفيديو إلى TikTok (HTTP $code)")
                } finally {
                    connection.disconnect()
                }
                start += length
            }
        }
    }

    private fun updateScheduleRecord(context: Context, workName: String, status: String, publishId: String? = null) {
        val prefs = context.getSharedPreferences("mesut_creator", Context.MODE_PRIVATE)
        val stored = prefs.getString("scheduled_posts", "[]")
        val current = try { JSONArray(stored) } catch (_: Exception) { JSONArray() }
        val updated = JSONArray()
        for (i in 0 until current.length()) {
            val item = current.optJSONObject(i) ?: continue
            if (item.optString("workName") == workName) {
                item.put("status", status)
                item.put("updatedAt", System.currentTimeMillis())
                if (!publishId.isNullOrBlank()) item.put("publishId", publishId)
            }
            updated.put(item)
        }
        prefs.edit().putString("scheduled_posts", updated.toString()).apply()
    }

    companion object {
        const val KEY_WORK_NAME = "work_name"
        const val KEY_VIDEO_URI = "video_uri"
        const val KEY_CAPTION = "caption"
        const val KEY_PRIVACY = "privacy"
        const val KEY_DISABLE_COMMENTS = "disable_comments"
        const val KEY_DISABLE_DUET = "disable_duet"
        const val KEY_DISABLE_STITCH = "disable_stitch"
        const val KEY_IS_AIGC = "is_aigc"
    }
}
