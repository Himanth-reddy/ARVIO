package com.lagradost.cloudstream3.utils

import android.content.Context

object DataStoreHelper {
    inline fun <reified T : Any> setKey(path: String, value: T) = DataStore.setKey(path, value)
    inline fun <reified T : Any> setKey(folder: String, path: String, value: T) = DataStore.setKey(folder, path, value)
    inline fun <reified T : Any> getKey(path: String, def: T): T = DataStore.getKey<T>(path, def)
    inline fun <reified T : Any> getKey(path: String): T? = DataStore.getKey<T>(path)
    inline fun <reified T : Any> getKey(folder: String, path: String, def: T): T = DataStore.getKey<T>(folder, path, def)
    inline fun <reified T : Any> getKey(folder: String, path: String): T? = DataStore.getKeyFromFolder<T>(folder, path)
    fun removeKey(path: String) = DataStore.removeKey(path)
    fun removeKey(folder: String, path: String) = DataStore.removeKey(folder, path)
    fun containsKey(path: String): Boolean = DataStore.containsKey(path)
    fun containsKey(folder: String, path: String): Boolean = DataStore.containsKey(folder, path)
}

fun <T : Any> Context.setKey(path: String, value: T) = DataStore.setKey(this, path, value)
fun <T : Any> Context.setKey(folder: String, path: String, value: T) = DataStore.setKey(this, folder, path, value)
inline fun <reified T : Any> Context.getKey(path: String, def: T): T = DataStore.getKey(this, path, def)
inline fun <reified T : Any> Context.getKey(path: String): T? = DataStore.getKey(this, path)
inline fun <reified T : Any> Context.getKey(folder: String, path: String, def: T): T = DataStore.getKey(this, folder, path, def)
inline fun <reified T : Any> Context.getKey(folder: String, path: String): T? = DataStore.getKeyFromFolder(this, folder, path)
