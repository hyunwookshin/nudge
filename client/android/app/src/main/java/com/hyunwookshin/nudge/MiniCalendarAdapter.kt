package com.hyunwookshin.nudge

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.time.LocalDate

class MiniCalendarAdapter(
    private val onDayClick: ((LocalDate) -> Unit)? = null,
    private val onDayLongPress: ((LocalDate, View) -> Unit)? = null
) : RecyclerView.Adapter<MiniCalendarAdapter.VH>() {

    private var days: List<DayState> = emptyList()
    // project spans, drawn as bands by ProjectBandDecoration (earlier start first = drawn first)
    var bands: List<ProjectBand> = emptyList()
        private set

    fun setBands(bands: List<ProjectBand>) {
        if (bands == this.bands) return
        this.bands = bands
        notifyDataSetChanged()
    }

    fun dayDates(): List<LocalDate> = days.map { it.date }

    // date of the selected reminder card, ringed in that card's accent color
    private var selectedDate: LocalDate? = null
    private var selectedColor: Int = 0

    fun setSelected(date: LocalDate?, color: Int = 0) {
        if (date == selectedDate && color == selectedColor) return
        selectedDate = date
        selectedColor = color
        notifyDataSetChanged()
    }

    fun submit(days: List<DayState>) {
        this.days = days
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_mini_day, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = days.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(days[position], onDayClick, onDayLongPress,
            if (days[position].date == selectedDate) selectedColor else null)
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView.findViewById(R.id.dayCard)
        private val dayNumber: TextView = itemView.findViewById(R.id.dayNumber)
        private val dayCount: TextView = itemView.findViewById(R.id.dayCount)

        fun bind(state: DayState, onDayClick: ((LocalDate) -> Unit)?, onDayLongPress: ((LocalDate, View) -> Unit)?,
                 selectedRing: Int? = null) {
            val ctx = itemView.context
            val today = LocalDate.now()

            dayNumber.text = state.date.dayOfMonth.toString()
            // days of the neighbouring month are dimmed (number only) so month boundaries stand out
            dayNumber.alpha = if (state.inMajorityMonth) 1f else 0.5f

            // Count badge (only show if >= 1)
            if (state.count >= 1) {
                dayCount.visibility = View.VISIBLE
                dayCount.text = state.count.toString()
            } else {
                dayCount.visibility = View.GONE
            }

            // Fill color: red > gray > transparent (unfilled)
            val fillColor = when {
                state.hasHigh -> ContextCompat.getColor(ctx, R.color.high_priority) // your red
                state.hasLow -> ContextCompat.getColor(ctx, R.color.low_priority)  // your gray
                else -> android.graphics.Color.TRANSPARENT
            }
            card.setCardBackgroundColor(fillColor)

            // Stroke default
            val outline = resolveThemeColor(ctx, com.google.android.material.R.attr.colorOutline)
            card.strokeWidth = dp(ctx, 1)
            card.strokeColor = outline

            // Today highlight: black in light / white in dark (as you requested)
            if (state.date == today) {
                val night =
                    (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                            Configuration.UI_MODE_NIGHT_YES

                val todayStroke = if (night) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                card.strokeWidth = dp(ctx, 2)
                card.strokeColor = todayStroke
            }

            // Selected reminder's date: ring in the card's accent color (wins over today's ring)
            if (selectedRing != null) {
                card.strokeWidth = dp(ctx, 3)
                card.strokeColor = selectedRing
            }

            itemView.setOnClickListener { onDayClick?.invoke(state.date) }
            itemView.setOnLongClickListener {
                onDayLongPress?.invoke(state.date, itemView)
                true
            }
        }

        private fun dp(ctx: android.content.Context, v: Int): Int {
            return (v * ctx.resources.displayMetrics.density).toInt()
        }

        private fun resolveThemeColor(ctx: android.content.Context, attr: Int): Int {
            val tv = android.util.TypedValue()
            ctx.theme.resolveAttribute(attr, tv, true)
            return tv.data
        }
    }
}
