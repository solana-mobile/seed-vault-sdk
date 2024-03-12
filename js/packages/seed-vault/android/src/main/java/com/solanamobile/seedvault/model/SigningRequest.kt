package com.solanamobile.seedvault.model

import android.net.Uri
import android.util.Base64
import com.solanamobile.seedvault.SigningRequest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class SigningRequestSurrogate(
    val payload: String,
    val requestedSignatures: List<@Serializable(with = UriAsStringSerializer::class) Uri>
)

object SigningRequestSerializer : KSerializer<SigningRequest> {
    override val descriptor: SerialDescriptor = SigningRequestSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: SigningRequest) {
        encoder.encodeSerializableValue(SigningRequestSurrogate.serializer(),
            SigningRequestSurrogate(Base64.encodeToString(value.payload, Base64.NO_WRAP), value.requestedSignatures)
        )
    }

    override fun deserialize(decoder: Decoder): SigningRequest =
        decoder.decodeSerializableValue(SigningRequestSurrogate.serializer()).let {
            val payload = Base64.decode(it.payload, Base64.DEFAULT)
            SigningRequest(payload, ArrayList(it.requestedSignatures))
        }
}

object UriAsStringSerializer : KSerializer<Uri> {
    override val descriptor: SerialDescriptor
        get() = String.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Uri) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Uri =
        Uri.parse(decoder.decodeString())
}