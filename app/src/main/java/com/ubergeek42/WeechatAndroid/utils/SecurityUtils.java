package com.ubergeek42.WeechatAndroid.utils;

import android.os.Build;
import android.security.keystore.KeyInfo;

import androidx.annotation.RequiresApi;

import com.ubergeek42.weechat.relay.connection.SSHConnection;

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

public class SecurityUtils {
    private interface SignerBuilder {
        BcContentSignerBuilder make(AlgorithmIdentifier sigAlgId, AlgorithmIdentifier digAlgId);
    }

    // answer by Tolga Okur https://stackoverflow.com/a/59182063/1449683
    public static X509Certificate generateSelfSignedCertificate(KeyPair keyPair)
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
                signingAlgorithm = "SHA256withECDSA";
                signerBuilder = BcECContentSignerBuilder::new;
                break;
            case "DSA":
                signingAlgorithm = "SHA256withDSA";
                signerBuilder = BcDSAContentSignerBuilder::new;
                break;
            default:
                throw new RuntimeException("Can't make a certificate for a key algorithm " + keyAlgorithm);
        }
        System.out.println("$$ using: " + signingAlgorithm);
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

    public static KeyStore getAndroidKeyStore() throws CertificateException,
            NoSuchAlgorithmException, IOException, KeyStoreException {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        return ks;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static boolean isInsideSecurityHardware(PrivateKey privateKey) throws NoSuchProviderException,
            NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory factory = KeyFactory.getInstance(privateKey.getAlgorithm(), "AndroidKeyStore");
        KeyInfo keyInfo = factory.getKeySpec(privateKey, KeyInfo.class);
        return keyInfo.isInsideSecureHardware();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static boolean isInsideSecurityHardware(String androidKeyStoreKeyAlias) throws
            NoSuchProviderException, NoSuchAlgorithmException, InvalidKeySpecException,
            CertificateException, IOException, UnrecoverableKeyException, KeyStoreException {
        PrivateKey privateKey = (PrivateKey) getAndroidKeyStore().getKey(androidKeyStoreKeyAlias, null);
        return isInsideSecurityHardware(privateKey);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static boolean areAllInsideSecurityHardware(String androidKeyStoreKeyAliasPrefix) throws
            NoSuchProviderException, NoSuchAlgorithmException, InvalidKeySpecException,
            CertificateException, IOException, UnrecoverableKeyException, KeyStoreException {
        KeyStore androidKeystore = getAndroidKeyStore();
        for (String alias : Collections.list(androidKeystore.aliases())) {
            if (alias.startsWith(androidKeyStoreKeyAliasPrefix)) {
                if (!isInsideSecurityHardware(alias)) return false;
            }
        }
        return true;
    }

    public static void putKeyPairIntoAndroidKeyStore(KeyPair keyPair, String alias)
            throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException,
            OperatorCreationException {
        KeyStore androidKeyStore = getAndroidKeyStore();
        androidKeyStore.setKeyEntry(alias, keyPair.getPrivate(), null, new Certificate[]{
                generateSelfSignedCertificate(keyPair)
        });
    }

}
