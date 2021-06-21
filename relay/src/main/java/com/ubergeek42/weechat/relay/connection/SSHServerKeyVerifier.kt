package com.ubergeek42.weechat.relay.connection

import com.trilead.ssh2.ServerHostKeyVerifier
import com.trilead.ssh2.crypto.Base64
import com.trilead.ssh2.signature.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.MessageDigest


// note that this verifier doesn't check for DNS spoofing. while useful, DNS spoofing warnings seem
// to always be accompanied with verification failure. this produces a big warning either way.
// it is possible to have a situation where IP key becomes different from the host key, and the
// latter is verifiable; ssh in this case produces a *small* warning:
//
//   Warning: the ECDSA host key for '...' differs from the key for the IP address '...'
//   Offending key for IP in /home/user/.ssh/known_hosts:3
//   Matching host key in /home/user/.ssh/known_hosts:2
//   Are you sure you want to continue connecting (yes/no)?
//
// in this case our verification will silently succeed. it's hard to imagine a situation when this
// would be a vector of an attack. this might be revisited in the future


// these are host key verification algorithms in the order of our preference
// each string represents a combination of key type (see KeyType)
// and a hashing algorithm (SHA1, SHA2-256, SHA2-384 or SHA2-512)

// the preferred order differs slightly from the one used by OpenSSH. we prefer Ed25519 because it
// is supposed to be the best one, and we don't have any compatibility considerations here as we
// might have were the keys exportable; we prefer nistp256 curves over other ECDSA curves as these
// good enough, and also easier on the server (Raspberry PI 3 takes 0.0004s to sign a hash using
// nistp256 vs 0.0181s for nistp384 and whopping 0.0421s for nistp251); and SHA-512 used for RSA
// is not significantly slower than SHA-256. see https://crypto.stackexchange.com/q/84271/83908
enum class HostKeyAlgorithms(val string: String) {
    Ed25519Sha512(Ed25519Verify.ED25519_ID),
    EcdsaNistp256Sha256(ECDSASHA2Verify.ECDSA_SHA2_PREFIX + "nistp256"),
    EcdsaNistp384Sha384(ECDSASHA2Verify.ECDSA_SHA2_PREFIX + "nistp384"),
    EcdsaNistp521Sha512(ECDSASHA2Verify.ECDSA_SHA2_PREFIX + "nistp521"),    // note that 521 is not a typo
    RsaSha512(RSASHA512Verify.ID_RSA_SHA_2_512),
    RsaSha256(RSASHA256Verify.ID_RSA_SHA_2_256),
    RsaSha1(RSASHA1Verify.ID_SSH_RSA),
    DsaSha1(DSASHA1Verify.ID_SSH_DSS);

    companion object {
        val preferred = values().map { it.string }
    }
}


// these are the distinct key types that can be used to verify the hostname. although rfc4253
// specifies that a host “may” have multiple host keys of the same or different types, it appears
// that using keys of the same type (e.g. 2 RSA keys, or 2 ECDSA nistp256 keys) you can only connect
// having the the first one, while if the server uses e.g. ECDSA nist256 and nist521 keys, you can
// connect having either one. for this reason, we consider the three ECDSA keys different keys.

// accompanying are the display name, as well as the supported host key verification algorithms
@Suppress("unused")
enum class KeyType(val displayName: String, vararg algorithms: String) {
    Ed25519("Ed25519", HostKeyAlgorithms.Ed25519Sha512.string),
    EcdsaNistp256("ECDSA", HostKeyAlgorithms.EcdsaNistp256Sha256.string),
    EcdsaNistp384("ECDSA", HostKeyAlgorithms.EcdsaNistp384Sha384.string),
    EcdsaNistp521("ECDSA", HostKeyAlgorithms.EcdsaNistp521Sha512.string),
    Rsa("RSA", HostKeyAlgorithms.RsaSha512.string,
               HostKeyAlgorithms.RsaSha256.string,
               HostKeyAlgorithms.RsaSha1.string),
    Dsa("DSA", HostKeyAlgorithms.DsaSha1.string);

    val algorithms = algorithms.toList()

