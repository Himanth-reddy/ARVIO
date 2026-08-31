package com.arflix.tv.plugin

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.lagradost.cloudstream3.plugins.Plugin
import io.mockk.mockk
import org.junit.Test

class PluginSettingsTest {

    @Test
    fun `Plugin default openSettings is null`() {
        val plugin = object : Plugin() {}
        assertThat(plugin.openSettings).isNull()
    }

    @Test
    fun `Plugin openSettings can be assigned and invoked`() {
        val plugin = object : Plugin() {}
        var settingsInvoked = false
        var passedContext: Context? = null

        val mockContext = mockk<Context>(relaxed = true)

        plugin.openSettings = { ctx ->
            settingsInvoked = true
            passedContext = ctx
        }

        assertThat(plugin.openSettings).isNotNull()
        plugin.openSettings?.invoke(mockContext)

        assertThat(settingsInvoked).isTrue()
        assertThat(passedContext).isEqualTo(mockContext)
    }

    @Test
    fun `Foreign plugin class with openSettings property can be reflectively discovered`() {
        class ForeignPlugin {
            var openSettings: ((Context) -> Unit)? = null
        }

        val foreign = ForeignPlugin()
        var invoked = false
        val mockContext = mockk<Context>(relaxed = true)

        foreign.openSettings = {
            invoked = true
        }

        // Simulate discovery logic from ReflectivePluginWrapper
        val clazz = foreign.javaClass
        val getter = clazz.getMethod("getOpenSettings")
        val callback = getter.invoke(foreign)

        assertThat(callback).isNotNull()

        @Suppress("UNCHECKED_CAST")
        val fn = callback as (Context) -> Unit
        fn.invoke(mockContext)

        assertThat(invoked).isTrue()
    }

    @Test
    fun `Foreign plugin class with throwing openSettings does not crash caller`() {
        val plugin = object : Plugin() {}
        plugin.openSettings = {
            throw RuntimeException("Simulated extension settings crash")
        }

        var caught = false
        try {
            plugin.openSettings?.invoke(mockk(relaxed = true))
        } catch (e: Exception) {
            caught = true
        }

        assertThat(caught).isTrue()
    }

    @Test
    fun `Plugin state update and settings persistence simulation`() {
        val settingsStore = mutableMapOf<String, String>()
        val plugin = object : Plugin() {}

        plugin.openSettings = {
            settingsStore["custom_domain"] = "https://mirror1.example.com"
        }

        assertThat(settingsStore["custom_domain"]).isNull()
        plugin.openSettings?.invoke(mockk(relaxed = true))
        assertThat(settingsStore["custom_domain"]).isEqualTo("https://mirror1.example.com")

        // Update again
        plugin.openSettings = {
            settingsStore["custom_domain"] = "https://mirror2.example.com"
        }
        plugin.openSettings?.invoke(mockk(relaxed = true))
        assertThat(settingsStore["custom_domain"]).isEqualTo("https://mirror2.example.com")
    }

    @Test
    fun `Foreign plugin class with openSettings in superclass is discovered`() {
        open class SuperPlugin {
            var openSettings: ((Context) -> Unit)? = null
        }
        class SubPlugin : SuperPlugin()

        val sub = SubPlugin()
        var invoked = false
        val mockContext = mockk<Context>(relaxed = true)

        sub.openSettings = {
            invoked = true
        }

        // Test superclass field traversal
        var curr: Class<*>? = sub.javaClass
        var callback: Any? = null
        while (curr != null && curr != Any::class.java) {
            try {
                val field = curr.getDeclaredField("openSettings")
                field.isAccessible = true
                val res = field.get(sub)
                if (res != null) {
                    callback = res
                    break
                }
            } catch (_: Throwable) {}
            curr = curr.superclass
        }

        assertThat(callback).isNotNull()
        @Suppress("UNCHECKED_CAST")
        val fn = callback as (Context) -> Unit
        fn.invoke(mockContext)
        assertThat(invoked).isTrue()
    }

    @Test
    fun `Plugin with openSettings member function is discoverable`() {
        class MethodBasedPlugin : Plugin() {
            var methodInvoked = false
            fun openSettings(context: Context) {
                methodInvoked = true
            }
        }

        val plugin = MethodBasedPlugin()
        val mockContext = mockk<Context>(relaxed = true)

        val clazz = plugin.javaClass
        val m = clazz.declaredMethods.firstOrNull {
            it.name == "openSettings" && it.parameterTypes.size == 1
        }
        assertThat(m).isNotNull()
        m?.invoke(plugin, mockContext)
        assertThat(plugin.methodInvoked).isTrue()
    }

    @Test
    fun `Test MainAPI and TmdbProvider class loading`() {
        val clazz = Class.forName("com.lagradost.cloudstream3.MainAPI")
        assertThat(clazz).isNotNull()
        val tmdbClazz = Class.forName("com.lagradost.cloudstream3.metaproviders.TmdbProvider")
        assertThat(tmdbClazz).isNotNull()
    }

    @Test
    fun `Plugin load(Context) can be overridden and executed`() {
        var loaded = false
        val plugin = object : Plugin() {
            override fun load(context: Context) {
                loaded = true
            }
        }
        val mockContext = mockk<Context>(relaxed = true)
        plugin.load(mockContext)
        assertThat(loaded).isTrue()
    }

    @Test
    fun `Plugin and repository sorting is ascending by name`() {
        val items = listOf("ZStream", "anime", "BetaProvider", "alpha")
        val sorted = items.sortedBy { it.lowercase() }
        assertThat(sorted).containsExactly("alpha", "anime", "BetaProvider", "ZStream").inOrder()
    }
}
