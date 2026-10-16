package com.meow.music.data.internetroom

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class MessageFormat {
  JSON,
  PROTOBUF
}

class MessageCodec(
  var format: MessageFormat = MessageFormat.JSON,
  var compressionEnabled: Boolean = false
) {
  companion object {
    private const val TAG = "MessageCodec"
    private const val COMPRESSION_THRESHOLD = 100

    fun detectMessageFormat(data: ByteArray): MessageFormat {
      return MessageFormat.JSON
    }
  }

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  fun encode(msgType: String, payload: Any?): ByteArray {
    return encodeJson(msgType, payload)
  }

  fun decode(data: ByteArray): Pair<String, ByteArray> {
    return decodeJson(data)
  }

  private fun encodeJson(msgType: String, payload: Any?): ByteArray {
    val payloadElement = if (payload != null) {
      try {
        json.parseToJsonElement(json.encodeToString(serializer(payload), payload))
      } catch (_: Exception) {
        null
      }
    } else {
      null
    }

    val jsonObject = buildJsonObject {
      put("type", msgType)
      if (payloadElement != null) {
        put("payload", payloadElement)
      }
    }

    var data = jsonObject.toString().toByteArray()

    if (compressionEnabled && data.size > COMPRESSION_THRESHOLD) {
      val compressed = compressData(data)
      if (compressed.size < data.size) {
        data = compressed
      }
    }

    return data
  }

  private fun decodeJson(data: ByteArray): Pair<String, ByteArray> {
    val actualData =
      if (
        compressionEnabled && data.size > 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()
      ) {
        decompressData(data) ?: data
      } else {
        data
      }

    try {
      val jsonObject = json.parseToJsonElement(actualData.decodeToString()).jsonObject
      val type = jsonObject["type"]?.jsonPrimitive?.content ?: ""
      val payloadElement = jsonObject["payload"]
      val payloadBytes = payloadElement?.toString()?.toByteArray() ?: byteArrayOf()
      return Pair(type, payloadBytes)
    } catch (_: Exception) {
      return Pair("", byteArrayOf())
    }
  }

  private fun compressData(data: ByteArray): ByteArray {
    val bos = java.io.ByteArrayOutputStream()
    java.util.zip.GZIPOutputStream(bos).use { it.write(data) }
    return bos.toByteArray()
  }

  private fun decompressData(data: ByteArray): ByteArray? {
    return try {
      val bis = java.io.ByteArrayInputStream(data)
      java.util.zip.GZIPInputStream(bis).use { it.readBytes() }
    } catch (_: Exception) {
      null
    }
  }

  fun decodePayload(msgType: String, payloadBytes: ByteArray, format: MessageFormat): Any? {
    if (payloadBytes.isEmpty()) return null
    return decodeJsonPayload(msgType, payloadBytes)
  }

  private fun decodeJsonPayload(msgType: String, payloadBytes: ByteArray): Any? {
    val payloadString = payloadBytes.decodeToString()

    return try {
      when (msgType) {
        MessageTypes.ROOM_CREATED -> json.decodeFromString<RoomCreatedPayload>(payloadString)
        MessageTypes.JOIN_REQUEST -> json.decodeFromString<JoinRequestPayload>(payloadString)
        MessageTypes.JOIN_APPROVED -> json.decodeFromString<JoinApprovedPayload>(payloadString)
        MessageTypes.JOIN_REJECTED -> json.decodeFromString<JoinRejectedPayload>(payloadString)
        MessageTypes.USER_JOINED -> json.decodeFromString<UserJoinedPayload>(payloadString)
        MessageTypes.USER_LEFT -> json.decodeFromString<UserLeftPayload>(payloadString)
        MessageTypes.SYNC_PLAYBACK, MessageTypes.PLAYBACK_ACTION -> json.decodeFromString<PlaybackActionPayload>(payloadString)
        MessageTypes.BUFFER_WAIT -> json.decodeFromString<BufferWaitPayload>(payloadString)
        MessageTypes.BUFFER_COMPLETE -> json.decodeFromString<BufferCompletePayload>(payloadString)
        MessageTypes.ERROR -> json.decodeFromString<ErrorPayload>(payloadString)
        MessageTypes.HOST_CHANGED -> json.decodeFromString<HostChangedPayload>(payloadString)
        MessageTypes.KICKED -> json.decodeFromString<KickedPayload>(payloadString)
        MessageTypes.SYNC_STATE -> json.decodeFromString<SyncStatePayload>(payloadString)
        MessageTypes.RECONNECTED -> json.decodeFromString<ReconnectedPayload>(payloadString)
        MessageTypes.USER_RECONNECTED -> json.decodeFromString<UserReconnectedPayload>(payloadString)
        MessageTypes.USER_DISCONNECTED ->
          json.decodeFromString<UserDisconnectedPayload>(payloadString)
        MessageTypes.SUGGESTION_RECEIVED ->
          json.decodeFromString<SuggestionReceivedPayload>(payloadString)
        MessageTypes.SUGGESTION_APPROVED ->
          json.decodeFromString<SuggestionApprovedPayload>(payloadString)
        MessageTypes.SUGGESTION_REJECTED ->
          json.decodeFromString<SuggestionRejectedPayload>(payloadString)
        MessageTypes.CHAT -> json.decodeFromString<ChatMessagePayload>(payloadString)
        MessageTypes.ROOM_SETTINGS_CHANGED ->
          json.decodeFromString<UpdateRoomSettingsPayload>(payloadString)
        else -> null
      }
    } catch (_: Exception) {
      null
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> serializer(value: T): kotlinx.serialization.KSerializer<T> {
    return when (value) {
      is CreateRoomPayload -> CreateRoomPayload.serializer()
      is JoinRoomPayload -> JoinRoomPayload.serializer()
      is ApproveJoinPayload -> ApproveJoinPayload.serializer()
      is RejectJoinPayload -> RejectJoinPayload.serializer()
      is PlaybackActionPayload -> PlaybackActionPayload.serializer()
      is BufferReadyPayload -> BufferReadyPayload.serializer()
      is KickUserPayload -> KickUserPayload.serializer()
      is SuggestTrackPayload -> SuggestTrackPayload.serializer()
      is ApproveSuggestionPayload -> ApproveSuggestionPayload.serializer()
      is RejectSuggestionPayload -> RejectSuggestionPayload.serializer()
      is ReconnectPayload -> ReconnectPayload.serializer()
      is TransferHostPayload -> TransferHostPayload.serializer()
      is ChatPayload -> ChatPayload.serializer()
      is UpdateRoomSettingsPayload -> UpdateRoomSettingsPayload.serializer()
      else -> throw IllegalArgumentException("Unknown type: ${value!!::class.simpleName}")
    }
      as kotlinx.serialization.KSerializer<T>
  }
}
