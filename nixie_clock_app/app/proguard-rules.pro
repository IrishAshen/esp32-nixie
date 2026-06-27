# Keep Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.nixieclock.model.** { *; }
-keep class com.nixieclock.util.Config { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
