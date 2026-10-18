package com.meow.music.discord

import org.json.JSONObject

data class GatewaySessionState(
  val sessionId: String,
  val seq: Int,
  val resumeGatewayUrl: String,
)

data class GatewayReadyEvent(
  val user: GatewayReadyUser,
  val sessionId: String,
  val resumeGatewayUrl: String,
) {
  companion object {
    fun fromJson(obj: JSONObject): GatewayReadyEvent {
      val userObj = obj.optJSONObject("user")
      val id = userObj?.optString("id", "") ?: ""
      val username = userObj?.optString("username", "") ?: ""
      val globalName = userObj?.optString("global_name", null)
      val sessionId = obj.optString("session_id", "")
      val resumeUrl = obj.optString("resume_gateway_url", "wss://gateway.discord.gg")
      return GatewayReadyEvent(
        user = GatewayReadyUser(
          id = id,
          username = username,
          globalName = globalName,
        ),
        sessionId = sessionId,
        resumeGatewayUrl = resumeUrl,
      )
    }
  }
}

data class GatewayReadyUser(
  val id: String,
  val username: String,
  val globalName: String? = null,
)

data class GatewayCloseInfo(
  val code: Int,
  val reason: String,
  val resumable: Boolean,
  val session: GatewaySessionState?,
)
