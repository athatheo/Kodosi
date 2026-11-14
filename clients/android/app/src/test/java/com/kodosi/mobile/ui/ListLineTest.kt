package com.kodosi.mobile.ui

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ListLineTest {
    @Test
    fun aLineInAMenuThatTakesTheWidthOfItsWidestRowHasASize() {
        var measured = IntSize.Zero
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            KodosiTheme {
                Column(Modifier.width(IntrinsicSize.Max)) {
                    ListLine(
                        "Model",
                        Modifier.onSizeChanged { measured = it },
                        subtitle = "The agent of this terminal",
                        leading = { Box(Modifier.size(20.dp)) }
                    ) { Text("Opus") }
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("$measured", measured.width > 0 && measured.height > 0)
    }
}
