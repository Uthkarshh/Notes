# Keep rules for kotlinx.serialization-generated serializers used by navigation routes and status schemas
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.notesapp.** {
    *** Companion;
}
-keepclasseswithmembers class com.notesapp.** {
    kotlinx.serialization.KSerializer serializer(...);
}
