# These rules are (hopefully) temporary needed to use R8 in full mode;
# these solve crashes such as:
#   java.lang.RuntimeException: cannot find implementation for
#       com.ubergeek42.WeechatAndroid.media.CachePersist$AttemptDatabase.
#       CachePersist$AttemptDatabase_Impl does not exist
# Probable issue: https://issuetracker.google.com/issues/218578949
-keep class com.ubergeek42.WeechatAndroid.media.CachePersist
-keep class com.ubergeek42.WeechatAndroid.notifications.ShortcutStatisticsDatabase
-keep class com.ubergeek42.WeechatAndroid.upload.UploadDatabase

# see http://stackoverflow.com/questions/5701126/compile-with-proguard-gives-exception-local-variable-type-mismatch
-dontobfuscate
-optimizations !code/allocation/variable

-optimizationpasses 5

# support library stuff
-keep public class android.support.v7.preference.** { *; }

-dontwarn org.ietf.jgss.*
-dontwarn com.jcraft.jzlib.ZStream

# neede for ssh
-keep public class com.trilead.ssh2.compression.**
-keep public class com.trilead.ssh2.crypto.**
-keep class com.trilead.ssh2.crypto.keys.Ed25519*Key { *; }

# strip debug and trace (verbose) logging
-assumenosideeffects class org.slf4j.Logger {
    public void debug(...);
    public void trace(...);
}
-dontwarn org.slf4j.impl.StaticMDCBinder
-dontwarn org.slf4j.impl.StaticMarkerBinder
-dontwarn org.slf4j.impl.StaticLoggerBinder

-assumenosideeffects class com.ubergeek42.WeechatAndroid.utils.Assert {
    assertThat(...);
}
-assumenosideeffects class com.ubergeek42.WeechatAndroid.utils.Assert$A {
    is*(...);
    *ontain*(...);
}

-keepclassmembers class ** {
    public void onEvent*(**);
}

# glide
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl

# https://github.com/Kotlin/kotlinx.serialization#android
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.ubergeek42.weechat.**$$serializer { *; }
-keepclassmembers class com.ubergeek42.weechat.** {
    *** Companion;
}
-keepclasseswithmembers class com.ubergeek42.weechat.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# the following are rules that are needed for PEM/PKCS #8 decoding (AndroidKeyStoreUtils.kt)
# these have been roughly determined by placing a logging break point on:
#   java.lang.ClassLoader.loadClass(java.lang.String, boolean)
-keep class org.bouncycastle.jcajce.provider.digest.MD2** { *; }
-keep class org.bouncycastle.jcajce.provider.digest.MD4** { *; }
-keep class org.bouncycastle.jcajce.provider.digest.MD5** { *; }
-keep class org.bouncycastle.jcajce.provider.digest.SHA1** { *; }
-keep class org.bouncycastle.jcajce.provider.digest.SHA224** { *; }
-keep class org.bouncycastle.jcajce.provider.digest.SHA256** { *; }
-keep class org.bouncycastle.jcajce.provider.digest.SHA384** { *; }
-keep class org.bouncycastle.jcajce.provider.digest.SHA512** { *; }

-keep class org.bouncycastle.jcajce.provider.symmetric.PBEPBKDF1** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.PBEPBKDF2** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.PBEPKCS12** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.AES** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.ARC4** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.ARIA** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.Blowfish** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.Camellia** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.DES** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.DESede** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.IDEA** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.RC2** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.RC5** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.RC6** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.Twofish** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.Threefish** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.OpenSSLPBKDF** { *; }

-keep class org.bouncycastle.jcajce.provider.asymmetric.DSA$Mappings { *; }
-keep class org.bouncycastle.jcajce.provider.asymmetric.EC$Mappings { *; }
-keep class org.bouncycastle.jcajce.provider.asymmetric.RSA$Mappings { *; }
-keep class org.bouncycastle.jcajce.provider.asymmetric.EdEC$Mappings { *; }

-keep class org.bouncycastle.jcajce.provider.keystore.BC$Mappings { *; }
-keep class org.bouncycastle.jcajce.provider.keystore.BCFKS$Mappings { *; }
-keep class org.bouncycastle.jcajce.provider.keystore.PKCS12$Mappings { *; }

-keep class org.bouncycastle.jcajce.provider.asymmetric.dsa.KeyFactorySpi { *; }
-keep class org.bouncycastle.jcajce.provider.asymmetric.rsa.KeyFactorySpi { *; }
-keep class org.bouncycastle.jcajce.provider.asymmetric.ec.KeyFactorySpi** { *; }
-keep class org.bouncycastle.jcajce.provider.asymmetric.edec.KeyFactorySpi** { *; }
-keep class org.bouncycastle.jcajce.provider.asymmetric.edec.KeyFactorySpi** { *; }

-keep class org.bouncycastle.jcajce.provider.drbg.DRBG$Mappings { *; }
-keep class javax.crypto.spec.GCMParameterSpec { *; }
-keep class org.bouncycastle.openssl.jcajce.PEMUtilities** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.util.IvAlgorithmParameters { *; }

# Remove missing classes warnings due to missing OkHttp proguard rules.
# Should be safe to remove after updating to OkHttp 5.x.
# https://github.com/square/okhttp/issues/6258
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE

# Remove missing classes warnings due to SnakeYAML. Mozilla uses these too.
# https://github.com/mozilla-mobile/focus-android/blob/main/app/proguard-rules.pro
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.FeatureDescriptor
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor


# Gets rid of the warning,
#   Missing class com.google.errorprone.annotations.Immutable
#   (referenced from: com.google.crypto.tink.util.Bytes)
# Should be safe to use. See:
#   https://github.com/google/tink/issues/536
#   https://issuetracker.google.com/issues/195752905
-dontwarn com.google.errorprone.annotations.Immutable

# ~*~*~*~ Historical rules, left here for lamenting and general amusement ~*~*~*~

# Looks like we aren't getting warnings anymore!
# # Warnings prevent build from continuing
# -ignorewarnings

# Did we use JUnit in production or something?
# # JUnit stuff
# -assumenosideeffects class org.junit.Assert {
#   public static *** assert*(...);
# }
# -dontwarn java.lang.management.*
#
# # Prevents warnings such as
# #   library class android.test.AndroidTestCase extends or implements program class junit.framework.TestCase
# # Maybe should be done differently?
# -dontwarn android.test.**

# We used to depend on commons-codec for hex and sha-256 things.
# This package is problematic because it is supplied by the system, and
# on earlier versions of Android it doesn't come with methods that we want to use.
# This lead to all sorts of problems, particularly with ProGuard and R8,
# and would often result in various crashes in production builds on earlier platforms.
# Perhaps it's best to just not use it.
# -keep class org.apache.commons.codec.digest.* { *; }
# -keep class org.apache.commons.codec.binary.* { *; }
# -keep class org.apache.commons.codec.*Exception { *; }
