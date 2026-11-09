# إعداد الربط الرسمي مع TikTok

## ما الذي أصبح موجوداً في المشروع؟
- TikTok OpenSDK Android 2.2.0 لتسجيل الدخول الرسمي وPKCE.
- استقبال callback عبر `https://open.tiktokapis.com/callback`.
- خادم Cloudflare Worker في `server/tiktok-worker` لتبادل authorization code وحفظ access/refresh tokens مشفرة في KV.
- التطبيق يحتفظ فقط برمز جلسة خاص به، مشفراً بواسطة Android Keystore.
- واجهات الخادم لقراءة معلومات المنشئ وتهيئة الرفع ومتابعة حالة النشر.

## ما يلزم من حساب TikTok Developer
1. أنشئ تطبيقاً في https://developers.tiktok.com/.
2. أضف منصة Android مع اسم الحزمة `com.mesut.tiktokmanager`.
3. أضف منتجي Login Kit وContent Posting API.
4. سجّل Redirect URI مطابقاً تماماً لـ `https://open.tiktokapis.com/callback`.
5. اطلب صلاحية `video.publish` ووافق على متطلبات مراجعة TikTok.
6. قدّم بصمة MD5 وSHA-256 لشهادة توقيع إصدار Android النهائي. لا تستخدم بصمة Debug مؤقتة؛ يلزم مفتاح Release ثابت قبل تسجيل تطبيق Android.

## إعداد GitHub Actions
أضف هذه القيم كـ Repository Secrets (لا تضعها داخل الملفات):
- `TIKTOK_CLIENT_KEY` — المفتاح العام للتطبيق.
- `TIKTOK_REDIRECT_URI` — يجب أن يطابق Redirect URI المسجل.
- `TIKTOK_WORKER_URL` — عنوان Worker المنشور، مثل `https://mesut-tiktok-api.<account>.workers.dev`.

لا تضف `TIKTOK_CLIENT_SECRET` إلى GitHub Actions الخاصة ببناء Android ولا إلى APK. ضعه فقط كـ Cloudflare Worker secret.

## نشر الخادم
اتبع التعليمات في `server/tiktok-worker/README.md` لإنشاء KV namespace، وضبط الأسرار، ونشر Worker.

## حدود النشر
- التطبيق يستخدم واجهات TikTok الرسمية فقط؛ لا يحاكي النقرات ولا يخزن كلمة مرور TikTok.
- لا يمكن تفعيل النشر العام حتى توافق TikTok على التطبيق وصلاحية `video.publish` وتُراجع التطبيق. العملاء غير المدققين يقتصر نشرهم على الخاص.
- يجب على التطبيق إرسال ملف الفيديو إلى `upload_url` المؤقت الذي تعيده TikTok. لا يمر ملف الفيديو الكبير عبر Worker.
- الربط البرمجي لا يعني أن الحساب أصبح مربوطاً تلقائياً؛ يجب إكمال إعداد المطور وتوقيع الإصدار أولاً.
