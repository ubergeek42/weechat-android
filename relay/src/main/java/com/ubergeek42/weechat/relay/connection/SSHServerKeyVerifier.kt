package com.ubergeek42.weechat.relay.connection

import com.trilead.ssh2.ExtendedServerHostKeyVerifier
import com.trilead.ssh2.crypto.Base64
import com.trilead.ssh2.signature.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.MessageDigest


enum class Algorithms(val string: String) {
    Ed25519(Ed25519Verify.ED25519_ID),
    ECDSA256(ECDSASHA2Verify.ECDSA_SHA2_PREFIX + "nistp256"),
    ECDSA384(ECDSASHA2Verify.ECDSA_SHA2_PREFIX + "nistp384"),
    ECDSA521(ECDSASHA2Verify.ECDSA_SHA2_PREFIX + "nistp521"),
    RSA512(RSASHA512Verify.ID_RSA_SHA_2_512),
    RSA256(RSASHA256Verify.ID_RSA_SHA_2_256),
    RSA(RSASHA1Verify.ID_SSH_RSA),
    DSS(DSASHA1Verify.ID_SSH_DSS);

    companion object {
        val preferred = values().map { it.string }
    }
}


@Suppress("unused", "MemberVisibilityCanBePrivate")
enum class Key(vararg preferredAlgorithms: String) {
    DSA(Algorithms.DSS.string),
    RSA(Algorithms.RSA512.string, Algorithms.RSA256.string, Algorithms.RSA.string),
    ECDSA256(Algorithms.ECDSA256.string, Algorithms.ECDSA384.string, Algorithms.ECDSA521.string), // this one is special
    Ed25519(Algorithms.Ed25519.string);

    val preferredAlgorithms = preferredAlgorithms.toList()

    companion object {
        fun fromHostKeyAlgorithm(algorithm: String): Key? {
            values().forEach { if (algorithm in it.preferredAlgorithms) return it }
            return null
        }

        fun getPreferredHostKeyAlgorithms(algorithm: String): List<String>? {
            return when (val key = fromHostKeyAlgorithm(algorithm)) {
                null -> null
                ECDSA256 -> listOf(algorithm)
                else -> key.preferredAlgorithms
            }
        }
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////


interface Hashable {
    override fun hashCode(): Int
    override fun equals(other: Any?): Boolean
}


interface Server: Hashable


interface Identity: Hashable {
    fun matches(other: Identity): Boolean
    fun getAlgorithms(): List<String>?
}


@Serializable data class ServerImpl(val hash: String): Server {
    companion object {
        fun fromHostAndPort(host: String, port: Int) = ServerImpl("$host:$port")
    }
}


@Serializable data class IdentityImpl(val algorithm: String, val hash: String): Identity {
    override fun matches(other: Identity) = if (other is IdentityImpl)
            this.hash == other.hash else false

    override fun getAlgorithms(): List<String>? = Key.getPreferredHostKeyAlgorithms(algorithm)

    companion object {
        fun fromKey(algorithm: String, key: ByteArray) = IdentityImpl(algorithm, key.base64)
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////


@Serializable open class SSHServerKeyVerifier : ExtendedServerHostKeyVerifier() {
    private val knownHosts = mutableMapOf<Server, MutableSet<Identity>>()

    @Throws(VerifyException::class)
    override fun verifyServerHostKey(host: String, port: Int, algorithm: String, key: ByteArray): Boolean {
        val server = ServerImpl.fromHostAndPort(host, port)
        val identity = IdentityImpl.fromKey(algorithm, key)

        val knownIdentities = knownHosts[server] ?:
            throw ServerNotKnownException(host, port, algorithm, key)

        if (!knownIdentities.any { it.matches(identity) })
            throw ServerNotVerifiedException(host, port, algorithm, key)

        return true
    }

    // if this returns a non-empty list, and the server offers key types that we lack,
    // the connection will fail without ever calling `verifyServerHostKey()`.
    // we want it to be called so that we can raise an appropriate exception.
    // therefore we return null here, and instead the below method is used to advertise all
    // algorithms that we are aware of
    override fun getKnownKeyAlgorithmsForHost(host: String, port: Int): List<String>? {
        return null
    }

    // returns *all* supported algorithms, with the ones that are valid for the server first,
    // and otherwise sorted by the the default preferred order
    fun getPreferredKeyAlgorithmsForHostAsWellAllTheOthers(host: String, port: Int): Array<String> {
        val server = ServerImpl.fromHostAndPort(host, port)
        val result = Algorithms.preferred.toMutableList()

        knownHosts[server]?.let { identities ->
            val serverPreferred = identities.mapNotNull { it.getAlgorithms() }.flatten()
            Algorithms.preferred.reversed().forEach {
                if (it in serverPreferred) result.moveToFront(it)
            }
        }

        return result.toTypedArray()
    }

    override fun removeServerHostKey(host: String, port: Int, algorithm: String, key: ByteArray) {
        val server = ServerImpl.fromHostAndPort(host, port)
        val identity = IdentityImpl.fromKey(algorithm, key)
        knownHosts[server]?.remove(identity)
    }

    override fun addServerHostKey(host: String, port: Int, algorithm: String, key: ByteArray) {
        val server = ServerImpl.fromHostAndPort(host, port)
        val identity = IdentityImpl.fromKey(algorithm, key)
        knownHosts.getOrPut(server, ::mutableSetOf)
        knownHosts[server]!!.add(identity)
    }

    fun encodeToString() = Json.encodeToString(this)

    companion object {
        @JvmStatic @Throws(IllegalArgumentException::class)     // actually SerializationException
        fun decodeFromString(string: String) = Json.decodeFromString<SSHServerKeyVerifier>(string)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    open class VerifyException(val host: String, val port: Int,
                               val algorithm: String, val key: ByteArray) : IOException() {
        val fingerprint get() = key.sha256fingerprint
    }

    class ServerNotKnownException(host: String, port: Int, algorithm: String, key: ByteArray)
            : VerifyException(host, port, algorithm, key) {
        override val message get() = "Server at $host:$port is not known"
    }

    class ServerNotVerifiedException(host: String, port: Int, algorithm: String, key: ByteArray)
            : VerifyException(host, port, algorithm, key) {
        override val message get() = "Server at $host:$port is known, but could not be verified. " +
                "Chosen algorithm: $algorithm; " +
                "SHA256 host key fingerprint: $fingerprint"
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////

private val sha256digest = MessageDigest.getInstance("SHA256")

private val ByteArray.base64 get() = String(Base64.encode(this))

private val ByteArray.sha256 get() = sha256digest.digest(this).base64
private val ByteArray.sha256fingerprint get() = this.sha256.trimEnd('=')

private fun <T> MutableList<T>.moveToFront(t: T) {
    this.remove(t)
    this.add(0, t)
}