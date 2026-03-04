package com.hyunwookshin.nudge

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import java.util.Calendar
import java.util.concurrent.TimeUnit
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

    //offline mode
    private var readOnly = false

    fun setReadOnly(readOnly: Boolean) {
        this.readOnly = readOnly
        notifyDataSetChanged()
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
        holder.bind(reminders[position], readOnly)
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

        fun bind(reminder: Reminder, readOnly: Boolean) {
            title.text = reminder.Title
            description.text = reminder.Description
            time.text = convert24HourTo12Hour(reminder.Time)
            val d = daysUntil(reminder.Time)
            if (d != null && d in 0..13) {
                countdown.visibility = View.VISIBLE
                if (d == 0L) {
                    countdown.text = "Today"
                } else if (d == 1L) {
                    countdown.text = "Tomorrow"
                } else {
                    countdown.text = "In $d days"
                }
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
            val isVirtual = reminder.Id.matches(Regex(".*_r\\d+$"))
            if (readOnly) {
                editButton.visibility = View.GONE
                copyButton.visibility = View.GONE
                deleteButton.visibility = View.GONE
            } else {
                editButton.visibility = if (isVirtual) View.GONE else View.VISIBLE
                copyButton.visibility = View.VISIBLE
                deleteButton.visibility = View.VISIBLE
            }
            editButton.setOnClickListener {
                reminderCallback?.onEditReminder(reminder)
            }
            copyButton.setOnClickListener {
                reminderCallback?.onCopyReminder(reminder)
            }
            deleteButton.setOnClickListener {
                val title: String
                val message: String
                val reminderToDelete: Reminder
                if (isVirtual) {
                    val baseId = reminder.Id.replace(Regex("_r\\d+$"), "")
                    reminderToDelete = reminder.copy(Id = baseId)
                    title = "Delete recurring series?"
                    message = "This will remove all occurrences of this reminder. This can’t be undone."
                } else {
                    reminderToDelete = reminder
                    title = "Delete reminder?"
                    message = "This can’t be undone."
                }
                val dialog = MaterialAlertDialogBuilder(itemView.context, R.style.RoundedMaterialDialog)
                    .setTitle(title)
                    .setMessage(message)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        reminderCallback?.onDeleteReminder(reminderToDelete)
                    }
                    .show()
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(android.graphics.Color.parseColor("#E53935"))
                dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(ContextCompat.getColor(itemView.context, R.color.colorTextPrimary))
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
            val isDarkMode = (itemView.context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val strokeColor = adjustBrightness(backgroundColor, isDarkMode)
            val density = itemView.context.resources.displayMetrics.density
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f * density
                setColor(backgroundColor)
                setStroke((density + 0.5f).toInt(), strokeColor)
            }
            cardRoot.background = drawable

            // For updating the mini calendar anchored to the reminder
            itemView.setOnClickListener {
                onReminderClick?.invoke(reminder)
            }

            itemView.setOnLongClickListener {
                copyReminderToClipboard(itemView.context, reminder)
                true
            }
        }
    }

    private fun adjustBrightness(color: Int, lighten: Boolean): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return if (lighten) {
            val factor = 0.30f
            Color.rgb(
                (r + (255 - r) * factor).toInt(),
                (g + (255 - g) * factor).toInt(),
                (b + (255 - b) * factor).toInt()
            )
        } else {
            val factor = 0.20f
            Color.rgb(
                (r * (1f - factor)).toInt(),
                (g * (1f - factor)).toInt(),
                (b * (1f - factor)).toInt()
            )
        }
    }

    private fun copyReminderToClipboard(context: Context, r: Reminder) {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

        val displayTime = convert24HourTo12Hour(r.Time) // matches card
        val link = r.Link.orEmpty()

        val text = buildString {
            appendLine(r.Title)
            appendLine(r.Description)
            appendLine(displayTime)
            if (link.isNotBlank()) appendLine(link)
        }.trim()

        val clip = android.content.ClipData.newPlainText("Reminder", text)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(context, "Copied to Clipboard", Toast.LENGTH_SHORT).show()
    }
}
