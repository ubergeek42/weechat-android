package com.ubergeek42.WeechatAndroid.utils;

import android.security.keystore.KeyInfo;

import androidx.annotation.NonNull;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcContentSignerBuilder;
import org.bouncycastle.operator.bc.BcDSAContentSignerBuilder;
import org.bouncycastle.operator.bc.BcECContentSignerBuilder;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;

import java.io.IOException;
import java.math.BigInteger;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.Calendar;
import java.util.Collections;

public class AndroidKeyStoreUtils {
    private interface SignerBuilder {
        BcContentSignerBuilder make(AlgorithmIdentifier sigAlgId,
                                    AlgorithmIdentifier digAlgId);
    }

    // this is generating a certificate that is required to put a KeyPair into AndroidKeyStore.
    // we will not be using this certificate, only its public key.
    // answer by Tolga Okur https://stackoverflow.com/a/59182063/1449683
    public static X509Certificate generateCertificate(KeyPair keyPair)
            throws IOException, OperatorCreationException, CertificateException {
        String keyAlgorithm = keyPair.getPublic().getAlgorithm();

        String signingAlgorithm;
        SignerBuilder signerBuilder;
        switch (keyAlgorithm) {
            case "RSA":
                signingAlgorithm = "SHA256withRSA";
                signerBuilder = BcRSAContentSignerBuilder::new;
                break;
            case "EC":
            case "ECDSA":
                signingAlgorithm = "SHA256withECDSA";
                signerBuilder = BcECContentSignerBuilder::new;
                break;
            case "DSA":
                signingAlgorithm = "SHA256withDSA";
                signerBuilder = BcDSAContentSignerBuilder::new;
                break;
            default:
                throw new CertificateException("Can't make a certificate for a key algorithm " +
                        keyAlgorithm);
        }

        AlgorithmIdentifier sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder().find(signingAlgorithm);
        AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);
        AsymmetricKeyParameter keyParam = PrivateKeyFactory.createKey(keyPair.getPrivate().getEncoded());
        SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());
        ContentSigner signer = signerBuilder.make(sigAlgId, digAlgId).build(keyParam);
        X500Name issuer = new X500Name("CN=Wee");
        X500Name subject = new X500Name("CN=Chat");
        BigInteger serial = BigInteger.valueOf(1);      // Update with unique one if it will be used to identify this certificate
        Calendar notBefore = Calendar.getInstance();
        Calendar notAfter = Calendar.getInstance();
        notAfter.add(Calendar.YEAR, 20);                // This certificate is valid for 20 years.

        X509v3CertificateBuilder v3CertGen = new X509v3CertificateBuilder(issuer,
                serial,
                notBefore.getTime(),
                notAfter.getTime(),
                subject,
                spki);
        X509CertificateHolder certificateHolder = v3CertGen.build(signer);

        return new JcaX509CertificateConverter().getCertificate(certificateHolder);
    }

    private static KeyStore androidKeyStore = null;
    public static KeyStore getAndroidKeyStore()
            throws CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException {
        if (androidKeyStore == null) {
            androidKeyStore = KeyStore.getInstance("AndroidKeyStore");
            androidKeyStore.load(null);
        }
        return androidKeyStore;
    }

    // YES if key inside Trusted Execution Environment (TEE) or Secure Element (SE), NO if not
    // CANT_TELL on Android < M which doesn't support the inquiry method
    public enum InsideSecureHardware {
        YES,
        NO,
        CANT_TELL
    }

    public static @NonNull InsideSecureHardware isInsideSecurityHardware(PrivateKey privateKey)
            throws NoSuchProviderException, NoSuchAlgorithmException, InvalidKeySpecException {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M)
            return InsideSecureHardware.CANT_TELL;
        KeyFactory factory = KeyFactory.getInstance(privateKey.getAlgorithm(), "AndroidKeyStore");
        KeyInfo keyInfo = factory.getKeySpec(privateKey, KeyInfo.class);
        return keyInfo.isInsideSecureHardware() ? InsideSecureHardware.YES : InsideSecureHardware.NO;
    }

    public static @NonNull InsideSecureHardware isInsideSecurityHardware(String androidKeyStoreKeyAlias)
            throws NoSuchProviderException, NoSuchAlgorithmException, InvalidKeySpecException,
            CertificateException, IOException, UnrecoverableKeyException, KeyStoreException {
        PrivateKey privateKey = (PrivateKey) getAndroidKeyStore().getKey(androidKeyStoreKeyAlias, null);
        return isInsideSecurityHardware(privateKey);
    }

    public static InsideSecureHardware areAllInsideSecurityHardware(String androidKeyStoreKeyAliasPrefix)
            throws NoSuchProviderException, NoSuchAlgorithmException, InvalidKeySpecException,
            CertificateException, IOException, UnrecoverableKeyException, KeyStoreException {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M)
            return InsideSecureHardware.CANT_TELL;
        KeyStore androidKeystore = getAndroidKeyStore();
        for (String alias : Collections.list(androidKeystore.aliases())) {
            if (alias.startsWith(androidKeyStoreKeyAliasPrefix)) {
                if (isInsideSecurityHardware(alias) == InsideSecureHardware.NO)
                    return InsideSecureHardware.NO;
            }
        }
        return InsideSecureHardware.YES;
    }

    public static void putKeyPairIntoAndroidKeyStore(KeyPair keyPair, String alias)
            throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException,
            OperatorCreationException {
        KeyStore androidKeyStore = getAndroidKeyStore();
        androidKeyStore.setKeyEntry(alias, keyPair.getPrivate(), null, new Certificate[]{
                generateCertificate(keyPair)
        });
    }

    public static void deleteAndroidKeyStoreEntry(String alias)
            throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        KeyStore androidKeyStore = getAndroidKeyStore();
        androidKeyStore.deleteEntry(alias);
    }

    // the store can also have certificate entries but we are not interested in those
    public static void putKeyEntriesIntoAndroidKeyStoreWithPrefix(
            KeyStore keyStore, String password, String aliasPrefix)
            throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException,
            UnrecoverableKeyException {
        KeyStore androidKeyStore = getAndroidKeyStore();
        for (String alias : Collections.list(keyStore.aliases())) {
            if (keyStore.isKeyEntry(alias)) {
                Key key = keyStore.getKey(alias, password.toCharArray());
                Certificate[] certs = keyStore.getCertificateChain(alias);
                androidKeyStore.setKeyEntry(aliasPrefix + alias, key, new char[0], certs);
            }
        }
    }

    public static void deleteAndroidKeyStoreEntriesWithPrefix(String aliasPrefix)
            throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        KeyStore androidKeyStore = getAndroidKeyStore();
        for (String alias : Collections.list(androidKeyStore.aliases())) {
            if (alias.startsWith(aliasPrefix)) androidKeyStore.deleteEntry(alias);
        }
    }
}
