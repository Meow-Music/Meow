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

  val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
  }

  fun encodeString(msgType: String, payload: Any?): String {
    val payloadElement = if (payload != null) {
      runCatching {
        when (payload) {
          is CreateRoomPayload -> json.encodeToJsonElement(CreateRoomPayload.serializer(), payload)
          is JoinRoomPayload -> json.encodeToJsonElement(JoinRoomPayload.serializer(), payload)
          is ApproveJoinPayload -> json.encodeToJsonElement(ApproveJoinPayload.serializer(), payload)
          is RejectJoinPayload -> json.encodeToJsonElement(RejectJoinPayload.serializer(), payload)
          is PlaybackActionPayload -> json.encodeToJsonElement(PlaybackActionPayload.serializer(), payload)
          is BufferReadyPayload -> json.encodeToJsonElement(BufferReadyPayload.serializer(), payload)
          is KickUserPayload -> json.encodeToJsonElement(KickUserPayload.serializer(), payload)
          is SuggestTrackPayload -> json.encodeToJsonElement(SuggestTrackPayload.serializer(), payload)
          is ApproveSuggestionPayload -> json.encodeToJsonElement(ApproveSuggestionPayload.serializer(), payload)
          is RejectSuggestionPayload -> json.encodeToJsonElement(RejectSuggestionPayload.serializer(), payload)
          is ReconnectPayload -> json.encodeToJsonElement(ReconnectPayload.serializer(), payload)
          is TransferHostPayload -> json.encodeToJsonElement(TransferHostPayload.serializer(), payload)
          is ChatPayload -> json.encodeToJsonElement(ChatPayload.serializer(), payload)
          is UpdateRoomSettingsPayload -> json.encodeToJsonElement(UpdateRoomSettingsPayload.serializer(), payload)
          else -> null
        }
      }.getOrNull()
    } else {
      null
    }

    val jsonObject = buildJsonObject {
      put("type", msgType)
      if (payloadElement != null) {
        put("payload", payloadElement)
      }
    }

    return jsonObject.toString()
  }

  fun encode(msgType: String, payload: Any?): ByteArray {
    var data = encodeString(msgType, payload).toByteArray(Charsets.UTF_8)
    if (compressionEnabled && data.size > COMPRESSION_THRESHOLD) {
      val compressed = compressData(data)
      if (compressed.size < data.size) {
        data = compressed
      }
    }
    return data
  }

  fun decodeString(text: String): Pair<String, String> {
    return try {
      val jsonObject = json.parseToJsonElement(text).jsonObject
      val type = jsonObject["type"]?.jsonPrimitive?.content ?: ""
      val payloadElement = jsonObject["payload"]
      val payloadStr = payloadElement?.toString() ?: ""
      Pair(type, payloadStr)
    } catch (_: Exception) {
      Pair("", "")
    }
  }

  fun decode(data: ByteArray): Pair<String, ByteArray> {
    val actualData =
      if (
        compressionEnabled && data.size > 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()
      ) {
        decompressData(data) ?: data
      } else {
        data
      }

    try {
      val text = actualData.decodeToString()
      val (type, payloadStr) = decodeString(text)
      return Pair(type, payloadStr.toByteArray(Charsets.UTF_8))
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
    return decodeJsonPayload(msgType, payloadBytes.decodeToString())
  }

  fun decodeJsonPayload(msgType: String, payloadString: String): Any? {
    if (payloadString.isBlank()) return null
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
}
