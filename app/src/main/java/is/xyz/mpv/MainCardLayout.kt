package `is`.xyz.mpv

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import kotlin.math.max

/**
 * A [LinearLayout] used for the action cards on the home screen.
 * With `app:mainCardSquare="true"` the card is at least as tall as it is wide
 * (it grows taller if its content needs the room, e.g. with large font sizes).
 */
class MainCardLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private val square: Boolean

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.MainCardLayout, defStyleAttr, 0)
        square = a.getBoolean(R.styleable.MainCardLayout_mainCardSquare, false)
        a.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (square) {
            val w = measuredWidth
            setMeasuredDimension(w, max(w, measuredHeight))
        }
    }
}
