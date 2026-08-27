# SeaCard — правила для R8 (release)

# Читаемые стектрейсы в Play Console / Firebase (деобфускация по mapping.txt)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Аннотации (Room, Compose и др.)
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# --- ZXing ---
-keep class com.google.zxing.** { *; }

# --- Kotlin / корутины ---
-dontwarn kotlinx.coroutines.**

# Виджет: провайдер объявлен в манифесте
-keep class ru.merrcurys.seacard.widget.SeaCardAppWidgetProvider { <init>(); }
