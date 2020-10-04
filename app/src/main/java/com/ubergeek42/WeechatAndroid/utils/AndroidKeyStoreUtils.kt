@file:Suppress("MoveVariableDeclarationIntoWhen")

package com.ubergeek42.WeechatAndroid.utils

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
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
import java.security.KeyPair
import java.security.PrivateKey
import kotlin.jvm.Throws


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
    val pemObject = PEMParser(keyReader).readObject()

    return when (pemObject) {
        // encrypted pkcs8 file, header: -----BEGIN ENCRYPTED PRIVATE KEY-----
        //   $ ssh-keygen -t ecdsa -m pem -N "password" -f ecdsa.pkcs8.password=password
        // upon decrypting it, we only get a private key. to obtain a key pair, we do something
        // a bit silly but simple: we convert the decrypted private key back to PEM and run
        // this method on the resulting data
        // see this answer by Dave Thompson https://stackoverflow.com/a/57069951/1449683
        is PKCS8EncryptedPrivateKeyInfo -> {
            val decryptionProv = pkcs8DecryptorProvider.build(passphrase)
            val privateKeyInfo = pemObject.decryptPrivateKeyInfo(decryptionProv)
            val privateKey = pkcs8pemKeyConverter.getPrivateKey(privateKeyInfo)
            return makeKeyPair(privateKey.toPem().toReader(), passphrase)
        }

        // unencrypted pkcs8 file, header: -----BEGIN PRIVATE KEY-----
        //   $ ssh-keygen -t rsa -m pem -N "" -f ecdsa.pkcs8
        is PrivateKeyInfo -> {
            val privateKey = pkcs8pemKeyConverter.getPrivateKey(pemObject)
            return makeKeyPair(privateKey.toPem().toReader(), passphrase)
        }

        // encrypted pkcs1 file, header like: -----BEGIN RSA PRIVATE KEY-----
        //                                    Proc-Type: 4,ENCRYPTED
        //                                    DEK-Info: AES-256-CBC,3B162E06B12794EA105855E7942D5A1A
        //   $ ssh-keygen -t ecdsa -m pem -N "password" -f ecdsa.pem.password=password
        //   $ openssl genrsa -aes256 -out openssl.rsa.aes256.password=password -passout pass:password 4096
        is PEMEncryptedKeyPair -> {
            val decryptorProvider = pemDecryptorProviderBuilder.build(passphrase)
            pkcs1pemKeyConverter.getKeyPair(pemObject.decryptKeyPair(decryptorProvider))
        }

        // unencrypted pkcs1 file, header like: -----BEGIN RSA PRIVATE KEY-----
        //   $ ssh-keygen -t rsa -m pem -f pem.rsa
        is PEMKeyPair -> {
            pkcs1pemKeyConverter.getKeyPair(pemObject)
        }

        else -> {
            throw IllegalArgumentException("Don't know how to decode " +
                    pemObject.javaClass.simpleName)
        }
    }
}


@Throws(Exception::class)
private fun PrivateKey.toPem(): String {
    val privateKeyPemObject = JcaMiscPEMGenerator(this, null).generate()
    val stringWriter = StringWriter()
    PemWriter(stringWriter).use { it.writeObject(privateKeyPemObject) }
    return stringWriter.toString()
}


fun ByteArray.toReader() = InputStreamReader(ByteArrayInputStream(this))
fun String.toReader() = StringReader(this)