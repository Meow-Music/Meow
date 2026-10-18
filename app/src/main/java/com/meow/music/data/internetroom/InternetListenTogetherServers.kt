package com.meow.music.data.internetroom

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class ListenTogetherServer(
  val name: String,
  val url: String,
  val location: String,
  val operator: String
)

object InternetListenTogetherServers {
  private const val SERVER_JSON_URL =
    "https://raw.githubusercontent.com/EchoMusicApp/Echo-Music/refs/heads/main/app/server.json"

  private val _servers =
    MutableStateFlow(
      listOf(
        ListenTogetherServer(
          name = "VIVI Global Sync",
          url = "wss://devilmi-vivi-music-listen-together.hf.space",
          location = "Global",
          operator = "Rexx"
        ),
        ListenTogetherServer(
          name = "Metrolist Server",
          url = "wss://metroserverx.meowery.eu/ws",
          location = "Poland",
          operator = "Metrolist"
        )
      )
    )

  val serversFlow: StateFlow<List<ListenTogetherServer>> = _servers

  val servers: List<ListenTogetherServer>
    get() = _servers.value

  init {
    GlobalScope.launch(Dispatchers.IO) {
      try {
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder().url(SERVER_JSON_URL).build()
        val response = client.newCall(request).execute()
        response.body?.string()?.let { jsonString ->
          val jsonObject = Json.parseToJsonElement(jsonString).jsonObject
          val name = jsonObject["name"]?.jsonPrimitive?.content ?: "VIVI Global Sync"
          val url =
            jsonObject["serverUrl"]?.jsonPrimitive?.content
              ?: "wss://devilmi-vivi-music-listen-together.hf.space"
          val region = jsonObject["region"]?.jsonPrimitive?.content ?: "Global - VIVIDH"

          val current = _servers.value.toMutableList()
          if (current.none { it.url == url }) {
            current.add(0, ListenTogetherServer(name = name, url = url, location = region, operator = ""))
            _servers.value = current
          }
        }
      } catch (_: Exception) {
        // Fallback implicitly retained
      }
    }
  }

  val defaultServerUrl: String
    get() = servers.firstOrNull()?.url ?: "wss://devilmi-vivi-music-listen-together.hf.space"

  fun findByUrl(url: String): ListenTogetherServer? = servers.firstOrNull { it.url == url }
}
