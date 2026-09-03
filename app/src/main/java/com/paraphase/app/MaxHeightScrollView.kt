package com.paraphase.app

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

/**
 * A ScrollView that stops growing past [maxHeight] so a long paraphrase scrolls
 * inside the popup instead of pushing the buttons off screen.
 */
class MaxHeightScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    private var maxHeight: Int = (240 * resources.displayMetrics.density).toInt()

    init {
        attrs?.let {
            val a = context.obtainStyledAttributes(it, R.styleable.MaxHeightScrollView)
            maxHeight = a.getDimensionPixelSize(R.styleable.MaxHeightScrollView_maxHeight, maxHeight)
            a.recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val capped = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, capped)
    }
}
