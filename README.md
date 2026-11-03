# Messages — تطبيق رسائل SMS للأندرويد

تطبيق رسائل SMS متكامل مبني بـ Kotlin و Jetpack Compose، بواجهة عربية وإنجليزية.

A full-featured Android SMS client built with Kotlin and Jetpack Compose, with
Arabic and English interfaces.

## التحميل / Download

**أحدث نسخة (1.1.0):**

| | |
|---|---|
| رابط مباشر | [`app-release.apk`](https://github.com/alifaleh302-dev/sms-messenger/releases/download/v1.1.0/app-release.apk) |
| صفحة الإصدار | [Releases v1.1.0](../../releases/tag/v1.1.0) |
| الحجم | 2.3 ميجابايت |
| بصمة SHA-256 | `ee687a1369cf3c704c12332774f657710baac254c708b6db81d00f6b6b4faea7` |

للتحقق من سلامة الملف بعد التحميل:

```bash
sha256sum app-release.apk
```

1. حمّل ملف APK على هاتفك.
2. افتح الملف، وإذا طلب النظام السماح بالتثبيت من مصدر غير معروف فاسمح بذلك.
3. ثبّت التطبيق، ثم افتحه وامنح أذونات الرسائل وجهات الاتصال.
4. من الإعدادات اضغط «تعيين كافتراضي» ليصبح التطبيق تطبيق الرسائل الافتراضي.

للتحقق من التوقيع:

```bash
apksigner verify --print-certs app-release.apk
```

بصمة الشهادة SHA-256:

```
B5:FB:4E:50:B2:6D:1C:EA:4F:51:67:41:58:88:D5:90:AE:16:00:5D:15:51:3D:B3:D7:D1:6D:2D:DF:2A:24:B9
```

## المزايا / Features

- **إرسال واستقبال SMS** مع دعم الرسائل متعددة الأجزاء وتتبّع حالة كل جزء على حدة
  (أُرسلت / تم التسليم).
- **المحادثات** مجمّعة تلقائياً حسب الرقم مع دمج الصيغ المختلفة لنفس الرقم في محادثة واحدة.
- **تطبيق الرسائل الافتراضي**: التعامل مع دور `SMS_DELIVER`، وخدمة الرد عبر الرسائل
  (`RESPOND_VIA_MESSAGE`)، وعقد MMS.
- **الإشعارات** لكل محادثة مع رد مضمّن مباشر من الإشعار وزر «تحديد كمقروء».
- **جهات الاتصال**: البحث بالاسم أو الرقم، وعرض اسم جهة الاتصال بدل الرقم.
- **القوالب**: ردود جاهزة قابلة للإدراج أثناء الكتابة.
- **الرسائل المجدولة**: اختيار التاريخ والوقت، مع إعادة الجدولة تلقائياً بعد إعادة تشغيل الجهاز.
- **حظر الأرقام**: الرسائل الواردة من الأرقام المحظورة تُهمل ولا تُخزَّن.
- **البحث** في كل الرسائل والمحادثات.
- **الأرشفة والتثبيت والكتم** للمحادثات.
- **الإعدادات**: المظهر (فاتح/داكن/النظام)، اللغة، الإشعارات والاهتزاز والنغمة،
  تقارير التسليم، إعادة المحاولة التلقائية، التوقيع، واستيراد الرسائل من النظام.
- **العربية والإنجليزية** مع تبديل اللغة داخل التطبيق.

## البناء من المصدر / Build from source

المتطلبات: JDK 17 أو أحدث، و Android SDK 35.

```bash
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-21

# فحص شامل قبل البناء
bash tools/audit.sh

# بناء النسخة الموقّعة
bash tools/gbuild.sh :app:assembleRelease :app:testDebugUnitTest
```

لإنشاء مفتاح إصدار خاص بك، أنشئ ملف `keystore.properties` في جذر المشروع:

```properties
storeFile=keystore/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

المسار نسبةً إلى جذر المشروع. الملفان `keystore.properties` و `*.jks` مُستثنيان من git.

بدون ملف المفتاح تُبنى نسخة الإصدار بمفتاح التصحيح تلقائياً.

## الأدوات / Tooling

- `tools/gbuild.sh` — بناء سريع مع Gradle daemon والكاش، ينتظر السجل عبر `inotifywait`
  بدل مدة تخمينية، ويطبع أسطر الأخطاء فقط.
- `tools/audit.sh` — فحص شامل بمرور واحد: استيرادات غير مستخدمة، نصوص مفقودة في
  `values/` أو `values-ar/`، نص إنجليزي مكتوب يدوياً، أيقونات أو رسومات غير موجودة.

## البنية / Architecture

```
app/src/main/java/com/sms/messages/
├── di/              Hilt modules: database, repositories, senders, application scope
├── data/            Room entities, DAOs, SmsRepository, settings, contacts, import
├── sms/             Receivers and services: send/receive, status, MMS, scheduling, boot
├── notifications/   Channels and per-conversation notifications with inline reply
├── ui/
│   ├── screens/     Conversations, chat, compose, search, templates, scheduled, settings
│   ├── components/  Message bubble, composer, search pill, avatar, empty state
│   ├── viewmodel/   One view model per screen
│   └── theme/       Material 3 theme
└── util/            Phone normalisation, time formatting, permissions, default-SMS role
```

### حقن التبعيات / Dependency injection

المشروع يستخدم **Hilt**. لا يوجد أي وصول مباشر إلى `Application` من الشاشات أو
مستقبلات البث:

- `SmsApp` معلَّم بـ `@HiltAndroidApp` ويحقن `CoroutineScope` فقط.
- `di/AppModule` يوفّر قاعدة البيانات والمستودعات والمُرسِلات كـ `@Singleton`.
- كل `ViewModel` معلَّم بـ `@HiltViewModel` ويستقبل تبعياته عبر `@Inject constructor`،
  والشاشات تحصل عليه بـ `hiltViewModel()`.
- المستقبلات والخدمات (`SmsReceiver`, `SmsStatusReceiver`, `BootReceiver`,
  `NotificationActionReceiver`, `ScheduledSendReceiver`, `HeadlessSmsSendService`)
  معلَّمة بـ `@AndroidEntryPoint` وتحصل على تبعياتها بحقن الحقول.

`SmsRepository` هو نقطة الدخول الوحيدة لكل ما يتعلق بالمحادثات، وكل المستقبلات
والشاشات تمر عبره.

### الواجهة / UI

التصميم يتبع بنية **Google Messages** المعروفة: شريط علوي بعنوان وزر حساب، حقل بحث
بيضاوي، قائمة محادثات بدون فواصل، زر إنشاء محادثة ممتد أسفل يمين الشاشة، وفقاعات
رسائل بذيل في الزاوية ووقت داخل الفقاعة، وفاصل تاريخ بين المجموعات.

## الإصدارات / Versions

| الإصدار | أبرز التغييرات |
|---------|----------------|
| 1.1.0   | بنية Hilt، تصميم Google Messages، إصلاحات من اختبارات Robolectric |
| 1.0.2   | تنظيف الأذونات وتوثيق نموذج الأذونات |
| 1.0.1   | إصلاح انهيار الإقلاع، تسريب النسخ الاحتياطي، ترجمة تنسيق الوقت |

## الاختبارات / Tests

```bash
bash tools/gbuild.sh :app:testDebugUnitTest
```

٥٦ اختبارًا تعمل على JVM. اختبارات Robolectric تشغّل نظام أندرويد الحقيقي (Room +
SQLite + جداول الموارد) بدل محاكاته:

| الملف | ما يغطيه |
|-------|----------|
| `MessageStoreTest` | إرسال/استقبال، حالات الرسائل، المرفوضة، القوالب، المجدولة، غير المقروء |
| `ContactResolverTest` | البحث في جهات الاتصال مع ذاكرة مؤقتة (كان ينهار قبل الإصلاح) |
| `SmsSenderTest` | بوابات الإرسال والانحدار الآمن عند تعذّر التقسيم |
| `NotificationTextTest` | ترجمة النصوص والأرقام العربية وسلوك معرّفات الإشعارات |
| `PhoneNumbersTest`, `TimeFormatTest`, `SearchQueryTest`, `LocalizationTest` | التطبيع والتنسيق والبحث |

## الأذونات / Permissions

يطلب التطبيق أقل عدد ممكن من الأذونات:

| الإذن | السبب |
|---|---|
| `SEND_SMS`, `RECEIVE_SMS`, `READ_SMS` | إرسال الرسائل واستقبالها وعرض سجلها |
| `RECEIVE_MMS`, `RECEIVE_WAP_PUSH` | استقبال رسائل الوسائط |
| `READ_CONTACTS` | عرض أسماء جهات الاتصال بدل الأرقام |
| `POST_NOTIFICATIONS` | تنبيهك بالرسائل الجديدة |
| `VIBRATE` | الاهتزاز عند وصول رسالة |
| `INTERNET` | تنزيل محتوى رسائل الوسائط من الشبكة |

لا يطلب التطبيق أذونات المكالمات أو الصور أو الموقع أو الملفات. زر الاتصال يفتح
تطبيق الهاتف عبر `ACTION_DIAL` الذي لا يحتاج أي إذن. الرسائل لا تُرفع إلى أي خادم،
والنسخ الاحتياطي السحابي مقصور على إعدادات التطبيق ولا يشمل نص أي رسالة.

## الترخيص / License

MIT
