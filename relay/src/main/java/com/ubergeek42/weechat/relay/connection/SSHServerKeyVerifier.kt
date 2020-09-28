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

interface Hashable {
    override fun hashCode(): Int
    override fun equals(other: Any?): Boolean
}

interface Server: Hashable

interface Identity: Hashable {
    fun matches(other: Identity): Boolean
}

@Serializable data class ServerImpl(val hash: String): Server {
    companion object {
        fun fromHostAndPort(host: String, port: Int): Server {
            return ServerImpl("$host:$port")
        }
    }
}

@Serializable data class IdentityImpl(val hash: String): Identity {
    override fun matches(other: Identity): Boolean {
        return this == other
    }

    companion object {
        fun fromKey(bytes: ByteArray): Identity {
            return IdentityImpl(bytes.base64)
        }
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////

@Serializable class SSHServerKeyVerifier : ServerHostKeyVerifier {
    private val knownHosts = mutableMapOf<Server, Set<Identity>>()

    @Throws(VerifyException::class)
    override fun verifyServerHostKey(host: String, port: Int,
                                     serverHostKeyAlgorithm: String,
                                     serverHostKey: ByteArray): Boolean {
        val server = ServerImpl.fromHostAndPort(host, port)
        val identity = IdentityImpl.fromKey(serverHostKey)

        val knownIdentities = knownHosts[server] ?:
            throw ServerNotKnownException(host, port)

        if (!knownIdentities.any { it.matches(identity) })
            throw ServerNotVerifiedException(host, port, serverHostKeyAlgorithm, serverHostKey)

        return true
    }

    fun encodeToString() = Json.encodeToString(this)

    companion object {
        @Throws(SerializationException::class)
        fun decodeFromString(string: String) = Json.decodeFromString<SSHServerKeyVerifier>(string)
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////

open class VerifyException(val host: String, val port: Int) : IOException()

class ServerNotKnownException(host: String, port: Int) : VerifyException(host, port) {
    override val message get() = "Server at $host:$port is not known"
}

class ServerNotVerifiedException(host: String, port: Int, val algorithm: String, val key: ByteArray) : VerifyException(host, port) {
    override val message get() = "Server at $host:$port is known, but could not be verified. " +
            "Chosen algorithm: $algorithm; " +
            "SHA256 host key fingerprint: $fingerprint"

    private val fingerprint get() = key.sha256fingerprint
}

////////////////////////////////////////////////////////////////////////////////////////////////////

private val sha256digest = MessageDigest.getInstance("SHA256")

private val ByteArray.base64 get() = String(Base64.encode(this))

private val ByteArray.sha256 get() = sha256digest.digest(this).base64
private val ByteArray.sha256fingerprint get() = this.sha256.trimEnd('=')
