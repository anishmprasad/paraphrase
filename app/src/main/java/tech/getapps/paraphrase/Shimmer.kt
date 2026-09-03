package tech.getapps.paraphrase

import android.animation.ValueAnimator
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.core.graphics.ColorUtils

/**
 * Sweeps a highlight across a TextView's own glyphs, so the text the model is
 * working on looks alive instead of frozen. Cheap: one shader on the existing
 * paint, no extra views.
 */
class Shimmer(private val view: TextView) {

    private var animator: ValueAnimator? = null

    fun start() {
        if (!Motion.enabled(view.context) || view.width == 0) return
        stop()

        val base = view.currentTextColor
        val highlight = ColorUtils.blendARGB(base, view.context.brandColor(), 0.9f)
        val width = view.width.toFloat()

        val gradient = LinearGradient(
            0f, 0f, width * 0.4f, 0f,
            intArrayOf(base, highlight, base),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        view.paint.shader = gradient
        val matrix = Matrix()

        animator = ValueAnimator.ofFloat(-width * 0.4f, width * 1.4f).apply {
            duration = 1_200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                matrix.setTranslate(it.animatedValue as Float, 0f)
                gradient.setLocalMatrix(matrix)
                view.invalidate()
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
        view.paint.shader = null
        view.invalidate()
    }

    private fun android.content.Context.brandColor(): Int {
        val typed = android.util.TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typed, true)
        return typed.data
    }
}
