@file:Suppress("unused")

package com.lagradost.cloudstream3.plugins

import android.content.Context

data class PluginData(
    val name: String = "",
    val url: String = "",
    val internalName: String = "",
    val version: Int = 0,
    val pluginClassName: String = "",
    val filename: String = "",
    val openSettings: ((Context) -> Unit)? = null
)
