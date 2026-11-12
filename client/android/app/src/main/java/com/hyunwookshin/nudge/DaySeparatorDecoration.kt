package com.hyunwookshin.nudge

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A thin "──── Mon 11/12 ────" rule above the first event of each day. Drawn only; not a list item. */
class DaySeparatorDecoration(context: Context, private val adapter: ReminderAdapter) : RecyclerView.ItemDecoration() {

    private val density = context.resources.displayMetrics.density
    private val height = (22 * density).toInt()
    private val sideInset = 24 * density
    private val textGap = 8 * density
    private val fmt = DateTimeFormatter.ofPattern("EEE M/d", Locale.getDefault())
    private val textColor = ContextCompat.getColor(context, R.color.colorTextSecondary)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, context.resources.displayMetrics)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(textColor, 0x59)
        strokeWidth = density
    }

    private fun dayAt(position: Int): LocalDate? =
        adapter.reminderAt(position)?.let { runCatching { LocalDate.parse(it.Time.take(10)) }.getOrNull() }

    private fun startsDay(position: Int): LocalDate? {
        val day = dayAt(position) ?: return null
        return if (position == 0 || dayAt(position - 1) != day) day else null
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val pos = parent.getChildAdapterPosition(view)
        if (pos != RecyclerView.NO_POSITION && startsDay(pos) != null) outRect.top = height
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val left = parent.paddingLeft + sideInset
        val right = parent.width - parent.paddingRight - sideInset
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(child)
            if (pos == RecyclerView.NO_POSITION) continue
            val day = startsDay(pos) ?: continue
            val topMargin = (child.layoutParams as RecyclerView.LayoutParams).topMargin
            val centerY = child.top + child.translationY - topMargin - height / 2f
            val label = day.format(fmt)
            val half = textPaint.measureText(label) / 2 + textGap
            val mid = (left + right) / 2
            textPaint.alpha = (child.alpha * 255).toInt()
            linePaint.alpha = (child.alpha * 0x59).toInt()
            c.drawLine(left, centerY, mid - half, centerY, linePaint)
            c.drawLine(mid + half, centerY, right, centerY, linePaint)
            c.drawText(label, mid, centerY - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)
        }
    }
}
