package com.ubergeek42.weechat.relay.protocol

import com.ubergeek42.weechat.Buffer
import com.ubergeek42.weechat.BufferLine
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal

object BigDecimalSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientString", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("This serializer only supports JSON format")

        val element = jsonDecoder.decodeJsonElement().jsonPrimitive.content

        return BigDecimal(element).toLong()
    }

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeLong(value)
    }
}

@Serializable(with = PayloadSerializer::class)
sealed class ApiData {
    abstract val requestId: String?;
    abstract val eventName: String?;

    fun requestIdOrEventName(): String? {
        return if (requestId != null) requestId else "_$eventName"
    }

    abstract fun toRelayObject(): RelayObject;
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class VersionPayload(
    @SerialName("body_type")
    val bodyType: String,
    val body: VersionBody
) : ApiData() {
    @SerialName("request_id")
    override val requestId: String? = null; // FIXME: why not in superclass?

    @SerialName("event_name")
    override val eventName: String? = null; // FIXME: why not in superclass?


    override fun toRelayObject(): RelayObject {
        return Info("version_number", body.versionNumber.toString())
    }
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class VersionBody(@SerialName("weechat_version_number") val versionNumber: Long)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class BuffersPayload(
    @SerialName("body_type")
    val bodyType: String? = null,
    val body: List<BufferBody>
) : ApiData() {
    @SerialName("request_id")
    override val requestId: String? = null; // FIXME: why not in superclass?

    @SerialName("event_name")
    override val eventName: String? = null; // FIXME: why not in superclass?

    override fun toRelayObject(): ApiRelayObject {
        return ApiRelayObject(body.map { buffer -> Buffer(buffer) })
    }
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class BufferBody(
    @Serializable(with = BigDecimalSerializer::class)
    val id: Long,
    val name: String,
    @SerialName("short_name")
    val shortName: String,
    val number: Int,
    val hidden: Boolean,
    val title: String,
    @SerialName("last_read_line_id")
    val lastReadLineId: Long
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class LinesPayload(
    @SerialName("body_type")
    val bodyType: String,
    val body: List<LineBody>,
    val request: String
) : ApiData() {
    @SerialName("request_id")
    override val requestId: String? = null; // FIXME: why not in superclass?

    @SerialName("event_name")
    override val eventName: String? = null; // FIXME: why not in superclass?

    override fun toRelayObject(): ApiRelayObject {
        val bufferId = """/(\d+)/lines""".toRegex().find(request)?.groups[1]?.value
        if (bufferId == null) {
            return ApiRelayObject( emptyList<LineBody>())
        }
        return ApiRelayObject(body.map { line -> BufferLine(line, bufferId.toLong()) })
    }
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class LinePayload(
    @SerialName("body_type")
    val bodyType: String,
    val body: LineBody,
    @SerialName("buffer_id")
    @Serializable(with = BigDecimalSerializer::class)
    val bufferId: Long
) : ApiData() {
    @SerialName("request_id")
    override val requestId: String? = null; // FIXME: why not in superclass?

    @SerialName("event_name")
    override val eventName: String? = null; // FIXME: why not in superclass?

    override fun toRelayObject(): ApiRelayObject {
        return ApiRelayObject(BufferLine(body, bufferId))
    }
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class LineBody(
    @Serializable(with = BigDecimalSerializer::class)
    val id: Long,
    val date: String,
    val displayed: Boolean,
    val highlight: Boolean,
    @SerialName("notify_level")
    val notifyLevel: Int,
    val prefix: String,
    val message: String,
    val tags: List<String>
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class Noop(val code: Int) : ApiData() {
    override val requestId: String? = "noop"; // FIXME: why not in superclass?

    override val eventName: String? = null; // FIXME: why not in superclass?

    override fun toRelayObject(): ApiRelayObject {
        return ApiRelayObject(null)
    }
}

object PayloadSerializer : JsonContentPolymorphicSerializer<ApiData>(ApiData::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ApiData> {
        val type = element.jsonObject["body_type"]?.jsonPrimitive?.content
        return when (type) {
            "version" -> VersionPayload.serializer()
            "buffers" -> BuffersPayload.serializer()
            "lines" -> LinesPayload.serializer()
            "line" -> LinePayload.serializer()
            else -> Noop.serializer() // FIXME: don't fall through for /api/sync?
            //else -> throw SerializationException("Unknown type: $type")
        }
    }
}
