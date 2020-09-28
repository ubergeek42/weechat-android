package com.ubergeek42.weechat.relay.connection

import com.trilead.ssh2.ServerHostKeyVerifier
import com.trilead.ssh2.crypto.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

@Serializable
data class Server(val host: String, val port: Int) {
    val simpleString get() = if (port == 22) host else "$host:$port"
}

@Serializable
data class Identity(val algorithm: String, val key: HashableByteArray) {
    val fingerprint get() = try {
                                    makeSha256Fingerprint(key.bytes)
                                } catch (e: Exception) {
                                    "n/a"
                                }

    fun matches(another: Identity) = this.key == another.key
}

////////////////////////////////////////////////////////////////////////////////////////////////

@Serializable
class SshMutableServerVerifier : ServerHostKeyVerifier {
    private val knownHosts = mutableMapOf<Server, Set<Identity>>()

    @Throws(VerifyException::class)
    override fun verifyServerHostKey(host: String, port: Int,
                                     serverHostKeyAlgorithm: String,
                                     serverHostKey: ByteArray): Boolean {
        val server = Server(host, port)
        val identity = Identity(serverHostKeyAlgorithm, HashableByteArray(serverHostKey))

        val knownIdentities = knownHosts[server] ?:
            throw ServerNotKnownException(server, identity)

        if (!knownIdentities.any { it.matches(identity) })
            throw ServerNotVerifiedException(server, identity)

        return true
    }

    fun encodeToString() = Json.encodeToString(this)

    companion object {
        @Throws(SerializationException::class)
        fun decodeFromString(string: String) = Json.decodeFromString<SshMutableServerVerifier>(string)
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////

open class VerifyException(val server: Server, val identity: Identity) : IOException()

class ServerNotKnownException(server: Server, identity: Identity) : VerifyException(server, identity) {
    override val message get() = "Server ${server.simpleString} is not present in known hosts"
}

class ServerNotVerifiedException(server: Server, identity: Identity) : VerifyException(server, identity) {
    override val message get() = "Server ${server.simpleString} is known, but could not be verified. " +
            "Chosen algorithm: ${identity.algorithm}; " +
            "SHA256 host key fingerprint: ${identity.fingerprint}"
}

@Throws(NoSuchAlgorithmException::class)
private fun makeSha256Fingerprint(bytes: ByteArray?): String {
    val sha256 = MessageDigest.getInstance("SHA256").digest(bytes)
    return String(Base64.encode(sha256)).trimEnd('=')
}

@Serializable
data class HashableByteArray(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HashableByteArray

        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }
}