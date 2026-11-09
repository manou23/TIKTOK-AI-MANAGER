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
5. اطلب صلاحيات `video.publish` و`video.list` ووافق على متطلبات مراجعة TikTok.
6. قدّم بصمة MD5 وSHA-256 لشهادة توقيع إصدار Android النهائي. لا تستخدم بصمة Debug مؤقتة؛ يلزم مفتاح Release ثابت قبل تسجيل تطبيق Android.

## تجهيز Cloudflare ونشر الخادم من GitHub

1. أنشئ حساب Cloudflare وفعّل Workers وKV.
2. من مجلد `server/tiktok-worker` أنشئ مساحة KV باسم `MESUT_TIKTOK_TOKENS` بالأمر:
   ```bash
   npx wrangler kv namespace create MESUT_TIKTOK_TOKENS
   ```
3. انسخ معرّف namespace الذي يعيده Cloudflare.
4. أنشئ Cloudflare API Token بصلاحيات Workers Scripts: Edit وWorkers KV Storage: Edit، مع تقييد الحساب الصحيح.
5. في GitHub افتح Settings → Secrets and variables → Actions وأضف الأسرار التالية:

| اسم السر | مكان استخدامه |
|---|---|
| `CLOUDFLARE_API_TOKEN` | نشر Worker |
| `CLOUDFLARE_ACCOUNT_ID` | تحديد حساب Cloudflare |
| `TIKTOK_KV_NAMESPACE_ID` | ربط KV |
| `TIKTOK_CLIENT_KEY` | TikTok Worker وAndroid |
| `TIKTOK_CLIENT_SECRET` | Worker فقط |
| `TIKTOK_REDIRECT_URI` | Worker وAndroid |
| `TOKEN_ENCRYPTION_KEY` | Worker فقط، 64 خانة hex |
| `TIKTOK_WORKER_URL` | Android وبفحص الصحة، مثال `https://mesut-tiktok-api.<subdomain>.workers.dev` |

ولإنشاء مفتاح تشفير عشوائي، شغّل محلياً `openssl rand -hex 32`. لا ترسله في المحادثة ولا تحفظه في ملفات المشروع.

6. بعد حفظ الأسرار، افتح GitHub → Actions → **Deploy TikTok Cloudflare Worker** → Run workflow. ينشئ سير العمل إعداد KV من السر، ويرفع أسرار Worker، ثم ينشر الخادم ويفحص `/health`.
7. بعد نجاح نشر Worker، تأكد أن قيمة `TIKTOK_WORKER_URL` تطابق عنوانه المنشور، ثم أعد تشغيل سير عمل **Android APK** لبناء نسخة مهيأة للربط.

لا تضع `TIKTOK_CLIENT_SECRET` أو `TOKEN_ENCRYPTION_KEY` في أسرار Android أو داخل APK. لا تشارك أي سر في رسائل المحادثة.

## إعداد Android
تحتاج عملية بناء APK إلى GitHub Actions secrets التالية:
- `TIKTOK_CLIENT_KEY`
- `TIKTOK_REDIRECT_URI`
- `TIKTOK_WORKER_URL`

## نشر الخادم
يوجد سير عمل نشر يدوي في `.github/workflows/deploy-tiktok-worker.yml`. لا يمكن تشغيله بنجاح قبل إنشاء KV namespace وإضافة أسرار Cloudflare أعلاه.

## حدود النشر
- التطبيق يستخدم واجهات TikTok الرسمية فقط؛ لا يحاكي النقرات ولا يخزن كلمة مرور TikTok.
- لا يمكن تفعيل النشر العام حتى توافق TikTok على التطبيق وصلاحية `video.publish` وتُراجع التطبيق. العملاء غير المدققين يقتصر نشرهم على الخاص.
- يجب على التطبيق إرسال ملف الفيديو إلى `upload_url` المؤقت الذي تعيده TikTok. لا يمر ملف الفيديو الكبير عبر Worker.
- الربط البرمجي لا يعني أن الحساب أصبح مربوطاً تلقائياً؛ يجب إكمال إعداد المطور وتوقيع الإصدار أولاً.


## النشر المباشر من التطبيق
بعد ضبط حساب TikTok والخادم، استخدم زر **نشر آخر فيديو على TikTok**. يعرض التطبيق خيارات الخصوصية التي يعيدها TikTok، ويطلب تأكيداً صريحاً قبل إرسال الفيديو. يرفع التطبيق الملف مباشرةً إلى عنوان الرفع المؤقت الذي تعيده TikTok، ثم يتابع حالة النشر من خلال الخادم. لا تُرسل كلمات المرور أو رموز TikTok السرية إلى APK.


## الجدولة والتحليلات
- يضيف التطبيق المهام المجدولة إلى Android WorkManager مع شرط اتصال الشبكة، ويمكن إلغاؤها من قائمة المنشورات المجدولة.
- تم تصحيح حساب أجزاء الرفع باستخدام التقريب للأعلى، مع اختبار أحجام الملفات التي لا تقبل القسمة بالتساوي على حجم الجزء.
- WorkManager مهمة مؤجلة؛ قد ينفذ Android النشر بعد الوقت المحدد قليلاً بسبب توفير الطاقة أو انقطاع الشبكة، لذلك لا يُضمن توقيت بالدقيقة.
- لوحة التحليلات تستخدم صلاحية `video.list` لقراءة أحدث 20 فيديو ومؤشرات المشاهدات والإعجابات والتعليقات والمشاركات التي توفرها TikTok.


## إصدار التطبيق
الإصدار الحالي: **2.0.0** (versionCode 11). ملف البناء المنشور من GitHub Actions هو Debug APK ما لم يتم إعداد توقيع Release ثابت. لا تستخدم بصمة Debug المؤقتة لتسجيل التطبيق في TikTok Developer Portal.
