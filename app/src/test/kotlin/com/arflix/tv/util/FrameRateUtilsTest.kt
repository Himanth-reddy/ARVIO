package com.arflix.tv.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.View
import android.view.Window
import android.view.WindowManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 30], application = Application::class, manifest = Config.NONE,
    shadows = [FrameRateUtilsTest.SimulatedDisplay::class])
@ConscryptMode(ConscryptMode.Mode.OFF)
@OptIn(ExperimentalCoroutinesApi::class)
class FrameRateUtilsTest {
    @Implements(Display::class)
    class SimulatedDisplay {
        var active = mode(1, 60f)
        var modes = arrayOf(active, mode(2, 24f), mode(3, 24000f / 1001f), mode(4, 50f))
        @Implementation fun getMode(): Display.Mode = active
        @Implementation fun getSupportedModes(): Array<Display.Mode> = modes
    }

    private class Screen(initialPreference: Int = 0) {
        val activity = mockk<Activity>()
        val window = mockk<Window>()
        val display = (RuntimeEnvironment.getApplication().getSystemService(Context.DISPLAY_SERVICE)
            as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        val simulation = Shadow.extract<SimulatedDisplay>(display)
        val decor = mockk<View>()
        var attributes = WindowManager.LayoutParams().apply {
            preferredDisplayModeId = initialPreference
        }
        var active: Display.Mode
            get() = simulation.active
            set(value) { simulation.active = value }
        var modes: Array<Display.Mode>
            get() = simulation.modes
            set(value) { simulation.modes = value }

        init {
            every { activity.window } returns window
            every { window.decorView } returns decor
            every { decor.display } returns display
            every { window.attributes } answers { attributes }
            every { window.attributes = any() } answers { attributes = firstArg() }
        }
    }

    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }

    @After fun cleanUp() {
        FrameRateUtils.clearOriginalMode()
        Dispatchers.resetMain()
    }

    @Test fun pendingHdmiSwitchIsNotRepeated() {
        val screen = Screen()
        repeat(10) { assertTrue(FrameRateUtils.applyFrameRateMode(screen.activity, 24f)) }
        assertEquals(2, screen.attributes.preferredDisplayModeId)
        verify(exactly = 1) { screen.window.attributes = any() }
    }

    @Test fun changedSourceCancelsAnIncompatiblePendingSwitch() {
        val screen = Screen()
        FrameRateUtils.applyFrameRateMode(screen.activity, 24f)
        assertTrue(FrameRateUtils.applyFrameRateMode(screen.activity, 30f))
        assertEquals(1, screen.attributes.preferredDisplayModeId)
    }

    @Test fun compatibleActiveModeNeedsNoSwitchOrSurfaceFallback() {
        val screen = Screen()
        assertTrue(FrameRateUtils.applyFrameRateMode(screen.activity, 30f))
        verify(exactly = 0) { screen.window.attributes = any() }
    }

    @Test fun fractionalContentSelectsFractionalMode() {
        val screen = Screen()
        assertTrue(FrameRateUtils.applyFrameRateMode(screen.activity, 24000f / 1001f))
        assertEquals(3, screen.attributes.preferredDisplayModeId)
    }

    @Test fun neverChangesResolutionToFindARefreshRate() {
        val screen = Screen()
        screen.modes = arrayOf(screen.active, mode(2, 24f, 3840, 2160))
        assertFalse(FrameRateUtils.applyFrameRateMode(screen.activity, 24f))
        verify(exactly = 0) { screen.window.attributes = any() }
    }

    @Test fun unsupportedNewRateReleasesPreviousPreference() {
        val screen = Screen()
        FrameRateUtils.applyFrameRateMode(screen.activity, 24f)
        assertFalse(FrameRateUtils.applyFrameRateMode(screen.activity, 27f))
        assertEquals(0, screen.attributes.preferredDisplayModeId)
    }

    @Test fun restoresEachWindowOwnOriginalPreference() {
        val first = Screen()
        val second = Screen(initialPreference = 4)
        FrameRateUtils.applyFrameRateMode(first.activity, 24f)
        FrameRateUtils.applyFrameRateMode(second.activity, 24000f / 1001f)
        FrameRateUtils.restoreOriginalMode(second.activity)
        assertEquals(4, second.attributes.preferredDisplayModeId)
        assertEquals(2, first.attributes.preferredDisplayModeId)
        FrameRateUtils.restoreOriginalMode(first.activity)
        assertEquals(0, first.attributes.preferredDisplayModeId)
        FrameRateUtils.restoreOriginalMode(first.activity)
        verify(exactly = 2) { first.window.attributes = any() }
    }

    @Test fun rejectedRequestDoesNotCrashPlayback() {
        val screen = Screen()
        every { screen.window.attributes = any() } throws IllegalArgumentException("Mode unavailable")
        assertFalse(FrameRateUtils.applyFrameRateMode(screen.activity, 24f))
    }

    @Test fun confirmsDelayedHdmiSwitchWithoutRepeatingTheRequest() = runTest {
        val screen = Screen()
        launch { delay(600); screen.active = screen.modes[1] }
        assertTrue(FrameRateUtils.matchFrameRateAndWait(screen.activity, 24f))
        verify(exactly = 1) { screen.window.attributes = any() }
    }

    @Test fun ignoredModeRequestTimesOutAndRestoresPreference() = runTest {
        val screen = Screen()
        assertFalse(FrameRateUtils.matchFrameRateAndWait(screen.activity, 24f))
        assertEquals(0, screen.attributes.preferredDisplayModeId)
        assertEquals(4000L, testScheduler.currentTime)
    }

    @Test fun integerRateIsNotFalseConfirmationOfFractionalSwitch() = runTest {
        val screen = Screen()
        screen.active = screen.modes[1]
        assertFalse(FrameRateUtils.matchFrameRateAndWait(screen.activity, 24000f / 1001f))
        assertEquals(0, screen.attributes.preferredDisplayModeId)
    }

    @Test fun uhdFilmSwitchesWhenOnlyIntegerFilmModeIsAdvertised() = runTest {
        val screen = Screen()
        screen.active = mode(1, 60f, 3840, 2160)
        screen.modes = arrayOf(screen.active, mode(2, 24f, 3840, 2160))
        launch { delay(2000); screen.active = screen.modes[1] }
        assertTrue(FrameRateUtils.matchFrameRateAndWait(screen.activity, 24000f / 1001f))
        assertEquals(2, screen.attributes.preferredDisplayModeId)
        verify(exactly = 1) { screen.window.attributes = any() }
    }

    @Test fun ignoredIntegerFallbackIsNotReportedAsSuccess() = runTest {
        val screen = Screen()
        screen.modes = arrayOf(screen.active, screen.modes[1])
        assertFalse(FrameRateUtils.matchFrameRateAndWait(screen.activity, 24000f / 1001f))
        assertEquals(0, screen.attributes.preferredDisplayModeId)
    }

    @Test fun lowerResolutionFallbackIsStillExcluded() {
        val screen = Screen()
        screen.active = mode(1, 60f, 3840, 2160)
        screen.modes = arrayOf(screen.active, mode(2, 24f))
        assertFalse(FrameRateUtils.applyFrameRateMode(screen.activity, 24000f / 1001f))
        verify(exactly = 0) { screen.window.attributes = any() }
    }

    companion object {
        private fun mode(id: Int, rate: Float, width: Int = 1920, height: Int = 1080): Display.Mode =
            Display.Mode::class.java.getDeclaredConstructor(
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Float::class.javaPrimitiveType,
            ).newInstance(id, width, height, rate)
    }
}
