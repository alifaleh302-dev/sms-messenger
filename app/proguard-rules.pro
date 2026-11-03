# Keep Room generated implementations
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# Keep data classes used by Room/reflection-free mapping
-keepclassmembers class com.sms.messages.data.** { *; }

# Kotlin metadata
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable
