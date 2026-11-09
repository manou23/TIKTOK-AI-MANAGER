# إعداد Instagram Reels — MESUT AI Social Manager

يحتوي التطبيق الآن على تدفق ربط Instagram ونشر Reels باستخدام Meta Graph API. لا يعمل التكامل تلقائياً بمجرد تثبيت APK: يجب إعداد تطبيق Meta وخادم Cloudflare وR2 وإكمال متطلبات الصلاحيات والمراجعة التي تطلبها Meta.

## الحسابات المدعومة

المسار الحالي يستخدم **Instagram API with Facebook Login**:

- حساب Instagram احترافي Business أو Creator.
- ربط حساب Instagram بصفحة Facebook.
- حساب Facebook الذي يملك صلاحية إدارة الصفحة المرتبطة.
- تطبيق Meta Developer مع Facebook Login وInstagram Graph API.

الحسابات الشخصية العادية غير مدعومة في مسار النشر هذا. الواجهة الحالية تنشر فيديو MP4 كـ Reel فقط؛ الصور والمنشورات المتعددة والقصص ليست ضمن هذا الإصدار.

## الصلاحيات

يطلب تدفق OAuth الصلاحيات التالية:

- `pages_show_list`
- `pages_read_engagement`
- `instagram_basic`
- `instagram_content_publish`

قد تتطلب إتاحة الاستخدام خارج أدوار التطبيق Standard/Advanced Access ومراجعة Meta. راجع إعدادات التطبيق والوثائق الرسمية قبل تقديمه للمراجعة.

## 1. إعداد Meta Developer

1. أنشئ تطبيق Meta في https://developers.facebook.com/.
2. أضف حالة الاستخدام/المنتج الذي يتيح Instagram Graph API مع Facebook Login.
3. سجّل عنوان OAuth التالي ضمن Valid OAuth Redirect URIs، مع استبدال نطاق Worker بعنوانك الفعلي:

   `https://YOUR-WORKER.workers.dev/instagram/oauth/callback`

4. أضف الصلاحيات المذكورة أعلاه وامنحها لحساب الاختبار.
5. أكمل App Review وBusiness Verification أو أي خطوات إضافية تعرضها لوحة Meta قبل استخدام التطبيق مع مستخدمين خارج أدوار الاختبار.

## 2. إعداد Cloudflare Worker

يستخدم Worker نفس KV namespace الموجود لتكامل TikTok، ونفس مفتاح التشفير AES-GCM. أضف أسرار Meta التالية من مجلد `server/tiktok-worker`:

```bash
npx wrangler secret put META_APP_ID
npx wrangler secret put META_APP_SECRET
npx wrangler secret put META_REDIRECT_URI
npx wrangler secret put META_GRAPH_API_VERSION
```

- `META_APP_ID`: رقم تطبيق Meta.
- `META_APP_SECRET`: سر التطبيق، للخادم فقط.
- `META_REDIRECT_URI`: العنوان المطابق حرفياً لما سجلته في Meta.
- `META_GRAPH_API_VERSION`: إصدار Graph API المدعوم في لوحة Meta، مثل `v26.0` عند توفره لتطبيقك.
- `TOKEN_ENCRYPTION_KEY`: المفتاح الحالي الموجود في Worker، لا تنشئ مفتاحاً مختلفاً قبل التأكد من سياسة تدوير المفاتيح لأن ذلك قد يمنع فك تشفير رموز TikTok الحالية.

لا تضع App Secret أو access tokens أو مفتاح التشفير في APK أو GitHub أو المحادثة.

## 3. إعداد تخزين الفيديو المؤقت عبر R2

تحتاج Meta إلى جلب الفيديو من عنوان URL عام أثناء تجهيز Reel. لذلك يرفع التطبيق MP4 إلى Cloudflare R2 ثم يرسل عنوانه إلى Meta.

1. أنشئ R2 bucket باسم `mesut-instagram-media` أو اختر اسماً آخر.
2. اربط bucket بالـ Worker بإضافة هذا المقطع إلى `wrangler.toml`:

```toml
[[r2_buckets]]
binding = "MEDIA_BUCKET"
bucket_name = "mesut-instagram-media"
```