    companion object {
        fun fromHostKeyAlgorithm(algorithm: String): KeyType? {
            values().forEach { if (algorithm in it.algorithms) return it }
            return null
        }
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////


@Serializable data class Server(
    val host: String,
    val port: Int
) {
    override fun toString() = if (port == 22) host else "$host:$port"

    companion object {
        fun fromHostAndPort(host: String, port: Int) = Server(host, port)
    }
}


// key type here is null if it can't be determined
// key is stored as a base64-encoded string as data classes don't do byte arrays out of the box yet
// incidentally, this gets us the same strings as are used in known_hosts files
@Serializable data class Identity(
    val keyType: KeyType?,
    val base64key: String
) {
    fun matches(other: Identity) = this.base64key == other.base64key

    val sha256keyFingerprint get() = base64key.fromBase64.toSha256fingerprint

    companion object {
        fun fromAlgorithmAndKey(algorithm: String, key: ByteArray) =
                Identity(KeyType.fromHostKeyAlgorithm(algorithm), key.toBase64)
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////


@Serializable class SSHServerKeyVerifier : ServerHostKeyVerifier {
    fun interface Listener {
        fun onChange()
    }

    @Transient var listener: Listener? = null

    private val knownHosts = mutableMapOf<Server, MutableSet<Identity>>()

    @Throws(VerifyException::class)
    override fun verifyServerHostKey(host: String, port: Int, algorithm: String, key: ByteArray): Boolean {
        val server = Server.fromHostAndPort(host, port)
        val identity = Identity.fromAlgorithmAndKey(algorithm, key)

        val knownIdentities = knownHosts[server]

        if (knownIdentities.isNullOrEmpty())
            throw ServerNotKnownException(server, identity)

        if (!knownIdentities.any { it.matches(identity) })
            throw ServerNotVerifiedException(server, identity)

        return true
    }

    // returns *all* supported algorithms, in the default preferred order, except that the ones
    // that are valid for the given server come first.

    // we don't send only those algorithms that are valid for the current server in order to make
    // sure that verifyServerHostKey is called in the case of key type mismatch. e.g. if we only
    // offer to verify RSA keys, and the server provides only EC keys, it means that the server
    // key has changed, and in this case we want to raise ServerNotVerifiedException. but as these
    // keys are incompatible, the library will simply close the connection with IOException,
    // and will never call verifyServerHostKey.
    fun getPreferredServerHostKeyAlgorithmsForServer(host: String, port: Int): Array<String> {
        val result = HostKeyAlgorithms.preferred.toMutableList()

        knownHosts[Server.fromHostAndPort(host, port)]?.let { identities ->
            val serverAlgorithms = identities.mapNotNull { it.keyType?.algorithms }.flatten()
            HostKeyAlgorithms.preferred.reversed().forEach {
                if (it in serverAlgorithms) result.moveToFront(it)
            }
        }

        return result.toTypedArray()
    }

    fun addServerHostKey(server: Server, identity: Identity) {
        knownHosts.getOrPut(server, ::mutableSetOf).add(identity)
        listener?.onChange()
    }

    fun clear() {
        knownHosts.clear()
        listener?.onChange()
    }

    val numberOfRecords get() = knownHosts.values.sumOf { it.size }

    fun encodeToString() = json.encodeToString(this)

    companion object {
        @JvmStatic @Throws(IllegalArgumentException::class)     // actually SerializationException
        fun decodeFromString(string: String) = json.decodeFromString<SSHServerKeyVerifier>(string)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    open class VerifyException(val server: Server, val identity: Identity) : IOException()

    class ServerNotKnownException(server: Server, identity: Identity) : VerifyException(server, identity) {
        override val message get() = "Server at $server is not known"
    }

    class ServerNotVerifiedException(server: Server, identity: Identity) : VerifyException(server, identity) {
        override val message get() = "Server at $server is known, but could not be verified. " +
                "${identity.keyType} key SHA256 fingerprint: ${identity.sha256keyFingerprint}"
    }

    // these are methods from ExtendedServerHostKeyVerifier
    // at the time being these mostly aren't being used
    // see https://github.com/kruton/sshlib/commits/hostkeys-prove
    //
    // the result of this method is filtering the wanted algorithms—either the library default ones
    // or the ones produced by getPreferredServerHostKeyAlgorithmsForServer. this leads to the same
    // problem that's mentioned in the comment above, so return null for no-op
    //override fun getKnownKeyAlgorithmsForHost(host: String, port: Int): List<String>? {
    //    return null
    //}
    //
    //override fun removeServerHostKey(host: String, port: Int, algorithm: String, key: ByteArray) {
    //    val server = Server.fromHostAndPort(host, port)
    //    val identity = Identity.fromKey(KeyType.fromHostKeyAlgorithm(algorithm), key)
    //    knownHosts[server]?.remove(identity)
    //    listener?.onChange()
    //}
    //
    //override fun addServerHostKey(host: String, port: Int, algorithm: String, key: ByteArray) {
    //    val server = Server.fromHostAndPort(host, port)
    //    val identity = Identity.fromKey(KeyType.fromHostKeyAlgorithm(algorithm), key)
    //    knownHosts.getOrPut(server, ::mutableSetOf).add(identity)
    //    listener?.onChange()
    //}
}


////////////////////////////////////////////////////////////////////////////////////////////////////

private val sha256digest = MessageDigest.getInstance("SHA256")

private val ByteArray.toBase64 get() = String(Base64.encode(this))
private val String.fromBase64 get() = Base64.decode(this.toCharArray())

private val ByteArray.toSha256 get() = sha256digest.digest(this).toBase64
private val ByteArray.toSha256fingerprint get() = this.toSha256.trimEnd('=')

private fun <T> MutableList<T>.moveToFront(t: T) {
    this.remove(t)
    this.add(0, t)
}

// allows putting Servers into Map
private val json = Json { allowStructuredMapKeys = true }