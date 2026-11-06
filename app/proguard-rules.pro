# The Anthropic SDK reads its models through Jackson, by reflection.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keep class com.anthropic.core.** { *; }
-keep class com.anthropic.errors.** { *; }
-keep class com.anthropic.helpers.BetaMessageAccumulator* { *; }
-keep class com.anthropic.models.ErrorObject* { *; }
-keep class com.anthropic.models.*Error* { *; }
-keep class com.anthropic.models.messages.Model* { *; }
-keep class com.anthropic.models.beta.AnthropicBeta* { *; }
-keep class com.anthropic.models.beta.*Error* { *; }
-keep class com.anthropic.models.beta.messages.** { *; }
-keep class com.anthropic.models.models.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* <fields>;
    @com.fasterxml.jackson.annotation.* <methods>;
    @com.fasterxml.jackson.databind.annotation.* <fields>;
    @com.fasterxml.jackson.databind.annotation.* <methods>;
}

# sherpa-onnx calls back into these classes from native code.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Optional parts of the SDK's dependencies that the app never loads.
-dontwarn com.github.victools.**
-dontwarn io.swagger.**
-dontwarn com.standardwebhooks.**
-dontwarn io.opentelemetry.**
-dontwarn org.slf4j.**
-dontwarn java.beans.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Used only by the SDK helper for typed structured outputs, which the app does not call.
-dontwarn java.lang.reflect.AnnotatedType
