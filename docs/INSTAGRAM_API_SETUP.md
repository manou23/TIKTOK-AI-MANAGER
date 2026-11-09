# إعداد Instagram الرسمي — MESUT AI Social Manager

هذا الدليل يحدد مسار التكامل المقترح مع Instagram باستخدام واجهة Meta الرسمية. هذه الإضافة لا تستخدم كلمة مرور Instagram ولا تحاكي تطبيق الهاتف.

## الحسابات المدعومة

يعتمد المسار الأول على **Instagram API with Facebook Login**، ولذلك يلزم:

- حساب Instagram احترافي من نوع Business أو Creator.
- ربط حساب Instagram بصفحة Facebook.
- أن يكون حساب Facebook الذي يسجل الدخول مخولاً لإدارة الصفحة والحساب المرتبط.
- تطبيق Meta Developer مع منتج Instagram API / Facebook Login.

لا تدعم واجهة النشر الحسابات الشخصية العادية.

## الصلاحيات المطلوبة

ابدأ بأقل مجموعة لازمة للوظائف المطلوبة، ثم اطلب Advanced Access ومراجعة التطبيق عند الحاجة إلى خدمة مستخدمين خارج أدوار التطبيق:

- `pages_show_list` لاكتشاف الصفحات التي يديرها المستخدم.
- `pages_read_engagement` لقراءة بيانات الصفحة المطلوبة في تدفق الربط.
- `instagram_basic` لقراءة بيانات حساب Instagram الاحترافي.
- `instagram_content_publish` لإنشاء ونشر المحتوى.

قد تتغير متطلبات Meta بحسب نوع تسجيل الدخول وإصدار Graph API؛ يجب مطابقة الصلاحيات مع لوحة Meta والوثائق الرسمية وقت التنفيذ، وعدم طلب صلاحيات غير مستخدمة.

## إعداد Meta Developer

1. أنشئ تطبيقاً في https://developers.facebook.com/.
2. أضف منتجات تسجيل الدخول وInstagram API المناسبة.
3. أضف رابط OAuth callback الذي سيظهر بعد نشر خدمة MESUT.
4. أضف نطاقات OAuth المطلوبة أعلاه.
5. أكمل App Review وBusiness Verification أو أي متطلبات تظهر في لوحة Meta قبل استخدام التطبيق مع حسابات خارج أدوار الاختبار.
6. لا تضع App Secret أو access token في APK أو في مستودع Git.

الوثائق الرسمية:
- https://developers.facebook.com/docs/instagram-platform/
- https://developers.facebook.com/docs/instagram-platform/instagram-api-with-facebook-login/
- https://developers.facebook.com/docs/instagram-platform/content-publishing/
- https://developers.facebook.com/docs/resp-plat-initiatives/individual-processes/app-review/

## تدفق النشر

واجهة Instagram للنشر تعتمد على مرحلتين:

1. إنشاء Media Container باستخدام `POST /{ig-user-id}/media` مع رابط عام للوسيط والتعليق والإعدادات المناسبة.
2. انتظار جاهزية الحاوية والتحقق من حالتها عند الحاجة، ثم استدعاء `POST /{ig-user-id}/media_publish` باستخدام `creation_id`.

يدعم المسار أنواع محتوى مختلفة وفق قيود Meta، مثل الصور والفيديو وReels وCarousel. يجب التحقق من قيود الملف ونوع الوسيط قبل إنشاء الحاوية.

**مهم:** Instagram يجلب الوسائط من عنوان URL يمكن الوصول إليه علناً. لذلك لا يكفي إرسال مسار ملف محلي من هاتف Android. قبل تفعيل النشر، يجب تجهيز تخزين مؤقت آمن للوسائط (مثل Cloudflare R2 أو تخزين متوافق)، بروابط مؤقتة ومحدودة العمر، ثم حذف الملفات المؤقتة بعد انتهاء النشر أو انتهاء صلاحيتها.

## الأمان والتخزين

- تتم المصادقة عبر OAuth الرسمي فقط.
- تحفظ أسرار Meta ورموز الوصول طويلة العمر في الخادم، مشفرة باستخدام AES-GCM، ولا تدخل APK.
- يحتفظ التطبيق برمز جلسة خاص بالخادم فقط.
- يجب تنفيذ تدوير/تحديث الرموز، وإلغاء الربط، وحذف بيانات الحساب عند فصل الحساب.
- لا تطلب من المستخدم إرسال Client Secret أو Access Token في المحادثة أو وضعها في ملفات المصدر.

## أسرار GitHub المقترحة (بعد تنفيذ الخادم)

الأسماء التالية مقترحة للتجهيز المستقبلي، وليست مطلوبة قبل أن يصبح مسار Instagram مدمجاً ومختبراً:

- `META_APP_ID`
- `META_APP_SECRET` (الخادم فقط)
- `META_REDIRECT_URI`
- `META_GRAPH_API_VERSION`
- `INSTAGRAM_MEDIA_BUCKET` أو إعدادات R2 المناسبة
- مفاتيح تشفير الخادم (مستقلة عن مفتاح TikTok)

لا تضف أسراراً غير مستخدمة، ولا تشغّل Workflow النشر قبل إعداد KV/R2 وصلاحيات Meta.

## الحالة

هذه الوثيقة هي مواصفات الإعداد والتكامل. وجودها لا يعني أن Instagram OAuth أو النشر أصبحا فعالين في التطبيق. لا يُعلن دعم النشر إلا بعد إكمال خادم OAuth وتخزين الوسائط وتدفق Media Container/Publish واختبارات API الحقيقية ومراجعة Meta المطلوبة.
