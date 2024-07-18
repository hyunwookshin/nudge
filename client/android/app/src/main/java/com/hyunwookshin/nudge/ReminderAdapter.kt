package com.hyunwookshin.nudge

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.hyunwookshin.nudge.ReminderListFragment

class ReminderAdapter : RecyclerView.Adapter<ReminderAdapter.ReminderViewHolder>() {

    private var reminders: List<Reminder> = listOf()

    fun setReminders(reminders: List<Reminder>) {
        this.reminders = reminders.sortedBy { it->it.Time }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false)
        return ReminderViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        holder.bind(reminders[position])
    }

    override fun getItemCount(): Int = reminders.size

    inner class ReminderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.title)
        private val description: TextView = itemView.findViewById(R.id.description)
        private val time: TextView = itemView.findViewById(R.id.time)

        fun bind(reminder: Reminder) {
            title.text = reminder.Title
            description.text = reminder.Description
            time.text = reminder.Time

            val backgroundColor = if (reminder.Priority <= 1) {
                ContextCompat.getColor(itemView.context, R.color.high_priority)
            } else {
                ContextCompat.getColor(itemView.context, R.color.low_priority)
            }
            itemView.setBackgroundColor(backgroundColor)
        }
    }
}
