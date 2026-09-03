package tech.getapps.paraphrase

import android.animation.ValueAnimator
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.widget.TextView
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * The two text animations the landing demo needs: growing a selection
 * highlight, and retyping one sentence into another.
 */
class TextMorpher(private val view: TextView) {

    private var animator: ValueAnimator? = null

    fun cancel() {
        animator?.cancel()
        animator = null
    }

    /** Sweeps a selection highlight across [text] the way a drag-select looks. */
    fun select(text: String, color: Int, duration: Long = 480L, onEnd: () -> Unit = {}) {
        cancel()
        if (!Motion.enabled(view.context)) {
            view.text = highlight(text, text.length, color)
            onEnd()
            return
        }
        animator = ValueAnimator.ofInt(0, text.length).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { view.text = highlight(text, it.animatedValue as Int, color) }
            doOnEnd(onEnd)
            start()
        }
    }

    /**
     * Deletes back to the common prefix, then types the replacement — the same
     * shape as watching someone rewrite the sentence.
     */
    fun morph(from: String, to: String, onEnd: () -> Unit = {}) {
        cancel()
        if (!Motion.enabled(view.context)) {
            view.text = to
            onEnd()
            return
        }
        val shared = from.commonPrefixWith(to).length
        val deletions = from.length - shared
        val insertions = to.length - shared
        val steps = deletions + insertions

        animator = ValueAnimator.ofInt(0, steps).apply {
            duration = (steps * 11L).coerceIn(420L, 1_400L)
            addUpdateListener {
                val step = it.animatedValue as Int
                val body = if (step <= deletions) {
                    from.substring(0, from.length - step)
                } else {
                    to.substring(0, shared + (step - deletions))
                }
                // A caret while typing, dropped on the last frame.
                view.text = if (step == steps) body else "$body▏"
            }
            doOnEnd {
                view.text = to
                onEnd()
            }
            start()
        }
    }

    private fun highlight(text: String, end: Int, color: Int): CharSequence =
        SpannableString(text).apply {
            if (end > 0) {
                setSpan(BackgroundColorSpan(color), 0, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

    private fun ValueAnimator.doOnEnd(action: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: android.animation.Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (!cancelled) action()
            }
        })
    }
}
