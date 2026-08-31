package com.lagradost.cloudstream3

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.lagradost.cloudstream3.utils.DataStore
import java.lang.ref.WeakReference

object AcraApplication {
    var context: Context? = null

    private var currentActivity: WeakReference<Activity>? = null

    fun setActivity(activity: Activity?) {
        currentActivity = if (activity != null) WeakReference(activity) else null
    }

    fun getActivity(): Activity? {
        return currentActivity?.get()
    }

    @JvmStatic
    fun getSharedPrefs(folder: String? = null): SharedPreferences? {
        return DataStore.getSharedPrefs(folder)
    }

    @JvmStatic
    fun getKey(path: String): String? {
        return DataStore.getKey<String>(path)
    }

    @JvmStatic
    inline fun <reified T : Any> getKey(path: String, def: T): T {
        return DataStore.getKey(path, def)
    }

    @JvmStatic
    inline fun <reified T : Any> getKey(folder: String, path: String, def: T): T {
        return DataStore.getKey(folder, path, def)
    }

    @JvmStatic
    fun setKey(path: String, value: Any?) {
        DataStore.setKey(null, path, value)
    }

    @JvmStatic
    fun setKey(folder: String, path: String, value: Any?) {
        DataStore.setKey(folder, path, value)
    }

    @JvmStatic
    fun removeKey(path: String) {
        DataStore.removeKey(path)
    }

    @JvmStatic
    fun removeKey(folder: String, path: String) {
        DataStore.removeKey(folder, path)
    }

    @JvmStatic
    fun getKeys(folder: String): List<String> {
        return DataStore.getKeys(folder)
    }
}
