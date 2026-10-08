# kotlinx.serialization keeps generated serializers for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class app.pratyahara.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class app.pratyahara.detection.ReelsAccessibilityService
