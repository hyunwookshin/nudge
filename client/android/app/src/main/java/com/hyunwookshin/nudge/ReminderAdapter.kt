package com.hyunwookshin.nudge

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import java.util.Calendar
import java.util.concurrent.TimeUnit
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class ReminderAdapter : RecyclerView.Adapter<ReminderAdapter.ReminderViewHolder>() {

    private var reminders: List<Reminder> = listOf()
    private var reminderCallback: ReminderCallback? = null
    // For updating the mini calendar
    private var onReminderClick: ((Reminder) -> Unit)? = null
    fun setOnReminderClick(listener: (Reminder) -> Unit) {
        onReminderClick = listener
    }

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
        this.reminders = reminders // already sorted
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

    private fun daysUntil(reminderTime: String): Long? {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val target = inputFormat.parse(reminderTime) ?: return null

        // Normalize both dates to local midnight so “days” feels human
        val nowCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetCal = Calendar.getInstance().apply {
            time = target
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffMs = targetCal.timeInMillis - nowCal.timeInMillis
        return TimeUnit.MILLISECONDS.toDays(diffMs)
    }

    inner class ReminderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardRoot: View = itemView.findViewById(R.id.cardRoot)
        private val title: TextView = itemView.findViewById(R.id.title)
        private val description: TextView = itemView.findViewById(R.id.description)
        private val time: TextView = itemView.findViewById(R.id.time)
        private val read: TextView = itemView.findViewById(R.id.read)
        private val countdown: TextView = itemView.findViewById(R.id.countdown)
        private val id: TextView = itemView.findViewById(R.id.id)
        private val editButton: ImageButton = itemView.findViewById(R.id.editButton)
        private val copyButton: ImageButton = itemView.findViewById(R.id.copyButton)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
        private val linkButton: ImageButton = itemView.findViewById(R.id.linkButton)

        fun bind(reminder: Reminder) {
            title.text = reminder.Title
            description.text = reminder.Description
            time.text = convert24HourTo12Hour(reminder.Time)
            val d = daysUntil(reminder.Time)
            if (d != null && d in 0..13) {
                countdown.visibility = View.VISIBLE
                countdown.text = if (d == 0L) "Today" else "In $d days"
            } else {
                countdown.visibility = View.GONE
                countdown.text = ""
            }
            id.text = reminder.Id
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
            copyButton.setOnClickListener {
                reminderCallback?.onCopyReminder(reminder)
            }
            deleteButton.setOnClickListener {
                reminderCallback?.onDeleteReminder(reminder)
            }
            val link = reminder.Link
            if (!link.isNullOrBlank()) {
                linkButton.visibility = View.VISIBLE
                linkButton.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    itemView.context.startActivity(intent)
                }
            } else {
                linkButton.visibility = View.GONE
                linkButton.setOnClickListener(null)
            }
            cardRoot.background?.mutate()?.setTint(backgroundColor)

            // For updating the mini calendar anchored to the reminder
            itemView.setOnClickListener {
                onReminderClick?.invoke(reminder)
            }
        }
    }
}
