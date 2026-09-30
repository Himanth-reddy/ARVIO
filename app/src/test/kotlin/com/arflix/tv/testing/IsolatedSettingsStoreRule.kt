package com.arflix.tv.testing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.arflix.tv.util.settingsDataStore
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.rules.ExternalResource
import java.io.File
import java.nio.file.Files

/** Prevent the process-wide delegate from retaining another Robolectric test's deleted directory. */
class IsolatedSettingsStoreRule : ExternalResource() {
    private val job = SupervisorJob()
    private lateinit var directory: File

    override fun before() {
        directory = Files.createTempDirectory("arvio-test-settings-").toFile()
        // DataStore 1.0 uses File.renameTo, which cannot replace an existing file on Windows.
        // Repository behavior is tested with the same atomic preferences contract there;
        // Linux CI retains the real disk-backed store.
        val store = if (System.getProperty("os.name").startsWith("Windows")) {
            object : DataStore<Preferences> {
                override val data = MutableStateFlow(emptyPreferences())
                private val mutex = Mutex()

                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                    mutex.withLock { transform(data.value).toPreferences().also { data.value = it } }
            }
        } else PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { File(directory, "settings.preferences_pb") }
        )
        mockkStatic("com.arflix.tv.util.DataStoresKt")
        every { any<Context>().settingsDataStore } returns store
    }

    override fun after() {
        try {
            runBlocking { job.cancelAndJoin() }
        } finally {
            unmockkStatic("com.arflix.tv.util.DataStoresKt")
            directory.deleteRecursively()
        }
    }
}
