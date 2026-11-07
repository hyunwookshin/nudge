package com.hyunwookshin.nudge

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import java.util.Random
import kotlin.math.max

/**
 * "Pajama print" card background: small motifs (already tinted) scattered over the whole card on a
 * staggered grid, each with a little offset, its own size and a tilt. The layout is seeded, so a
 * card keeps the same arrangement every time it is drawn.
 */
class MotifPatternDrawable(
    private val motifs: List<Drawable>,
    private val seed: Long,
    private val density: Float,
    private val motifAlpha: Int,
) : Drawable() {

    override fun draw(canvas: Canvas) {
        if (motifs.isEmpty()) return
        val b = bounds
        val rnd = Random(seed)
        val cell = 48f * density              // distance between motifs
        val rowStep = cell * 0.82f
        canvas.save()
        canvas.clipRect(b)
        var row = 0
        var y = b.top + rowStep / 2
        while (y < b.bottom + rowStep) {
            var x = b.left + (if (row % 2 == 1) cell / 2 else 0f) + cell * 0.25f
            while (x < b.right + cell / 2) {
                val motif = motifs[rnd.nextInt(motifs.size)]
                val size = (16f + rnd.nextFloat() * 12f) * density      // longest side 16-28dp
                val k = size / max(motif.intrinsicWidth, motif.intrinsicHeight)
                val w = (motif.intrinsicWidth * k).toInt()
                val h = (motif.intrinsicHeight * k).toInt()
                val cx = x + (rnd.nextFloat() - 0.5f) * cell * 0.3f
                val cy = y + (rnd.nextFloat() - 0.5f) * rowStep * 0.3f
                canvas.save()
                canvas.translate(cx, cy)
                canvas.rotate(rnd.nextFloat() * 60f - 30f)
                motif.alpha = motifAlpha
                motif.setBounds(-w / 2, -h / 2, w - w / 2, h - h / 2)
                motif.draw(canvas)
                canvas.restore()
                x += cell
            }
            y += rowStep
            row++
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
