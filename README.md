# Messages — تطبيق رسائل SMS للأندرويد

تطبيق رسائل SMS متكامل مبني بـ Kotlin و Jetpack Compose، بواجهة عربية وإنجليزية.

A full-featured Android SMS client built with Kotlin and Jetpack Compose, with
Arabic and English interfaces.

## التحميل / Download

**أحدث نسخة:** انتقل إلى صفحة [Releases](../../releases/latest) وحمّل ملف
`app-release.apk`.

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
├── data/            Room entities, DAOs, SmsRepository, settings, contacts, import
├── sms/             Receivers and services: send/receive, status, MMS, scheduling, boot
├── notifications/   Channels and per-conversation notifications with inline reply
├── ui/
│   ├── screens/     Conversations, chat, compose, search, templates, scheduled, settings
│   ├── components/  Message bubble, empty state, avatar
│   ├── viewmodel/   One view model per screen
│   └── theme/       Material 3 theme
└── util/            Phone normalisation, time formatting, permissions, default-SMS role
```

`SmsRepository` هو نقطة الدخول الوحيدة لكل ما يتعلق بالمحادثات، وكل المستقبلات
والشاشات تمر عبره.

## الاختبارات / Tests

```bash
bash tools/gbuild.sh :app:testDebugUnitTest
```

تغطي تطبيع أرقام الهاتف، تنسيق الوقت، وتكافؤ النصوص بين العربية والإنجليزية.

## الأذونات / Permissions

`SEND_SMS`, `RECEIVE_SMS`, `READ_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH`,
`READ_PHONE_STATE`, `READ_CONTACTS`, `CALL_PHONE`, `POST_NOTIFICATIONS`,
`VIBRATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `INTERNET`

## الترخيص / License

MIT
