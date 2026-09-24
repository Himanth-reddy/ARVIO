package com.arflix.tv.data.repository

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import org.json.JSONArray
import org.json.JSONObject

/** Catalog membership and hidden IDs are versioned, including deliberately empty lists. */
internal object CatalogCloudFields {
    val fields = setOf("catalogsByProfile", "hiddenPreinstalledByProfile", "hiddenAddonByProfile", "hiddenHomeServerByProfile")
    private val timestampsKey = stringPreferencesKey("cloud_sync_field_ts")
    private val baselineKey = stringPreferencesKey("cloud_sync_field_base")

    fun key(profileId: String, field: String) = "c:$profileId:$field"

    fun hasBaseline(prefs: Preferences, profileId: String, field: String) =
        parse(prefs[baselineKey]).has(key(profileId, field))

    fun keys(root: JSONObject): List<String> = buildList {
        for (field in fields) {
            val profiles = root.optJSONObject(field) ?: continue
            for (id in profiles.keys()) if (profiles.optJSONArray(id) != null) add(key(id, field))
        }
    }

    fun value(root: JSONObject, key: String): Any? {
        val path = key.removePrefix("c:")
        return root.optJSONObject(path.substringAfter(':'))?.optJSONArray(path.substringBefore(':'))
    }

    fun put(root: JSONObject, key: String, value: Any) {
        val path = key.removePrefix("c:")
        val field = path.substringAfter(':')
        require(field in fields)
        val profiles = root.optJSONObject(field) ?: JSONObject().also { root.put(field, it) }
        profiles.put(path.substringBefore(':'), value)
    }

    fun stamp(prefs: MutablePreferences, profileId: String, field: String, json: String) {
        val key = key(profileId, field)
        val timestamps = parse(prefs[timestampsKey])
        val baseline = parse(prefs[baselineKey])
        timestamps.put(key, maxOf(System.currentTimeMillis(), timestamps.optLong(key) + 1))
        baseline.put(key, json)
        prefs[timestampsKey] = timestamps.toString()
        prefs[baselineKey] = baseline.toString()
    }

    fun isNewerLocal(prefs: Preferences, profileId: String, field: String, incoming: JSONObject): Boolean {
        val key = key(profileId, field)
        return parse(prefs[timestampsKey]).optLong(key) > incoming.optLong(key)
    }

    fun recordRemote(prefs: MutablePreferences, profileId: String, field: String, json: String, incoming: JSONObject) {
        val key = key(profileId, field)
        val timestamps = parse(prefs[timestampsKey])
        val baseline = parse(prefs[baselineKey])
        timestamps.put(key, incoming.optLong(key))
        baseline.put(key, json)
        prefs[timestampsKey] = timestamps.toString()
        prefs[baselineKey] = baseline.toString()
    }

    fun reconcileSnapshot(root: JSONObject, timestamps: JSONObject, baseline: JSONObject, captured: JSONObject) {
        for (key in keys(root)) {
            if (timestamps.optLong(key) > captured.optLong(key) && baseline.has(key)) {
                put(root, key, JSONArray(baseline.getString(key)))
            }
        }
    }

    private fun parse(raw: String?): JSONObject = try {
        if (raw.isNullOrBlank()) JSONObject() else JSONObject(raw)
    } catch (_: org.json.JSONException) { JSONObject() }
}
