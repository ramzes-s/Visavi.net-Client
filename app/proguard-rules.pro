# ProGuard / R8 rules for VisaviNet

# 1. Отключение удаления метаданных и типов (Signature, Reflection)
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, SourceFile, LineNumberTable

# 2. Сохранять конструкторы по умолчанию и инициализаторы
-keepclassmembers class * {
    public <init>();
    public <init>(...);
}

# 3. Сохранять ВСЕ сетевые модели и их поля
-keep class com.ramzes.visavinet.network.** { *; }
-keepclassmembers class com.ramzes.visavinet.network.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 4. Сохранять интерфейсы и генераторы Retrofit
-keep interface com.ramzes.visavinet.network.VisaviApiService { *; }
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# 5. Сохранять Gson
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.TypeAdapter

# 6. Игнорировать предупреждения внешних библиотек
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn okio.**
-dontwarn com.google.gson.**
-dontwarn coil.**

-keep class coil.** { *; }