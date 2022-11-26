@file:Suppress("MoveVariableDeclarationIntoWhen")

package com.ubergeek42.WeechatAndroid.utils

import com.trilead.ssh2.crypto.keys.Ed25519PrivateKey
import com.trilead.ssh2.crypto.keys.Ed25519PublicKey
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.jcajce.interfaces.EdDSAKey
import org.bouncycastle.jcajce.provider.asymmetric.edec.BCEdDSAPrivateKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMEncryptedKeyPair
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaMiscPEMGenerator
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo
import org.bouncycastle.util.io.pem.PemWriter
import java.io.*
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.*


private val bouncyCastleProvider = BouncyCastleProvider()
private val pkcs8DecryptorProvider = JceOpenSSLPKCS8DecryptorProviderBuilder()
        .setProvider(bouncyCastleProvider)
private val pemDecryptorProviderBuilder = JcePEMDecryptorProviderBuilder()
        .setProvider(bouncyCastleProvider)

private val pkcs8pemKeyConverter = JcaPEMKeyConverter().setProvider(bouncyCastleProvider)

// do NOT use BC for converting pkcs1 as for ECDSA it creates keys with algorithm "ECDSA"
// instead of "EC", and trying to put the former inside AndroidKeyStore results in
// exception “Unsupported key algorithm: ECDSA”
private val pkcs1pemKeyConverter = JcaPEMKeyConverter()


@Throws(Exception::class)   // this method throws just too many exceptions
fun makeKeyPair(keyReader: Reader, passphrase: CharArray): KeyPair {
    var obj = PEMParser(keyReader).readObject()

    // encrypted pkcs8 file, header: -----BEGIN ENCRYPTED PRIVATE KEY-----
    //   $ openssl genpkey -aes256 -pass pass:password -algorithm EC -out ecdsa.aes256.pkcs8 \
    //     -pkeyopt ec_paramgen_curve:P-384
    //   $ pkcs8 -topk8 -v2 des3 -passout pass:password -in rsa.pem -out rsa.des3.pkcs8
    if (obj is PKCS8EncryptedPrivateKeyInfo) {
        val decryptorProvider = pkcs8DecryptorProvider.build(passphrase)
        obj = /* PrivateKeyInfo */ obj.decryptPrivateKeyInfo(decryptorProvider)
    }

    // unencrypted pkcs8 file, header: -----BEGIN PRIVATE KEY-----
    //   $ openssl genpkey -algorithm RSA -out rsa.pkcs8 -pkeyopt rsa_keygen_bits:3072
    //   $ openssl genpkey -algorithm Ed25519 -out ed25519.pkcs8
    //   $ pkcs8 -topk8 -in rsa.pem -out rsa.pkcs8
    if (obj is PrivateKeyInfo) {
        val privateKey = pkcs8pemKeyConverter.getPrivateKey(obj)

        // Ed25519 can't be converted to PEM, so generate public key directly
        when (privateKey) {
            is BCEdDSAPrivateKey -> return KeyPair(privateKey.publicKey, privateKey)
            is EdDSAKey -> return KeyPair(genEd25519publicKey(privateKey, obj), privateKey)
        }

        // there is no direct way of extracting the public key from the private key.
        // to simplify things, we convert the key back to PEM and read it; this gets us a key pair
        obj = /* PEMKeyPair */ PEMParser(privateKey.toPem().toReader()).readObject()
    }

    // encrypted pkcs1 file, header like: -----BEGIN RSA PRIVATE KEY-----
    //                                    Proc-Type: 4,ENCRYPTED
    //                                    DEK-Info: AES-256-CBC,3B162E06B12794EA105855E7942D5A1A
    //   $ ssh-keygen -t ecdsa -m pem -N "password" -f ecdsa.aes128.pem
    //   $ openssl genrsa -aes256 -passout pass:password 4096 -out rsa.aes256.pem
    if (obj is PEMEncryptedKeyPair) {
        val decryptorProvider = pemDecryptorProviderBuilder.build(passphrase)
        obj = /* PEMKeyPair */ obj.decryptKeyPair(decryptorProvider)
    }

    // unencrypted pkcs1 file, header like: -----BEGIN RSA PRIVATE KEY-----
    //   $ ssh-keygen -t rsa -m pem -f rsa.pem
    //   $ openssl ecparam -name secp521r1 -genkey -noout -out ecdsa.pem
    if (obj is PEMKeyPair) {
        return pkcs1pemKeyConverter.getKeyPair(obj)
    }

    if (obj == null) throw IllegalArgumentException("File does not contain PEM objects")

    throw IllegalArgumentException("Don't know how to decode " + obj.javaClass.simpleName)
}


private fun PrivateKey.toPem(): String {
    val privateKeyPemObject = JcaMiscPEMGenerator(this, null).generate()
    val stringWriter = StringWriter()
    PemWriter(stringWriter).use { it.writeObject(privateKeyPemObject) }
    return stringWriter.toString()
}


private fun genEd25519publicKey(privateKey: EdDSAKey, privateKeyInfo: PrivateKeyInfo): PublicKey {
    val privateKeyRaw = ASN1OctetString.getInstance(privateKeyInfo.parsePrivateKey()).octets
    val privateKeyParameters = Ed25519PrivateKeyParameters(privateKeyRaw)
    val publicKeyParameters = privateKeyParameters.generatePublicKey()
    val spi = SubjectPublicKeyInfo(privateKeyInfo.privateKeyAlgorithm, publicKeyParameters.encoded)
    val factory = KeyFactory.getInstance(privateKey.algorithm, bouncyCastleProvider)
    return factory.generatePublic(X509EncodedKeySpec(spi.encoded))
}


fun KeyPair.edDsaKeyPairToSshLibEd25519KeyPair() = KeyPair(
    Ed25519PublicKey(X509EncodedKeySpec(public.encoded)),
    Ed25519PrivateKey(PKCS8EncodedKeySpec(private.encoded))
)

fun ByteArray.toReader() = InputStreamReader(ByteArrayInputStream(this))
fun String.toReader() = StringReader(this)
