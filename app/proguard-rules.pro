# Alakey R8 rules.
#
# Crash triage (L3): keep line numbers so release stack traces are
# symbolicateable via the uploaded mapping.txt, and stop the obfuscator
# from erasing where a crash actually happened.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room entities/converters are reflected over by the Room runtime.
-keep class com.example.alakey.data.** { *; }

# Gson deserialization targets.
-keep class com.example.alakey.data.Chapter { *; }
-keep class com.example.alakey.data.PodcastPalette { *; }
