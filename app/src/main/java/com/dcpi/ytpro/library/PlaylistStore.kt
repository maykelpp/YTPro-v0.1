package com.dcpi.ytpro.library

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class UserPlaylist(val id: String, val name: String, val trackUris: List<String>)

class PlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("ytpro_playlists_v1", Context.MODE_PRIVATE)

    fun all(): List<UserPlaylist> {
        val array = JSONArray(prefs.getString("items", "[]"))
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val tracks = item.optJSONArray("tracks") ?: JSONArray()
                val trackUris = buildList {
                    for (j in 0 until tracks.length()) {
                        val uri = tracks.optString(j)
                        if (uri.isNotBlank()) add(uri)
                    }
                }
                add(UserPlaylist(item.optString("id"), item.optString("name"), trackUris))
            }
        }
    }

    fun save(playlist: UserPlaylist) {
        val items = all().filterNot { it.id == playlist.id }.toMutableList()
        items += playlist
        persist(items)
    }

    fun delete(id: String) = persist(all().filterNot { it.id == id })

    private fun persist(playlists: List<UserPlaylist>) {
        val array = JSONArray()
        playlists.forEach { playlist ->
            val tracks = JSONArray()
            playlist.trackUris.forEach(tracks::put)
            array.put(JSONObject().put("id", playlist.id).put("name", playlist.name).put("tracks", tracks))
        }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
