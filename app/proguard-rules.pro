-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile
-dontobfuscate

-keepclasseswithmembers public class androidx.recyclerview.widget.RecyclerView { *; }
-keep class io.cueapp.android.ui.fragments.preferences.*
-keep class io.cueapp.android.importers.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

-dontwarn javax.naming.**
