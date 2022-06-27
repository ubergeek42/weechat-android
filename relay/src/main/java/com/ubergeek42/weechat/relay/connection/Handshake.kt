package com.ubergeek42.weechat.relay.connection

import com.ubergeek42.weechat.relay.RelayMessage
import com.ubergeek42.weechat.relay.protocol.Hashtable
import com.ubergeek42.weechat.relay.protocol.Info
import com.ubergeek42.weechat.fromHexStringToByteArray
import com.ubergeek42.weechat.toHexStringLowercase
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.random.Random


private const val HANDSHAKE_MESSAGE_ID = "handshake"
private const val VERSION_MESSAGE_ID = "version"


private val logger = LoggerFactory.getLogger("Handshake")


@Suppress("unused")
enum class HandshakeMethod(val string: String) {
    Compatibility("compatibility"),
    ModernFast("modern_fast_only"),
    ModernFastAndSlow("modern_fast_and_slow");

    companion object {
        @JvmStatic fun fromString(string: String) = HandshakeMethod::string.find(string)
                ?: throw IllegalArgumentException("Bad handshake method: '$string'")
    }
}


enum class Authenticated { Yes, NotYet }


interface Handshake {
    val connection: RelayConnection

    fun start()
    fun onMessage(message: RelayMessage): Authenticated

    fun checkForVersionResponse(message: RelayMessage): Authenticated {
        return if (VERSION_MESSAGE_ID == message.id) {
            weechatVersion = message.asVersionResponse().version
            logger.info("WeeChat version: {}", String.format("0x%x", weechatVersion))
            Authenticated.Yes
        } else {
            Authenticated.NotYet
        }
    }

    companion object {
        @JvmStatic var weechatVersion = 0L
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////

class CompatibilityHandshake(
    override val connection: RelayConnection,
    private val password: String
): Handshake {
    override fun start() {
        connection.sendMessage("init password=${password.withCommasEscaped},compression=zlib\n" +
                               "($VERSION_MESSAGE_ID) info version_number\n")
    }

    override fun onMessage(message: RelayMessage) = checkForVersionResponse(message)
}


class ModernHandshake(
    override val connection: RelayConnection,
    private val password: String,
    private val onlyFastHashingAlgorithms: Boolean
): Handshake {
    override fun start() {
        val algorithms = Algorithm.values()
                .filter { if (onlyFastHashingAlgorithms) it.fast else true }
                .filter { if (password.isEmpty()) it.canHandleEmptyPassword else true }
                .joinToString(":") { it.string }
        connection.sendMessage("($HANDSHAKE_MESSAGE_ID) handshake " +
                "password_hash_algo=$algorithms,compression=zlib")
    }

    override fun onMessage(message: RelayMessage): Authenticated {
        if (HANDSHAKE_MESSAGE_ID == message.id) {
            val (algorithm, iterations, totp, serverNonce) = message.asHandshakeResponse()

            if (totp == Totp.On) throw IllegalArgumentException("TOTP not supported")

            val passwordOptionPair = when (algorithm) {
                Algorithm.Plain -> {
                    "password=${password.withCommasEscaped}"
                }
                Algorithm.Sha256, Algorithm.Sha512 -> {
                    val (salt, hash) = hashSha(algorithm.shaSize, serverNonce, password)
                    "password_hash=${algorithm.string}:${salt.toHexStringLowercase()}:${hash.toHexStringLowercase()}"
                }
                Algorithm.Pbkdf2Sha256, Algorithm.Pbkdf2Sha512 -> {
                    val (salt, hash) = hashPbkdf2(algorithm.shaSize, serverNonce, iterations, password)
                    "password_hash=${algorithm.string}:${salt.toHexStringLowercase()}:${iterations}:${hash.toHexStringLowercase()}"
                }
            }

            connection.sendMessage("init $passwordOptionPair\n" +
                                   "($VERSION_MESSAGE_ID) info version_number\n")
        }
        return checkForVersionResponse(message)
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////

private enum class Algorithm(
    val string: String,
    val shaSize: Int,
    val fast: Boolean,
    val canHandleEmptyPassword: Boolean,
) {
    Plain("plain", 0, true, true),
    Sha256("sha256", 256, true, true),
    Sha512("sha512", 512, true, true),
    Pbkdf2Sha256("pbkdf2+sha256", 256, false, false),
    Pbkdf2Sha512("pbkdf2+sha512", 512, false, false);

    companion object {
        fun fromString(string: String) = Algorithm::string.find(string)
                ?: throw IllegalArgumentException("Unsupported password hash algorithm: '$string'")
    }
}


@Suppress("unused")
private enum class Totp(val string: String) {
    On("on"),
    Off("off");

    companion object {
        fun fromString(string: String) = Totp::string.find(string)
                ?: throw IllegalArgumentException("Bad TOTP value: '$string'")
    }
}


@Suppress("unused")
private enum class Compression(val string: String) {
    Off("off"),
    Zlib("zlib");

    companion object {
        fun fromString(string: String) = Compression::string.find(string)
                ?: throw IllegalArgumentException("Bad compression method: '$string'")
    }
}


@Suppress("ArrayInDataClass")
private data class HandshakeResponse(
    val passwordHashAlgorithm: Algorithm,
    val passwordHashIterations: Int,
    val totp: Totp,
    val serverNonce: ByteArray,
    val compression: Compression,
)

private fun RelayMessage.asHandshakeResponse(): HandshakeResponse {
    val o = objects[0] as Hashtable

    return HandshakeResponse(
        passwordHashAlgorithm = o.get("password_hash_algo").asString().run(Algorithm::fromString),
        passwordHashIterations = o.get("password_hash_iterations").asString().toInt(),
        totp = o.get("totp").asString().run(Totp::fromString),
        serverNonce = o.get("nonce").asString().fromHexStringToByteArray(),
        compression = o.get("compression").asString().run(Compression::fromString)
    )
}


private data class VersionResponse(val version: Long)

private fun RelayMessage.asVersionResponse() = VersionResponse((objects[0] as Info).value.toLong())

////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////

@Suppress("ArrayInDataClass")
data class HashResult(val salt: ByteArray, val hash: ByteArray)

private fun hashSha(shaSize: Int, serverNonce: ByteArray, password: String): HashResult {
    val salt = serverNonce + generateClientNonce()
    val hash = MessageDigest.getInstance("SHA-$shaSize").digest(salt + password.encodeToByteArray())
    return HashResult(salt, hash)
}

private fun hashPbkdf2(shaSize: Int, serverNonce: ByteArray, iterations: Int, password: String): HashResult {
    val salt = serverNonce + generateClientNonce()
    val spec = PBEKeySpec(password.toCharArray(), salt, iterations, shaSize)
    val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA$shaSize")
    val hash = secretKeyFactory.generateSecret(spec).encoded
    return HashResult(salt, hash)
}

fun generateClientNonce(length: Int = 16) = Random.nextBytes(length)

////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////

private inline val String.withCommasEscaped: String get() = replace(",", "\\,")

inline fun <reified T : Enum<T>, V> ((T) -> V).find(value: V): T? {
    return enumValues<T>().firstOrNull { this(it) == value }
}
