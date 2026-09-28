package com.arflix.tv.ui.screens.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.arflix.tv.R
import com.arflix.tv.di.RepositoryAccessEntryPoint
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.LocalDeviceType
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Requests a code only: never logs out, approves a login, or changes the current account. */
@OptIn(ExperimentalTestApi::class)
class TraktActivationDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun liveCodeProducesScannableQrAndRemoteCanDismiss() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("traktLive") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val repository = EntryPointAccessors.fromApplication(context, RepositoryAccessEntryPoint::class.java)
            .traktRepository()
        val started = android.os.SystemClock.elapsedRealtime()
        val code = repository.getDeviceCode()
        val elapsed = android.os.SystemClock.elapsedRealtime() - started
        assertTrue("Activation request must finish within its deadline", elapsed < 21_000)
        assertTrue(code.userCode.isNotBlank())
        val payload = traktActivationUrl(code.verificationUrl, code.userCode)
        var dismissed = false
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides DeviceType.TV) {
                TraktActivationModal(code.verificationUrl, code.userCode,
                    onDismiss = { dismissed = true }, qrData = payload,
                    expiresAtMillis = System.currentTimeMillis() + code.expiresIn * 1000L)
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(code.userCode).assertIsDisplayed()
        val qr = compose.onNodeWithContentDescription(context.getString(R.string.component_qr_code))
        qr.assertIsDisplayed()
        val bitmap = qr.captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(
            RGBLuminanceSource(bitmap.width, bitmap.height, pixels))))
        assertEquals(payload, decoded.text)
        compose.onNode(isDialog()).performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertTrue("TV remote must have working dialog focus", dismissed) }
        instrumentation.sendStatus(0, android.os.Bundle().apply {
            putString("stream", "Live Trakt code returned in ${elapsed}ms; QR decoded correctly; remote dismissal passed.\n")
        })
    }
}
