# Keep all class and member names readable (disable obfuscation)
# R8 will still perform tree-shaking and code optimization.
-keepattributes SourceFile,LineNumberTable
-keepclassmembers class * {
    *;
}
-keep class * {
    *;
}
