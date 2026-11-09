package com.mesut.tiktokmanager

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream

@UnstableApi
class MainActivity : Activity() {
    private val ink = Color.rgb(24, 29, 43)
    private val muted = Color.rgb(105, 112, 128)
    private val accent = Color.rgb(0, 166, 145)
    private val prefs: SharedPreferences by lazy { getSharedPreferences("mesut_creator", Context.MODE_PRIVATE) }
    private val imageUris = mutableListOf<Uri>()
    private var audioUri: Uri? = null
    private lateinit var titleInput: EditText
    private lateinit var captionInput: EditText
    private lateinit var imageInfo: TextView
    private lateinit var audioInfo: TextView
    private lateinit var statusInfo: TextView
    private lateinit var exportButton: Button
    private lateinit var rootContent: LinearLayout
    private lateinit var sceneListContainer: LinearLayout
    private lateinit var durationSpinner: Spinner
    private lateinit var qualitySpinner: Spinner
    private var lastExportUri: Uri? = null

    companion object {
        private const val PICK_IMAGES = 101
        private const val PICK_AUDIO = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadDraft()
        showStudio()
    }

    private fun loadDraft() {
        val saved = prefs.getString("images", "[]") ?: "[]"
        try {
            val json = JSONArray(saved)
            for (i in 0 until json.length()) imageUris.add(Uri.parse(json.getString(i)))
        } catch (_: Exception) { imageUris.clear() }
        audioUri = prefs.getString("audio", null)?.let { Uri.parse(it) }
        lastExportUri = prefs.getString("last_export_uri", null)?.let { Uri.parse(it) }
    }

    private fun saveDraft() {
        val json = JSONArray()
        imageUris.forEach { json.put(it.toString()) }
        prefs.edit().putString("images", json.toString())
            .putString("audio", audioUri?.toString()).apply()
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

        section("2. الصور والمشاهد")
        imageInfo = label(imageSummary(), 13, muted, false)
        rootContent.addView(imageInfo)
        sceneListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        rootContent.addView(sceneListContainer)
        action("＋  اختيار صور من الهاتف") { pickImages() }
        actionSecondary("مسح الصور المختارة") {
            imageUris.clear()
            saveDraft()
            refreshSelectionLabels()
        }

        section("3. الصوت الخلفي")
        audioInfo = label(audioSummary(), 13, muted, false)
        rootContent.addView(audioInfo)
        actionSecondary("اختيار ملف صوتي") { pickAudio() }
        actionSecondary("إزالة الصوت") {
            audioUri = null
            saveDraft()
            refreshSelectionLabels()
        }

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

        section("5. إنشاء الفيديو")
        cardText("يمكنك الآن إعادة ترتيب المشاهد أو حذف أي صورة، واختيار مدة المشهد ودقة 1080p أو 720p. معدل الإطارات 30 إطاراً في الثانية.")
        statusInfo = label("جاهز لإنشاء فيديو جديد.", 14, muted, false).apply { setPadding(0, dp(12), 0, dp(8)) }
        rootContent.addView(statusInfo)
        exportButton = action("إنشاء فيديو MP4") { exportVideo() }
        actionSecondary("مشاركة آخر فيديو") { shareLastVideo() }
        actionSecondary("فتح مجلد فيديوهات MESUT") { openVideoFolder() }

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
        prefs.edit().putString("title", title).putString("caption", captionInput.text.toString()).apply()
        exportButton.isEnabled = false
        statusInfo.text = "جارٍ تجهيز الفيديو... لا تغلق التطبيق."
        val output = File(cacheDir, "mesut_video_${System.currentTimeMillis()}.mp4")
        val durationMs = listOf(3_000L, 4_000L, 5_000L, 6_000L, 8_000L, 10_000L)[durationSpinner.selectedItemPosition.coerceIn(0, 5)]
        val width = if (qualitySpinner.selectedItemPosition == 1) 720 else 1080
        val height = if (qualitySpinner.selectedItemPosition == 1) 1280 else 1920
        try {
            val clips = imageUris.map { uri ->
                val media = MediaItem.Builder().setUri(uri).setImageDurationMs(durationMs).build()
                EditedMediaItem.Builder(media)
                    .setFrameRate(30)
                    .setEffects(Effects(emptyList(), listOf(Presentation.createForWidthAndHeight(width, height, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))))
                    .build()
            }
            val videoSequence = EditedMediaItemSequence.Builder(clips).build()
            val sequences = mutableListOf(videoSequence)
            audioUri?.let { uri ->
                val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(uri)).build()
                sequences.add(EditedMediaItemSequence.Builder(audioItem).setIsLooping(true).build())
            }
            val composition = Composition.Builder(sequences).build()
            val transformer = Transformer.Builder(this)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        try {
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
    private fun audioSummary() = if (audioUri == null) "لم تتم إضافة صوت. يمكن إنشاء فيديو صامت." else "تم اختيار ملف صوتي للخلفية."

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
        text = "MESUT TikTok AI Manager  •  الإصدار 1.2.0"
        textSize = 11f
        gravity = Gravity.CENTER
        setTextColor(muted)
        setPadding(0, dp(10), 0, 0)
    }

    private fun matchBottom() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
