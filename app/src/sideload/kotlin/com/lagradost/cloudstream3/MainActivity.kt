package com.lagradost.cloudstream3

import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.utils.Event

open class MainActivity : AppCompatActivity() {
    companion object {
        @JvmStatic
        var activity: MainActivity? = null

        @JvmStatic
        var lastError: String? = null

        @JvmStatic
        val bookmarksUpdatedEvent = Event<Boolean>()

        @JvmStatic
        val reloadHomeEvent = Event<Boolean>()

        @JvmStatic
        val afterPluginsLoadedEvent = Event<Boolean>()
    }
}
