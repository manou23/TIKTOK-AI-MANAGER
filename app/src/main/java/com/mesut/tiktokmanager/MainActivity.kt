package com.mesut.tiktokmanager

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private val ink = Color.rgb(25, 28, 38)
    private val muted = Color.rgb(115, 120, 135)
    private val accent = Color.rgb(0, 190, 160)
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun base(title: String, subtitle: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(247, 248, 251))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(22), dp(20), dp(16))
        }
        root.addView(label("MESUT  /  AI STUDIO", 12, accent, true))
        root.addView(label(title, 27, ink, true).apply { setPadding(0, dp(8), 0, 0) })
        root.addView(label(subtitle, 14, muted, false).apply { setPadding(0, dp(4), 0, dp(16)) })
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        nav.addView(navButton("الرئيسية") { showHome() }, weight())
        nav.addView(navButton("إنشاء فكرة") { showIdeas() }, weight())
        nav.addView(navButton("المحتوى") { showContent() }, weight())
        root.addView(nav)
        setContentView(root)
    }

    private fun showHome() {
        base("لوحة التحكم", "مساحة واحدة لتخطيط محتوى TikTok وإدارته")
        card("مرحباً بك 👋", "ابدأ بتجهيز أول فيديو عربي. هذه النسخة التأسيسية تعمل محلياً، وسيتم توصيل خدمات الذكاء الاصطناعي والحساب لاحقاً.")
        card("استوديو المحتوى", "أفكار وسيناريوهات قصيرة بالعربية لمجالات متنوعة.")
        card("النشر والجدولة", "ستظهر هنا المسودات ومواعيد النشر وحالة كل فيديو بعد إعداد الربط الرسمي.")
        card("تحليلات الحساب", "سيتم عرض المشاهدات والتفاعل وأفضل أوقات النشر بعد ربط حساب TikTok.")
        action("اكتشف أفكار الفيديوهات") { showIdeas() }
    }

    private fun showIdeas() {
        base("مولّد أفكار الفيديو", "اختر مجالاً للحصول على فكرة وسيناريو أولي")
        val choices = listOf("قصص مشوقة", "معلومات وحقائق", "ألعاب وتحديات", "تقنية وذكاء اصطناعي", "غرائب العالم")
        choices.forEach { topic ->
            action(topic) {
                val idea = when (topic) {
                    "قصص مشوقة" -> "عنوان: رسالة وصلت بعد 20 سنة\nالمقدمة: تخيّل أن هاتفك استقبل رسالة من رقمك القديم!\nالمحتوى: ابنِ قصة قصيرة حول لغز الرسالة مع تصاعد التشويق.\nالخاتمة: ماذا كنت ستفعل؟"
                    "معلومات وحقائق" -> "عنوان: حقيقة ستفاجئك\nالمقدمة: هل تعلم أن بعض الكائنات تستطيع تجديد أجزاء من جسمها؟\nالمحتوى: قدّم معلومة موثوقة مع مثال مبسط وصورة توضيحية.\nالخاتمة: هل تريد المزيد من الحقائق؟"
                    "ألعاب وتحديات" -> "عنوان: تحدي اللاعب الذكي\nالمقدمة: أمامك 5 ثوانٍ لاكتشاف الحل!\nالمحتوى: اعرض لغزاً أو لقطة لعب قصيرة ثم اكشف الحل.\nالخاتمة: اكتب نتيجتك في التعليقات."
                    "تقنية وذكاء اصطناعي" -> "عنوان: أداة AI تستحق التجربة\nالمقدمة: هل يمكن للذكاء الاصطناعي اختصار هذه المهمة؟\nالمحتوى: اشرح استخداماً واحداً مع مثال عملي ونتيجة واضحة.\nالخاتمة: ما الأداة التي تريد تجربتها؟"
                    else -> "عنوان: أغرب شيء ستشاهده اليوم\nالمقدمة: قد يبدو هذا مستحيلاً لكنه حقيقي!\nالمحتوى: اعرض ظاهرة موثقة واشرح سببها ببساطة.\nالخاتمة: هل سبق أن شاهدت شيئاً كهذا؟"
                }
                showScript(topic, idea)
            }
        }
    }

    private fun showScript(topic: String, script: String) {
        base("سيناريو مقترح", topic)
        val text = TextView(this).apply {
            this.text = script
            textSize = 16f
            setTextColor(ink)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.WHITE)
        }
        content.addView(text)
        action("نسخ السيناريو") {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("TikTok script", script))
            Toast.makeText(this, "تم نسخ السيناريو", Toast.LENGTH_SHORT).show()
        }
        action("العودة للأفكار") { showIdeas() }
    }

    private fun showContent() {
        base("مكتبة المحتوى", "مكان متابعة الفيديوهات والمسودات")
        card("لا توجد فيديوهات بعد", "أنشئ فكرة أولاً، ثم سنضيف أدوات تحويل السيناريو إلى فيديو وحفظ المسودات.")
        action("إنشاء فكرة جديدة") { showIdeas() }
    }

    private fun card(title: String, body: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            setBackgroundColor(Color.WHITE)
        }
        box.addView(label(title, 17, ink, true))
        box.addView(label(body, 14, muted, false).apply { setPadding(0, dp(7), 0, 0) })
        val lp = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        content.addView(box, lp)
    }

    private fun action(text: String, onClick: () -> Unit) {
        val button = Button(this).apply {
            this.text = text
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(ink)
            setOnClickListener { onClick() }
        }
        content.addView(button, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun navButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 12f
        isAllCaps = false
        setTextColor(ink)
        setOnClickListener { onClick() }
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun weight() = LinearLayout.LayoutParams(0, dp(48), 1f)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
