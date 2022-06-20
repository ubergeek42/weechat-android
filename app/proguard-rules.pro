# warnings prevent build from continuing
# looks like we aren't getting warnings anymore
#-ignorewarnings

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

-dontskipnonpubliclibraryclasses
-forceprocessing
-optimizationpasses 5

# support library stuff
-keep public class android.support.v7.preference.** { *; }

-dontwarn org.ietf.jgss.*
-dontwarn com.jcraft.jzlib.ZStream

# neede for ssh
-keep public class com.trilead.ssh2.compression.**
-keep public class com.trilead.ssh2.crypto.**

# strip debug and trace (verbose) logging
-assumenosideeffects class org.slf4j.Logger {
    public void debug(...);
    public void trace(...);
}
-dontwarn org.slf4j.impl.StaticMDCBinder
-dontwarn org.slf4j.impl.StaticMarkerBinder
-dontwarn org.slf4j.impl.StaticLoggerBinder

# junit stuff
-assumenosideeffects class org.junit.Assert {
  public static *** assert*(...);
}
-dontwarn java.lang.management.*

-assumenosideeffects class com.ubergeek42.WeechatAndroid.utils.Assert {
    assertThat(...);
}
-assumenosideeffects class com.ubergeek42.WeechatAndroid.utils.Assert$A {
    is*(...);
    *ontain*(...);
}

# prevents warnings such as "library class android.test.AndroidTestCase extends or implements program class junit.framework.TestCase"
# maybe should be done differently?
-dontwarn android.test.**

-keepclassmembers class ** {
    public void onEvent*(**);
}
-keep class org.apache.commons.codec.digest.* { *; }
-keep class org.apache.commons.codec.binary.* { *; }

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
