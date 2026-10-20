package com.meow.music.discord

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject

object MeowDiscordClient {
  private const val TAG = "MeowDiscordClient"
  private const val GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"

  private val mutex = Mutex()
  private var client: OkHttpClient? = null
  private var webSocket: WebSocket? = null
  private var scope: CoroutineScope? = null
  private var heartbeatJob: Job? = null
  private var activeToken: String? = null
  private var isReady = false

  private val messageChannel = Channel<String>(Channel.UNLIMITED)
  private var processingJob: Job? = null

  suspend fun updatePresence(accessToken: String, activity: MeowDiscordActivity): Result<Unit> =
    withContext(Dispatchers.IO) {
      mutex.withLock {
        val token = accessToken.trim()
        if (token.isBlank()) {
          return@withLock Result.failure(IllegalArgumentException("Discord token is blank"))
        }

        var connectResult = ensureConnected(token)
        if (connectResult.isFailure) {
          disconnectInternal()
          connectResult = ensureConnected(token)
        }
        if (connectResult.isFailure) return@withLock connectResult

        val payload = buildPayload(activity)
        var sent = sendOp(3, payload)
        if (!sent) {
          disconnectInternal()
          val retryConnect = ensureConnected(token)
          if (retryConnect.isFailure) {
            return@withLock Result.failure(Exception("Failed to reconnect to Discord gateway"))
          }
          sent = sendOp(3, payload)
        }
        if (!sent) {
          return@withLock Result.failure(Exception("Failed to send presence payload"))
        }
        Result.success(Unit)
      }
    }

  suspend fun clearPresence(accessToken: String? = null): Result<Unit> =
    withContext(Dispatchers.IO) {
      mutex.withLock {
        if (webSocket == null && !accessToken.isNullOrBlank()) {
          ensureConnected(accessToken)
        }
        webSocket ?: return@withLock Result.failure(Exception("Not connected to Discord"))
        val empty = JSONObject().apply {
          put("activities", JSONArray())
          put("afk", false)
          put("since", JSONObject.NULL)
          put("status", "online")
        }
        val sent = sendOp(3, empty)
        if (!sent) return@withLock Result.failure(Exception("Failed to clear presence"))
        Result.success(Unit)
      }
    }

  suspend fun close(): Result<Unit> =
    withContext(Dispatchers.IO) {
      mutex.withLock {
        disconnectInternal()
        Result.success(Unit)
      }
    }

  private suspend fun ensureConnected(token: String): Result<Unit> {
    if (activeToken == token && webSocket != null && isReady) {
      return Result.success(Unit)
    }

    disconnectInternal()

    val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    scope = newScope
    activeToken = token
    isReady = false

    val okHttpClient = OkHttpClient.Builder()
      .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
      .build()
    client = okHttpClient

    val readyDeferred = CompletableDeferred<Unit>()

    val listener = object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d(TAG, "Gateway connected")
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        newScope.launch {
          messageChannel.send(text)
        }
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "Gateway closed: $code / $reason")
        isReady = false
        if (!readyDeferred.isCompleted) {
          readyDeferred.completeExceptionally(Exception("Gateway closed before ready: $reason"))
        }
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e(TAG, "Gateway failure", t)
        isReady = false
        if (!readyDeferred.isCompleted) {
          readyDeferred.completeExceptionally(t)
        }
      }
    }

    processingJob = newScope.launch {
      for (msg in messageChannel) {
        handleGatewayMessage(token, msg, readyDeferred)
      }
    }

    val request = Request.Builder().url(GATEWAY_URL).build()
    webSocket = okHttpClient.newWebSocket(request, listener)

    return runCatching {
      withTimeout(15_000) {
        readyDeferred.await()
      }
    }.fold(
      onSuccess = { Result.success(Unit) },
      onFailure = { e ->
        disconnectInternal()
        Result.failure(e)
      }
    )
  }

  private fun handleGatewayMessage(token: String, raw: String, readyDeferred: CompletableDeferred<Unit>) {
    try {
      val json = JSONObject(raw)
      val op = json.getInt("op")
      val d = json.opt("d")
      val t = json.optString("t", "")

      when (op) {
        10 -> { // HELLO
          val interval = (d as JSONObject).getLong("heartbeat_interval")
          startHeartbeat(interval)
          sendIdentify(token)
        }
        11 -> { // HEARTBEAT_ACK
          // Heartbeat acknowledged
        }
        0 -> { // DISPATCH
          if (t == "READY") {
            isReady = true
            if (!readyDeferred.isCompleted) {
              readyDeferred.complete(Unit)
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error handling gateway message", e)
    }
  }

  private fun startHeartbeat(intervalMs: Long) {
    heartbeatJob?.cancel()
    heartbeatJob = scope?.launch {
      while (isActive) {
        delay(intervalMs)
        sendOp(1, JSONObject.NULL)
      }
    }
  }

  private fun sendIdentify(token: String) {
    val d = JSONObject().apply {
      put("token", token)
      put("intents", 0)
      put("properties", JSONObject().apply {
        put("os", "android")
        put("browser", "Meow")
        put("device", "Meow")
      })
    }
    sendOp(2, d)
  }

  private fun sendOp(op: Int, d: Any): Boolean {
    val ws = webSocket ?: return false
    return try {
      val packet = JSONObject().apply {
        put("op", op)
        put("d", d)
      }
      ws.send(packet.toString())
    } catch (e: Exception) {
      Log.e(TAG, "Failed to send op $op", e)
      false
    }
  }

  private fun disconnectInternal() {
    heartbeatJob?.cancel()
    heartbeatJob = null
    processingJob?.cancel()
    processingJob = null
    try {
      webSocket?.close(1000, "Disconnect")
    } catch (_: Exception) {}
    webSocket = null
    client?.dispatcher?.executorService?.shutdown()
    client = null
    scope?.cancel()
    scope = null
    activeToken = null
    isReady = false
  }

  private fun buildPayload(activity: MeowDiscordActivity): JSONObject {
    val activityJson = JSONObject().apply {
      put("name", activity.name)
      put("type", activity.type)
      activity.details?.let { put("details", it) }
      activity.state?.let { put("state", it) }
      put("application_id", activity.applicationId.toString())

      val timestamps = JSONObject()
      activity.timestamps.startMs?.let { timestamps.put("start", it) }
      activity.timestamps.endMs?.let { timestamps.put("end", it) }
      if (timestamps.length() > 0) {
        put("timestamps", timestamps)
      }

      val assets = JSONObject()
      activity.assets.largeImage?.let { assets.put("large_image", it) }
      activity.assets.largeText?.let { assets.put("large_text", it) }
      activity.assets.smallImage?.let { assets.put("small_image", it) }
      activity.assets.smallText?.let { assets.put("small_text", it) }
      if (assets.length() > 0) {
        put("assets", assets)
      }

      if (activity.buttons.isNotEmpty()) {
        val buttonsArray = JSONArray()
        val urlsArray = JSONArray()
        for (btn in activity.buttons.take(2)) {
          buttonsArray.put(btn.label)
          urlsArray.put(btn.url)
        }
        put("buttons", buttonsArray)
        put("metadata", JSONObject().put("button_urls", urlsArray))
      }

      put("platform", "android")
    }

    return JSONObject().apply {
      put("activities", JSONArray().put(activityJson))
      put("afk", false)
      put("since", JSONObject.NULL)
      put("status", activity.onlineStatus)
    }
  }
}