3. اربط نطاق HTTPS مخصصاً بالـ bucket، واجعله يعرض الملفات دون مصادقة حتى تستطيع Meta تنزيل الفيديو. لا تفعّل عرض قائمة محتويات bucket.
4. اضبط متغير Worker باسم `MEDIA_PUBLIC_BASE_URL` ليكون جذر النطاق العام، دون شرطة مائلة في النهاية، مثال: `https://media.example.com`.
5. أضف قاعدة Lifecycle في R2 لحذف الملفات داخل المسار `instagram/` بعد يوم واحد كحد احتياطي للملفات التي لم يُكمل نشرها.

يقبل Worker حالياً فيديو MP4 حتى 95 MB. الروابط العامة للوسائط حساسة؛ استخدم نطاقاً مخصصاً، وأبقِ أسماء الملفات عشوائية، ولا تعِد استخدام الروابط بعد انتهاء عملية النشر.

## 4. النشر والتثبيت

بعد ضبط متغيرات Meta وR2:

```bash
npm install
npm run deploy
```

تأكد أن `TIKTOK_WORKER_URL` في إعداد بناء Android يشير إلى نفس Worker المنشور. لا تحتاج إلى إضافة Meta App Secret إلى إعدادات بناء Android.

## 5. تدفق الاستخدام داخل التطبيق

1. افتح MESUT AI Manager.
2. اختر **ربط حساب Instagram رسمياً**.
3. أكمل تسجيل الدخول في صفحة Meta ووافق على الصلاحيات.
4. يجب أن يكون الحساب الاحترافي مرتبطاً بصفحة Facebook يمكن للحساب إدارتها.
5. أنشئ فيديو MP4 من محرر MESUT.
6. اختر **نشر آخر فيديو كـ Reel على Instagram** ثم أكد النشر.
7. يرفع التطبيق الفيديو إلى Worker، ويُخزن مؤقتاً في R2، ثم ينشئ Media Container.
8. ينتظر التطبيق حالة `FINISHED`، وبعدها ينفذ `media_publish`.
9. بعد نجاح النشر يحذف Worker ملف R2 المؤقت. وتظل قاعدة Lifecycle احتياطاً للملفات المتروكة.

## 6. مسارات Worker

| المسار | الوظيفة |
|---|---|
| `GET /instagram/oauth/start` | بدء OAuth مع state عشوائي أحادي الاستخدام |
| `GET /instagram/oauth/callback` | تبادل رمز Meta واكتشاف حساب Instagram الاحترافي |
| `POST /instagram/session/exchange` | تحويل رمز handoff قصير العمر إلى جلسة التطبيق |
| `POST /instagram/me` | عرض اسم الحساب المتصل |
| `PUT /instagram/media/upload` | رفع MP4 إلى R2 بتدفق مباشر |
| `POST /instagram/publish/container` | إنشاء Reel container |
| `POST /instagram/publish/status` | فحص حالة معالجة الفيديو |
| `POST /instagram/publish/finalize` | نشر الحاوية بعد FINISHED وحذف الملف المؤقت |
| `POST /instagram/disconnect` | فصل الجلسة وحذف سجل الحساب المشفر |

جلسة Instagram مستقلة عن جلسة TikTok. جميع الرموز طويلة العمر تبقى في Worker/KV مشفرة باستخدام AES-GCM. جلسة التطبيق وحدها تصل إلى APK.

## القيود الحالية

- تكامل النشر الحالي مخصص لـ Reels وفيديو MP4 فقط.
- الحد الذي يفرضه Worker في هذا الإصدار هو 95 MB.
- ينتظر التطبيق معالجة Meta حتى ثلاث دقائق؛ إذا لم تصبح الحاوية جاهزة، يعرض فشل الانتظار ولا يدّعي نجاح النشر.
- يجب أن يكون عنوان R2 العام قابلاً للوصول من خوادم Meta عبر HTTPS.
- يجب أن تكون صلاحيات Meta فعالة ومقبولة. وجود الكود لا يعني أن Meta وافقت على التطبيق أو منحته Advanced Access.
- عند انقطاع التطبيق بعد إنشاء الحاوية وقبل النشر النهائي، قد يبقى ملف R2 مؤقتاً حتى تنفذ قاعدة Lifecycle.

## الوثائق الرسمية

- https://developers.facebook.com/docs/instagram-platform/
- https://developers.facebook.com/docs/instagram-platform/instagram-api-with-facebook-login/
- https://developers.facebook.com/docs/instagram-platform/content-publishing/
- https://developers.facebook.com/docs/resp-plat-initiatives/individual-processes/app-review/
