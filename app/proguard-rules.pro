# VARUGAI 16 release R8 rules.
# Room entities are persisted by generated adapters and must retain field/constructor metadata.
-keep @androidx.room.Entity class com.gasczoology.varugai.data.db.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Compose runtime ships consumer rules; retain composable/runtime metadata needed by generated code.
-keepattributes *Annotation*,InnerClasses,EnclosingMethod
-keep class androidx.compose.runtime.** { *; }

# BackupCodec currently uses explicit JsonElement/JsonObject encoding, not reflective DTO serialization.
# Keep generated serializers if future @Serializable models are added.
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# SQLCipher ships consumer rules; no app-specific reflective entry points are used.
