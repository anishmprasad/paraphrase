package tech.getapps.paraphrase

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Slow drifting colour blobs behind the landing content. Three radial
 * gradients on Lissajous paths — cheap enough to run at 60fps, and it reads as
 * movement without competing with the text.
 *
 * Colours come from the day/night palette, so the same view is pastel on white
 * and deep on black.
 */
class AuroraView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private class Blob(val color: Int, val speed: Float, val phase: Float, val radius: Float)

    private val blobs = listOf(
        Blob(color(R.color.aurora_1), 1.00f, 0.00f, 0.85f),
        Blob(color(R.color.aurora_2), 0.72f, 0.33f, 0.70f),
        Blob(color(R.color.aurora_3), 0.55f, 0.66f, 0.60f)
    )
    private val paints = blobs.map { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val radii = FloatArray(blobs.size)

    private val alpha = resources.getInteger(R.integer.aurora_alpha)
    private var progress = 0f
    private var parallax = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 28_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    private fun color(id: Int) = ContextCompat.getColor(context, id)

    /** Drifts the blobs against the scroll for a bit of depth. */
    fun setParallax(scrollY: Int) {
        parallax = -scrollY * 0.25f
        invalidate()
    }

    fun play() {
        if (!Motion.enabled(context)) {
            progress = 0.12f
            invalidate()
            return
        }
        if (!animator.isStarted) animator.start() else animator.resume()
    }

    fun pause() {
        if (animator.isStarted) animator.pause()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val base = min(w, h).toFloat()
        blobs.forEachIndexed { index, blob ->
            val radius = base * blob.radius
            radii[index] = radius
            paints[index].shader = RadialGradient(
                0f, 0f, radius,
                intArrayOf(
                    Color.argb(alpha, Color.red(blob.color), Color.green(blob.color), Color.blue(blob.color)),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        blobs.forEachIndexed { index, blob ->
            val t = (progress * blob.speed + blob.phase) * 2f * Math.PI.toFloat()
            val cx = w * (0.5f + 0.42f * sin(t))
            val cy = h * (0.32f + 0.30f * cos(t * 1.3f)) + parallax * (index + 1)
            canvas.save()
            canvas.translate(cx, cy)
            canvas.drawCircle(0f, 0f, radii[index], paints[index])
            canvas.restore()
        }
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}
