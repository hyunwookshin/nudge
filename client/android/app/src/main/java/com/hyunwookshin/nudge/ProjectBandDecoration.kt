package com.hyunwookshin.nudge

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors

/**
 * Draws each project as one continuous band along the bottom of its days in the mini calendar. A
 * band that runs past the end of a week continues on the next row like a snake. The ends are
 * rounded only where the project really starts and ends, so overlapping projects can be told apart
 * by their caps; where they overlap, the one that started later is drawn on top, and every band has
 * a thin outline in the surface color to keep its edges visible even when two share a color.
 */
class ProjectBandDecoration(context: Context, private val adapter: MiniCalendarAdapter) : RecyclerView.ItemDecoration() {

    private val density = context.resources.displayMetrics.density
    private val thickness = 5f * density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }
    private val path = Path()
    private val rect = RectF()

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val days = adapter.dayDates()
        val bands = adapter.bands
        if (days.isEmpty() || bands.isEmpty()) return
        outline.color = MaterialColors.getColor(parent, com.google.android.material.R.attr.colorSurface)
        val layoutManager = parent.layoutManager ?: return

        for (band in bands) {
            fill.color = band.color
            for (segment in MiniCalendarMath.bandSegments(days, band.start, band.end)) {
                val first = segment.row * 7
                val left = layoutManager.findViewByPosition(first + segment.firstCol) ?: continue
                val right = layoutManager.findViewByPosition(first + segment.lastCol) ?: continue

                val startsHere = segment.startsHere
                val endsHere = segment.endsHere
                // where the band wraps to the neighbouring row it runs flat to the edge of the grid
                val bleed = 4f * density
                rect.set(
                    left.left - (if (startsHere) 0f else bleed),
                    left.bottom - 5f * density - thickness,
                    right.right + (if (endsHere) 0f else bleed),
                    left.bottom - 5f * density,
                )
                val rl = if (startsHere) thickness / 2 else 0f
                val rr = if (endsHere) thickness / 2 else 0f
                path.reset()
                path.addRoundRect(rect, floatArrayOf(rl, rl, rr, rr, rr, rr, rl, rl), Path.Direction.CW)
                c.drawPath(path, outline)
                c.drawPath(path, fill)
            }
        }
    }
}
