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
    private val onDayClick: ((LocalDate) -> Unit)? = null
) : RecyclerView.Adapter<MiniCalendarAdapter.VH>() {

    private var days: List<DayState> = emptyList()

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
        holder.bind(days[position], onDayClick)
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView.findViewById(R.id.dayCard)
        private val dayNumber: TextView = itemView.findViewById(R.id.dayNumber)
        private val dayCount: TextView = itemView.findViewById(R.id.dayCount)

        fun bind(state: DayState, onDayClick: ((LocalDate) -> Unit)?) {
            val ctx = itemView.context
            val today = LocalDate.now()

            dayNumber.text = state.date.dayOfMonth.toString()

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

            itemView.setOnClickListener { onDayClick?.invoke(state.date) }
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
