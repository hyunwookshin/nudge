package com.hyunwookshin.nudge

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.hyunwookshin.nudge.ReminderListFragment
import java.text.SimpleDateFormat
import java.util.Locale

class ReminderAdapter : RecyclerView.Adapter<ReminderAdapter.ReminderViewHolder>() {

    private var reminders: List<Reminder> = listOf()
    private var reminderCallback: ReminderCallback? = null

    fun convert24HourTo12Hour(time24: String): String {
        // Define the input format
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        // Define the output format
        val outputFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy hh:mm a", Locale.getDefault())

        // Parse the input time string
        val date = inputFormat.parse(time24)

        // Format the date object into the output format
        return if (date != null) {
            outputFormat.format(date)
        } else {
            ""
        }
    }

    fun setReminders(reminders: List<Reminder>) {
        this.reminders = reminders.sortedBy { it->it.Time }
        notifyDataSetChanged()
    }
    fun setReminderCallback(callback: ReminderCallback) {
        this.reminderCallback = callback
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
        private val read: TextView = itemView.findViewById(R.id.read)
        val editButton: Button = itemView.findViewById(R.id.editButton)

        fun bind(reminder: Reminder) {
            title.text = reminder.Title
            description.text = reminder.Description
            time.text = convert24HourTo12Hour(reminder.Time)
            if (reminder.Read.isEmpty()) {
                read.text = "(Not read)";
            } else {
                read.text = "Verified: " + convert24HourTo12Hour(reminder.Read)
            }
            val backgroundColor = if (reminder.Priority <= 1) {
                ContextCompat.getColor(itemView.context, R.color.high_priority)
            } else {
                ContextCompat.getColor(itemView.context, R.color.low_priority)
            }
            editButton.setOnClickListener {
                reminderCallback?.onEditReminder(reminder)
            }
            itemView.setBackgroundColor(backgroundColor)
        }
    }
}
