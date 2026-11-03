package com.pulse.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("pulse_prefs", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = sp.getBoolean("onboarding", false)
        set(v) = sp.edit().putBoolean("onboarding", v).apply()

    /** 0 = sistema, 1 = claro, 2 = escuro */
    var themeMode: Int
        get() = sp.getInt("theme", 2)
        set(v) = sp.edit().putInt("theme", v).apply()

    /** Pastas que o usuário tirou da biblioteca. Por padrão todas entram. */
    var excludedFolders: Set<String>
        get() = sp.getStringSet("excluded", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("excluded", v).apply()

    var favorites: Set<Long>
        get() = (sp.getStringSet("fav", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
        set(v) = sp.edit().putStringSet("fav", v.map { it.toString() }.toSet()).apply()

    var playlists: List<Playlist>
        get() = try {
            val arr = JSONArray(sp.getString("playlists", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val t = o.getJSONArray("t")
                Playlist(o.getString("id"), o.getString("n"), (0 until t.length()).map { t.getLong(it) })
            }
        } catch (e: Exception) {
            emptyList()
        }
        set(v) {
            val arr = JSONArray()
            v.forEach { p ->
                arr.put(JSONObject().put("id", p.id).put("n", p.name).put("t", JSONArray(p.trackIds)))
            }
            sp.edit().putString("playlists", arr.toString()).apply()
        }

    // ---- retomar de onde parou ----
    var lastQueue: List<Long>
        get() = (sp.getString("lastQueue", "") ?: "").split(',').mapNotNull { it.toLongOrNull() }
        set(v) = sp.edit().putString("lastQueue", v.joinToString(",")).apply()
    var lastTrackId: Long
        get() = sp.getLong("lastTrackId", -1L)
        set(v) = sp.edit().putLong("lastTrackId", v).apply()
    var lastPosition: Long
        get() = sp.getLong("lastPosition", 0L)
        set(v) = sp.edit().putLong("lastPosition", v).apply()
    var shuffle: Boolean
        get() = sp.getBoolean("shuffle", false)
        set(v) = sp.edit().putBoolean("shuffle", v).apply()
    var repeat: Int
        get() = sp.getInt("repeat", 0)
        set(v) = sp.edit().putInt("repeat", v).apply()

    // ---- equalizador ----
    var eqEnabled: Boolean
        get() = sp.getBoolean("eqEnabled", false)
        set(v) = sp.edit().putBoolean("eqEnabled", v).apply()
    var eqLevels: List<Int>
        get() = (sp.getString("eqLevels", "") ?: "").split(',').mapNotNull { it.toIntOrNull() }
        set(v) = sp.edit().putString("eqLevels", v.joinToString(",")).apply()
    var bassStrength: Int
        get() = sp.getInt("bass", 0)
        set(v) = sp.edit().putInt("bass", v).apply()
}
