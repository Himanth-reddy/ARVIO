package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.utils.AppUtils.toJson

object DataStore {
    fun getDefaultSharedPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
    }

    fun getDefaultSharedPrefs(): SharedPreferences? {
        val ctx = AcraApplication.context ?: return null
        return getDefaultSharedPrefs(ctx)
    }

    fun getSharedPrefs(context: Context, folder: String? = null): SharedPreferences {
        val name = if (folder != null) "cs3_$folder" else "cs3_datastore"
        return context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    fun getSharedPrefs(context: Context): SharedPreferences = getSharedPrefs(context, null)

    fun getSharedPrefs(folder: String? = null): SharedPreferences? {
        val ctx = AcraApplication.context ?: return null
        return getSharedPrefs(ctx, folder)
    }

    fun getSharedPrefs(): SharedPreferences? = getSharedPrefs(null as String?)

    fun getFolder(context: Context, folder: String): SharedPreferences = getSharedPrefs(context, folder)
    fun getFolder(folder: String): SharedPreferences? = getSharedPrefs(folder)

    fun containsKey(context: Context, path: String): Boolean = getSharedPrefs(context).contains(path)
    fun containsKey(context: Context, folder: String, path: String): Boolean = getSharedPrefs(context, folder).contains(path)
    fun containsKey(path: String): Boolean = getSharedPrefs()?.contains(path) ?: false
    fun containsKey(folder: String, path: String): Boolean = getSharedPrefs(folder)?.contains(path) ?: false

    fun containsDefaultKey(context: Context, path: String): Boolean = getDefaultSharedPrefs(context).contains(path)
    fun containsDefaultKey(path: String): Boolean = getDefaultSharedPrefs()?.contains(path) ?: false

    fun removeKey(context: Context, path: String) {
        getSharedPrefs(context).edit().remove(path).apply()
        getDefaultSharedPrefs(context).edit().remove(path).apply()
    }
    fun removeKey(context: Context, folder: String, path: String) {
        getSharedPrefs(context, folder).edit().remove(path).apply()
    }
    fun removeKey(path: String) {
        val ctx = AcraApplication.context
        if (ctx != null) {
            removeKey(ctx, path)
        } else {
            getSharedPrefs()?.edit()?.remove(path)?.apply()
            getDefaultSharedPrefs()?.edit()?.remove(path)?.apply()
        }
    }
    fun removeKey(folder: String, path: String) {
        getSharedPrefs(folder)?.edit()?.remove(path)?.apply()
    }

    fun removeDefaultKey(context: Context, path: String) {
        getDefaultSharedPrefs(context).edit().remove(path).apply()
        getSharedPrefs(context).edit().remove(path).apply()
    }
    fun removeDefaultKey(path: String) {
        val ctx = AcraApplication.context
        if (ctx != null) {
            removeDefaultKey(ctx, path)
        } else {
            getDefaultSharedPrefs()?.edit()?.remove(path)?.apply()
        }
    }

    fun removeKeys(context: Context, folder: String): Int {
        val prefs = getSharedPrefs(context, folder)
        val count = prefs.all.size
        prefs.edit().clear().apply()
        return count
    }
    fun removeKeys(folder: String): Int {
        val prefs = getSharedPrefs(folder) ?: return 0
        val count = prefs.all.size
        prefs.edit()?.clear()?.apply()
        return count
    }

    fun getKeys(context: Context, folder: String): List<String> {
        return getSharedPrefs(context, folder).all.keys.toList()
    }
    fun getKeys(folder: String): List<String> {
        return getSharedPrefs(folder)?.all?.keys?.toList() ?: emptyList()
    }

    fun <T> setKey(context: Context, path: String, value: T) {
        setKey(context, null, path, value)
    }
    fun <T> setKey(context: Context, folder: String?, path: String, value: T) {
        val editor = getSharedPrefs(context, folder).edit()
        val defaultEditor = if (folder == null) getDefaultSharedPrefs(context).edit() else null

        if (value == null) {
            editor.remove(path).apply()
            defaultEditor?.remove(path)?.apply()
            return
        }
        when (value) {
            is Boolean -> {
                editor.putBoolean(path, value)
                defaultEditor?.putBoolean(path, value)
            }
            is Int -> {
                editor.putInt(path, value)
                defaultEditor?.putInt(path, value)
            }
            is Long -> {
                editor.putLong(path, value)
                defaultEditor?.putLong(path, value)
            }
            is Float -> {
                editor.putFloat(path, value)
                defaultEditor?.putFloat(path, value)
            }
            is String -> {
                editor.putString(path, value)
                defaultEditor?.putString(path, value)
            }
            else -> {
                val jsonStr = try {
                    (value as Any).toJson()
                } catch (_: Throwable) {
                    value.toString()
                }
                editor.putString(path, jsonStr)
                defaultEditor?.putString(path, jsonStr)
            }
        }
        editor.apply()
        defaultEditor?.apply()
    }
    fun <T> setKey(path: String, value: T) {
        setKey(null, path, value)
    }
    fun <T> setKey(folder: String?, path: String, value: T) {
        val ctx = AcraApplication.context ?: return
        setKey(ctx, folder, path, value)
    }

    fun <T> setKeyDefault(context: Context, path: String, value: T) {
        setKey(context, null, path, value)
    }
    fun <T> setKeyDefault(path: String, value: T) {
        val ctx = AcraApplication.context ?: return
        setKeyDefault(ctx, path, value)
    }

    inline fun <reified T : Any> getKey(context: Context, path: String, def: T): T {
        return getKeyFromFolder<T>(context, null, path) ?: def
    }
    inline fun <reified T : Any> getKey(context: Context, path: String): T? {
        return getKeyFromFolder<T>(context, null, path)
    }
    inline fun <reified T : Any> getKey(context: Context, folder: String?, path: String, def: T): T {
        return getKeyFromFolder<T>(context, folder, path) ?: def
    }
    inline fun <reified T : Any> getKeyFromFolder(context: Context, folder: String?, path: String): T? {
        val prefs = getSharedPrefs(context, folder)
        val targetPrefs = if (prefs.contains(path)) prefs else if (folder == null && getDefaultSharedPrefs(context).contains(path)) getDefaultSharedPrefs(context) else null
        if (targetPrefs == null) return null

        val clazz = T::class.java
        return try {
            when (clazz) {
                Boolean::class.java, java.lang.Boolean::class.java -> targetPrefs.getBoolean(path, false) as? T
                Int::class.java, java.lang.Integer::class.java -> targetPrefs.getInt(path, 0) as? T
                Long::class.java, java.lang.Long::class.java -> targetPrefs.getLong(path, 0L) as? T
                Float::class.java, java.lang.Float::class.java -> targetPrefs.getFloat(path, 0f) as? T
                String::class.java, java.lang.String::class.java -> {
                    val s = targetPrefs.getString(path, null) ?: targetPrefs.all[path]?.toString()
                    s as? T
                }
                else -> {
                    val raw = targetPrefs.getString(path, null) ?: targetPrefs.all[path]?.toString() ?: return null
                    try {
                        AppUtils.parseJson<T>(raw)
                    } catch (_: Throwable) {
                        null
                    }
                }
            }
        } catch (_: Throwable) {
            try {
                val raw = targetPrefs.all[path]
                if (raw is T) raw else null
            } catch (_: Throwable) {
                null
            }
        }
    }

    inline fun <reified T : Any> getKeyDefault(context: Context, path: String, def: T): T {
        val prefs = getDefaultSharedPrefs(context)
        if (!prefs.contains(path)) return def
        val clazz = T::class.java
        return try {
            when (clazz) {
                Boolean::class.java, java.lang.Boolean::class.java -> (prefs.getBoolean(path, false) as? T) ?: def
                Int::class.java, java.lang.Integer::class.java -> (prefs.getInt(path, 0) as? T) ?: def
                Long::class.java, java.lang.Long::class.java -> (prefs.getLong(path, 0L) as? T) ?: def
                Float::class.java, java.lang.Float::class.java -> (prefs.getFloat(path, 0f) as? T) ?: def
                String::class.java, java.lang.String::class.java -> (prefs.getString(path, null) as? T) ?: def
                else -> {
                    val raw = prefs.getString(path, null) ?: return def
                    try {
                        AppUtils.parseJson<T>(raw) ?: def
                    } catch (_: Throwable) {
                        def
                    }
                }
            }
        } catch (_: Throwable) {
            def
        }
    }

    inline fun <reified T : Any> getKeyDefault(path: String, def: T): T {
        val ctx = AcraApplication.context ?: return def
        return getKeyDefault(ctx, path, def)
    }

    inline fun <reified T : Any> getKey(path: String, def: T): T {
        return getKeyFromFolder<T>(null, path) ?: def
    }
    inline fun <reified T : Any> getKey(path: String): T? {
        return getKeyFromFolder<T>(null, path)
    }
    inline fun <reified T : Any> getKey(folder: String?, path: String, def: T): T {
        return getKeyFromFolder<T>(folder, path) ?: def
    }
    inline fun <reified T : Any> getKeyFromFolder(folder: String?, path: String): T? {
        val ctx = AcraApplication.context ?: return null
        return getKeyFromFolder<T>(ctx, folder, path)
    }

    @JvmStatic
    fun <T> getKey(context: Context, folder: String?, path: String, valueType: Class<T>): T? {
        val prefs = getSharedPrefs(context, folder)
        if (!prefs.contains(path)) return null
        return try {
            when (valueType) {
                Boolean::class.java, java.lang.Boolean::class.java -> @Suppress("UNCHECKED_CAST") (prefs.getBoolean(path, false) as T)
                Int::class.java, java.lang.Integer::class.java -> @Suppress("UNCHECKED_CAST") (prefs.getInt(path, 0) as T)
                Long::class.java, java.lang.Long::class.java -> @Suppress("UNCHECKED_CAST") (prefs.getLong(path, 0L) as T)
                Float::class.java, java.lang.Float::class.java -> @Suppress("UNCHECKED_CAST") (prefs.getFloat(path, 0f) as T)
                String::class.java, java.lang.String::class.java -> @Suppress("UNCHECKED_CAST") (prefs.getString(path, null) as T)
                else -> {
                    val raw = prefs.getString(path, null) ?: return null
                    try {
                        com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readValue(raw, valueType)
                    } catch (_: Throwable) {
                        null
                    }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    @JvmStatic
    fun <T> getKey(folder: String?, path: String, valueType: Class<T>): T? {
        val ctx = AcraApplication.context ?: return null
        return getKey(ctx, folder, path, valueType)
    }
}
