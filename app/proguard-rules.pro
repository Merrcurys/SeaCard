# SeaCard — правила для R8 (release)

# Читаемые стектрейсы в Play Console / Firebase (деобфускация по mapping.txt)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Аннотации, generics-сигнатуры и вложенные классы (Room, Kotlin, reflection)
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# --- Room ---
# Реализация AppDatabase_Impl создаётся рефлексивно: Room.databaseBuilder(...) делает
# Class.forName(name + "_Impl"). База и её сгенерированная реализация не должны вырезаться
# и переименовываться.
-keep class * extends androidx.room.RoomDatabase { *; }

# --- ViewModel ---
# Конструкторы ViewModel вызываются рефлексивно (AndroidViewModelFactory / NewInstanceFactory),
# поэтому их нельзя вырезать (например, MainViewModel(Application), SettingsViewModel(Application)).
-keep class * extends androidx.lifecycle.ViewModel { <init>(); }

# --- ZXing (генерация штрих-кодов) ---
-keep class com.google.zxing.** { *; }

# --- Виджет ---
# Провайдер объявлен в манифесте; правило — страховка от переименования при работе через
# ComponentName и RemoteViews.
-keep class ru.merrcurys.seacard.widget.SeaCardAppWidgetProvider { <init>(); }
