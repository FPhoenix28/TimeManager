package com.example.timemanager.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.timemanager.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Host renders use the actual resource-inflated adaptive layers and their platform bounds. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconResourceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val output = File("build/reports/launcher-icon/api-${Build.VERSION.SDK_INT}")

    @Test fun manifestIconsResolveToApprovedAdaptiveArtwork() {
        assertEquals(R.mipmap.ic_launcher, context.applicationInfo.icon)
        for (resource in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            val drawable = context.getDrawable(resource)
            assertTrue(drawable is AdaptiveIconDrawable)
            val adaptive = drawable as AdaptiveIconDrawable
            assertNotNull(adaptive.background)
            if (Build.VERSION.SDK_INT >= 33) assertEquals(null, adaptive.monochrome)
            val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            adaptive.setBounds(0, 0, 256, 256)
            adaptive.draw(Canvas(bitmap))
            assertEquals(255, Color.alpha(bitmap.getPixel(128, 128)))
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
            save(bitmap, if (resource == R.mipmap.ic_launcher) "platform-default" else "platform-round")
            bitmap.recycle()
        }
    }

    @Test fun renderRealLayersUnderLauncherMasksAndAt48Dp() {
        val board = Bitmap.createBitmap(1120, 1000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(board)
        canvas.drawColor(Color.rgb(29, 27, 37))
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(235, 231, 240)
            textSize = 22f
        }
        canvas.drawText("Approved artwork / actual Android resource layers", 36f, 40f, text)
        canvas.drawText("256px masks + 48dp at 2x density (96px, shown at native size)", 36f, 74f, text)
        for ((index, mask) in listOf("circle", "rounded-square", "squircle").withIndex()) {
            val large = renderMask(mask, 256)
            save(large, mask)
            val x = 40f + index * 360f
            canvas.drawText(mask, x, 118f, text)
            canvas.drawBitmap(large, x, 144f, null)
            large.recycle()
            val small = renderMask(mask, 96)
            save(small, "${mask}-48dp")
            canvas.drawBitmap(small, x + 80f, 444f, null)
            val darkSample = Bitmap.createBitmap(256, 180, Bitmap.Config.ARGB_8888)
            val sampleCanvas = Canvas(darkSample)
            sampleCanvas.drawColor(Color.rgb(244, 243, 248))
            sampleCanvas.drawBitmap(small, 80f, 16f, null)
            text.color = Color.rgb(36, 32, 42)
            sampleCanvas.drawText("TimeManager", 54f, 148f, text)
            canvas.drawBitmap(darkSample, x, 650f, null)
            darkSample.recycle()
            text.color = Color.rgb(235, 231, 240)
            canvas.drawText("TimeManager", x + 54f, 576f, text)
            small.recycle()
        }
        canvas.drawText("Mask simulations on dark / light surfaces. No device screenshot.", 36f, 942f, text)
        save(board, "launcher-mask-preview")
        board.recycle()
    }

    private fun renderMask(name: String, size: Int): Bitmap {
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        icon.setBounds(0, 0, size, size)
        val edge = size.toFloat()
        val mask = Path().apply {
            when (name) {
                "circle" -> addCircle(edge / 2, edge / 2, edge / 2, Path.Direction.CW)
                "rounded-square" -> addRoundRect(RectF(0f, 0f, edge, edge),
                    edge * 0.22f, edge * 0.22f, Path.Direction.CW)
                else -> {
                    moveTo(edge * 0.5f, 0f)
                    cubicTo(edge * 0.93f, 0f, edge, edge * 0.07f, edge, edge * 0.5f)
                    cubicTo(edge, edge * 0.93f, edge * 0.93f, edge, edge * 0.5f, edge)
                    cubicTo(edge * 0.07f, edge, 0f, edge * 0.93f, 0f, edge * 0.5f)
                    cubicTo(0f, edge * 0.07f, edge * 0.07f, 0f, edge * 0.5f, 0f)
                    close()
                }
            }
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.clipPath(mask)
        // setBounds above applies Android's 1.5x layer expansion before this launcher clip.
        icon.background.draw(canvas)
        icon.foreground?.draw(canvas)
        assertEquals(255, Color.alpha(bitmap.getPixel(size / 2, size / 2)))
        return bitmap
    }

    private fun save(bitmap: Bitmap, name: String) {
        output.mkdirs()
        File(output, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
