package com.lagradost.cloudstream3

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

open class CloudStreamApp : Application() {
    companion object {
        @JvmStatic
        var context: Context?
            get() = AcraApplication.context
            set(value) {
                AcraApplication.context = value
            }

        private fun getPrefs(folder: String? = null): SharedPreferences? {
            val ctx = context ?: return null
            val name = if (folder != null) "cs3_$folder" else "cs3_datastore"
            return ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
        }

        @JvmStatic
        fun getKey(path: String): String? {
            return getPrefs()?.getString(path, null)
        }

        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <T> getKey(path: String, def: T): T {
            val prefs = getPrefs() ?: return def
            return when (def) {
                is Boolean -> (prefs.getBoolean(path, def) as? T) ?: def
                is Int -> (prefs.getInt(path, def) as? T) ?: def
                is Long -> (prefs.getLong(path, def) as? T) ?: def
                is Float -> (prefs.getFloat(path, def) as? T) ?: def
                is String -> (prefs.getString(path, def) as? T) ?: def
                else -> def
            }
        }

        @JvmStatic
        fun setKey(path: String, value: Any?) {
            val editor = getPrefs()?.edit() ?: return
            when (value) {
                is Boolean -> editor.putBoolean(path, value)
                is Int -> editor.putInt(path, value)
                is Long -> editor.putLong(path, value)
                is Float -> editor.putFloat(path, value)
                is String -> editor.putString(path, value)
                null -> editor.remove(path)
                else -> editor.putString(path, value.toString())
            }
            editor.apply()
        }

        @JvmStatic
        fun setKey(folder: String, path: String, value: Any?) {
            val editor = getPrefs(folder)?.edit() ?: return
            when (value) {
                is Boolean -> editor.putBoolean(path, value)
                is Int -> editor.putInt(path, value)
                is Long -> editor.putLong(path, value)
                is Float -> editor.putFloat(path, value)
                is String -> editor.putString(path, value)
                null -> editor.remove(path)
                else -> editor.putString(path, value.toString())
            }
            editor.apply()
        }

        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <T> getKey(folder: String, path: String, def: T): T {
            val prefs = getPrefs(folder) ?: return def
            return when (def) {
                is Boolean -> (prefs.getBoolean(path, def) as? T) ?: def
                is Int -> (prefs.getInt(path, def) as? T) ?: def
                is Long -> (prefs.getLong(path, def) as? T) ?: def
                is Float -> (prefs.getFloat(path, def) as? T) ?: def
                is String -> (prefs.getString(path, def) as? T) ?: def
                else -> def
            }
        }

        @JvmStatic
        fun removeKey(path: String) {
            getPrefs()?.edit()?.remove(path)?.apply()
        }

        @JvmStatic
        fun removeKey(folder: String, path: String) {
            getPrefs(folder)?.edit()?.remove(path)?.apply()
        }

        @JvmStatic
        fun getKeys(folder: String): List<String> {
            return getPrefs(folder)?.all?.keys?.toList() ?: emptyList()
        }
    }
}
