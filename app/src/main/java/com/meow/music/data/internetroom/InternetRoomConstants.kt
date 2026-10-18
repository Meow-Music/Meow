package com.meow.music.data.internetroom

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.meow.music.data.prefs.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>, default: T): T {
    return runBlocking {
        data.first()[key] ?: default
    }
}

val ListenTogetherSessionTokenKey = stringPreferencesKey("listen_together_session_token")
val ListenTogetherRoomCodeKey = stringPreferencesKey("listen_together_room_code")
val ListenTogetherUserIdKey = stringPreferencesKey("listen_together_user_id")
val ListenTogetherIsHostKey = booleanPreferencesKey("listen_together_is_host")
val ListenTogetherSessionTimestampKey = longPreferencesKey("listen_together_session_timestamp")
val ListenTogetherServerUrlKey = stringPreferencesKey("listen_together_server_url")
val ListenTogetherAutoApprovalKey = booleanPreferencesKey("listen_together_auto_approval")
val ListenTogetherBlockedUsersKey = stringPreferencesKey("listen_together_blocked_users")
val ListenTogetherSmartResyncKey = booleanPreferencesKey("listen_together_smart_resync")
val ListenTogetherSyncVolumeKey = booleanPreferencesKey("listen_together_sync_volume")
val ListenTogetherUsernameKey = stringPreferencesKey("listen_together_username")

object InternetRoomConstants {
    val autoApprovalKey = ListenTogetherAutoApprovalKey
    val isHostKey = ListenTogetherIsHostKey
    val roomCodeKey = ListenTogetherRoomCodeKey
    val serverUrlKey = ListenTogetherServerUrlKey
    val sessionTimestampKey = ListenTogetherSessionTimestampKey
    val sessionTokenKey = ListenTogetherSessionTokenKey
    val userIdKey = ListenTogetherUserIdKey
    val smartResyncKey = ListenTogetherSmartResyncKey
    val syncVolumeKey = ListenTogetherSyncVolumeKey
    val usernameKey = ListenTogetherUsernameKey
    val blockedUsersKey = ListenTogetherBlockedUsersKey
}
