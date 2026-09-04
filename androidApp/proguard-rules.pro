# The level files are parsed by kotlinx.serialization, whose generated serializers are
# reached reflectively. The library ships consumer rules; these make the intent explicit
# for the model classes this app actually deserialises.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.aectann.battlecity.engine.**$$serializer { *; }
-keepclassmembers class com.aectann.battlecity.engine.** {
    *** Companion;
}
-keepclasseswithmembers class com.aectann.battlecity.engine.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Compose Multiplatform resources are looked up by path at runtime.
-keep class battlecity.shared.generated.resources.** { *; }
