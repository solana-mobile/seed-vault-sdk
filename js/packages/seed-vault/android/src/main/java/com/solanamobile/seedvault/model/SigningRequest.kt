package com.solanamobile.seedvault.model

import android.net.Uri
import com.solanamobile.seedvault.SigningRequest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class SigningRequestSurrogate(
    val payload: ByteArray,
    val requestedSignatures: List<@Serializable(with = UriAsStringSerializer::class) Uri>
)

object SigningRequestSerializer : KSerializer<SigningRequest> {
    override val descriptor: SerialDescriptor = SigningRequestSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: SigningRequest) {
        encoder.encodeSerializableValue(SigningRequestSurrogate.serializer(),
            SigningRequestSurrogate(value.payload, value.requestedSignatures)
        )
    }

    override fun deserialize(decoder: Decoder): SigningRequest =
        decoder.decodeSerializableValue(SigningRequestSurrogate.serializer()).let {
            SigningRequest(it.payload, ArrayList(it.requestedSignatures))
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