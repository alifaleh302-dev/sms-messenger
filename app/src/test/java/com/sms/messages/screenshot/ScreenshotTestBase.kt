package com.sms.messages.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.sms.messages.data.ThemeMode
import com.sms.messages.ui.theme.SmsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.robolectric.shadows.ShadowLooper
import java.io.File

/**
 * Renders a composable to a real PNG on the JVM.
 *
 * Robolectric's native graphics mode draws through the same Skia the device uses, so the
 * output is the actual layout rather than a description of it. That makes these shots useful
 * for reviewing alignment, right-to-left mirroring and colour, none of which an assertion-only
 * test can see.
 *
 * The locale comes from the test's `qualifiers`; light and dark are both captured from one
 * test body because a compose rule only accepts a single `setContent` per test.
 */
abstract class ScreenshotTestBase {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Set before each capture so the theme can be switched without a second `setContent`. */
    private var dark by mutableStateOf(false)

    /**
     * Renders [content] in light and dark and writes two PNGs.
     *
     * [ready] guards the capture: the screens read from a database-backed flow, so the first
     * frame is the empty state and capturing it would hide every layout problem.
     */
    protected fun shootBoth(
        name: String,
        ready: (() -> Boolean)? = null,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            SmsTheme(
                themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
                // The app's own palette is what is under review; dynamic colour would
                // replace it with whatever colour the host device happened to pick.
                dynamicColor = false,
                content = content,
            )
        }
        shoot("$name-light", ready)
        dark = true
        shoot("$name-dark", ready)
    }

    /** Renders [content] once, in light. */
    protected fun shootOne(name: String, ready: (() -> Boolean)? = null, content: @Composable () -> Unit) {
        compose.setContent {
            SmsTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false, content = content)
        }
        shoot(name, ready)
    }

    private fun shoot(name: String, ready: (() -> Boolean)?) {
        compose.waitForIdle()
        if (ready != null) compose.waitUntil(timeoutMillis = 15_000) { ready() }

        val view: View = compose.activity.window.decorView
        ShadowLooper.idleMainLooper()
        if (view.width == 0 || view.height == 0) {
            val metrics = view.resources.displayMetrics
            val w = metrics.widthPixels.coerceAtLeast(1)
            val h = metrics.heightPixels.coerceAtLeast(1)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, w, h)
        }
        val bitmap = Bitmap.createBitmap(
            view.width.coerceAtLeast(1),
            view.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        view.draw(Canvas(bitmap))
        // Both locales run the same test bodies, so the file name carries the locale or the
        // second run would silently overwrite the first.
        Output.write("${locale()}-$name", bitmap)
        assertRendered(bitmap, "${locale()}-$name")
    }

    /** A shot that came out blank is a broken test, not a design finding. */
    private fun assertRendered(bitmap: Bitmap, name: String) {
        val distinct = mutableSetOf<Int>()
        var y = 0
        while (y < bitmap.height && distinct.size < 12) {
            var x = 0
            while (x < bitmap.width) {
                distinct += bitmap.getPixel(x, y)
                x += 11
            }
            y += 11
        }
        assertTrue(
            "$name rendered only ${distinct.size} distinct colours — content did not draw",
            distinct.size >= 3,
        )
    }

    /** "ar" or "en", taken from the activity so it matches what the shot actually rendered. */
    private fun locale(): String =
        compose.activity.resources.configuration.locales[0].language

    /** The bounding box of everything that is not the background, in pixels. */
    protected fun contentBounds(bitmap: Bitmap): IntArray {
        val background = bitmap.getPixel(0, bitmap.height - 1)
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                if (bitmap.getPixel(x, y) != background) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
                x += 2
            }
            y += 2
        }
        return intArrayOf(left, top, right, bottom)
    }

    companion object {
        /** 411x891dp at xxhdpi is a Pixel-class phone. */
        const val ENGLISH = "w411dp-h891dp-xxhdpi"
        const val ARABIC = "ar-rSA-w411dp-h891dp-xxhdpi"
    }
}

/** Where the shots land. The path is stable so a reviewer can open the folder directly. */
object Output {
    val directory: File = File("build/screenshots").apply { mkdirs() }

    fun write(name: String, bitmap: Bitmap) {
        val file = File(directory, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SHOT ${file.name} ${bitmap.width}x${bitmap.height}")
    }
}
