package com.hyunwookshin.nudge

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import java.util.Calendar
import java.util.concurrent.TimeUnit
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Locale

class ReminderAdapter : RecyclerView.Adapter<ReminderAdapter.ReminderViewHolder>() {

    private var reminders: List<Reminder> = listOf()
    private var reminderCallback: ReminderCallback? = null
    // For updating the mini calendar
    private var onReminderClick: ((Reminder) -> Unit)? = null
    // tapped card: drawn with a thicker accent bar
    private var selectedId: String? = null

    /**
     * LIST: the main list (projects are minimal cards, member events indented).
     * PROJECTS: the Projects page (full project cards).
     * PROJECT_EVENTS: one project's page (plain event cards, nothing indented).
     */
    enum class Mode { LIST, PROJECTS, PROJECT_EVENTS }
    var mode: Mode = Mode.LIST

    // id -> title of every known project, for the tag on events that belong to one
    private var projectTitles: Map<String, String> = emptyMap()
    fun setProjectTitles(titles: Map<String, String>) { projectTitles = titles }

    /** A project card was tapped: in the main list it opens the Projects page, there it opens the project. */
    var onProjectClick: ((Reminder) -> Unit)? = null
    /** The "+" on an event: add it to a project. */
    var onAddToProject: ((Reminder) -> Unit)? = null

    /** Highlights a card (thick accent bar), e.g. the project the user came from. */
    fun select(id: String?) {
        val previous = reminders.indexOfFirst { it.Id == selectedId }
        selectedId = id
        if (previous >= 0) notifyItemChanged(previous)
        val now = reminders.indexOfFirst { it.Id == id }
        if (now >= 0) notifyItemChanged(now)
    }
    // Template.matchKey() of every template, for the [T] badge
    private var templateKeys: Set<String> = emptySet()

    // told when the selected card goes away (e.g. swiped), so the calendar ring can go too
    var onSelectionCleared: (() -> Unit)? = null

    /** Un-highlights the tapped card (tap elsewhere). */
    fun clearSelection() {
        val previous = reminders.indexOfFirst { it.Id == selectedId }
        selectedId = null
        if (previous >= 0) notifyItemChanged(previous)
    }

    fun setTemplateKeys(keys: Set<String>) {
        if (keys == templateKeys) return
        templateKeys = keys
        notifyDataSetChanged()
    }
    fun setOnReminderClick(listener: (Reminder) -> Unit) {
        onReminderClick = listener
    }

    private var readOnly = false
    private var isOfflineMode = false

    fun setReadOnly(readOnly: Boolean) {
        this.readOnly = readOnly
        notifyDataSetChanged()
    }

    fun setOfflineMode(offline: Boolean) {
        this.isOfflineMode = offline
        this.readOnly = false
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

    private fun compactTime(time24: String): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(time24) ?: return ""
        val sameYear = Calendar.getInstance().get(Calendar.YEAR) ==
            Calendar.getInstance().apply { time = date }.get(Calendar.YEAR)
        val pattern = if (sameYear) "EEE, MMM d · h:mm a" else "EEE, MMM d, yyyy · h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
    }

    /** "Nov 10 – Nov 14" (with the year when it isn't this year) for a project's chip. */
    private fun projectRange(r: Reminder): String {
        val start = runCatching { java.time.LocalDate.parse(r.Time.take(10)) }.getOrNull()
        val end = runCatching { java.time.LocalDate.parse(r.EndDate) }.getOrNull()
        if (start == null || end == null) return compactTime(r.Time)
        return ProjectInfo.rangeLabel(start, end, java.time.LocalDate.now())
    }

    fun setReminders(reminders: List<Reminder>) {
        this.reminders = reminders // already sorted
        notifyDataSetChanged()
    }
    /**
     * Swipe a card left or right to delete it (replaces the trash button; the web client keeps its icon).
     * One-off reminders disappear at once with an Undo snackbar, and the delete is only sent when the
     * snackbar goes away. A recurring series removes every occurrence, so it still asks first.
     */
    fun attachSwipeToDelete(recyclerView: RecyclerView, onSwiping: (Boolean) -> Unit = {}) {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            // past 35% of the card width deletes on release (default 50% feels heavy on a wide card)
            override fun getSwipeThreshold(vh: RecyclerView.ViewHolder) = 0.35f

            // while a card is being dragged, keep pull-to-refresh and other parents from stealing the
            // gesture (a slightly downward swipe at the top of the list used to cancel it and snap back)
            override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(vh, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    recyclerView.parent?.requestDisallowInterceptTouchEvent(true)
                    onSwiping(true)
                }
            }

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                onSwiping(false)
            }

            // same rule as the old trash button: no deleting while read-only or offline
            override fun getSwipeDirs(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int =
                if (readOnly || isOfflineMode) 0 else super.getSwipeDirs(rv, vh)

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val position = vh.adapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val reminder = reminders[position]
                if (reminder.Id.matches(Regex(".*_r\\d+$"))) {
                    confirmSeriesDelete(recyclerView, reminder, position)
                    return
                }
                reminders = reminders.toMutableList().apply { removeAt(position) }
                notifyItemRemoved(position)
                if (reminder.Id == selectedId) {
                    selectedId = null
                    onSelectionCleared?.invoke()
                }
                Snackbar.make(recyclerView, "Reminder deleted", Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
                        reminders = reminders.toMutableList().apply { add(minOf(position, size), reminder) }
                        notifyItemInserted(position)
                        recyclerView.scrollToPosition(position)
                    }
                    .addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(bar: Snackbar?, event: Int) {
                            if (event != DISMISS_EVENT_ACTION) reminderCallback?.onDeleteReminder(reminder)
                        }
                    })
                    .show()
            }

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val item = vh.itemView
                if (dX != 0f) {
                    // red rounded background (matches the 12dp card) with a white trash icon on the uncovered side
                    val density = rv.resources.displayMetrics.density
                    val background = GradientDrawable().apply {
                        cornerRadius = 12f * density
                        setColor(Color.parseColor("#E53935"))
                    }
                    background.setBounds(item.left, item.top, item.right, item.bottom)
                    c.save()
                    if (dX > 0) c.clipRect(item.left.toFloat(), item.top.toFloat(), item.left + dX, item.bottom.toFloat())
                    else c.clipRect(item.right + dX, item.top.toFloat(), item.right.toFloat(), item.bottom.toFloat())
                    background.draw(c)
                    ContextCompat.getDrawable(rv.context, R.drawable.ic_delete)?.mutate()?.let { icon ->
                        icon.setTint(Color.WHITE)
                        val inset = (24 * density).toInt()
                        val top = item.top + (item.height - icon.intrinsicHeight) / 2
                        val left = if (dX > 0) item.left + inset else item.right - inset - icon.intrinsicWidth
                        icon.setBounds(left, top, left + icon.intrinsicWidth, top + icon.intrinsicHeight)
                        icon.draw(c)
                    }
                    c.restore()
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(recyclerView)
    }

    private fun confirmSeriesDelete(recyclerView: RecyclerView, reminder: Reminder, position: Int) {
        val context = recyclerView.context
        val series = reminder.copy(Id = reminder.Id.replace(Regex("_r\\d+$"), ""))
        var deleting = false
        val dialog = MaterialAlertDialogBuilder(context, R.style.RoundedMaterialDialog)
            .setTitle("Delete recurring series?")
            .setMessage("This will remove all occurrences of this reminder. This can’t be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                deleting = true
                reminderCallback?.onDeleteReminder(series)
            }
            .setOnDismissListener { if (!deleting) notifyItemChanged(position) }  // slide the card back
            .show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            .setTextColor(Color.parseColor("#E53935"))
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
            .setTextColor(ContextCompat.getColor(context, R.color.colorTextPrimary))
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
        private val pendingBadge: TextView = itemView.findViewById(R.id.pendingBadge)
        private val description: TextView = itemView.findViewById(R.id.description)
        private val time: TextView = itemView.findViewById(R.id.time)
        private val read: TextView = itemView.findViewById(R.id.read)
        private val countdown: TextView = itemView.findViewById(R.id.countdown)
        private val templateBadge: TextView = itemView.findViewById(R.id.templateBadge)
        private val projectTag: TextView = itemView.findViewById(R.id.projectTag)
        private val projectButton: ImageButton = itemView.findViewById(R.id.projectButton)
        private val id: TextView = itemView.findViewById(R.id.id)
        private val editButton: ImageButton = itemView.findViewById(R.id.editButton)
        private val copyButton: ImageButton = itemView.findViewById(R.id.copyButton)
        private val linkButton: ImageButton = itemView.findViewById(R.id.linkButton)
        private val accentBar: View = itemView.findViewById(R.id.accentBar)
        private val cardContent: View = itemView.findViewById(R.id.cardContent)
        private val contextIcon: ImageView = itemView.findViewById(R.id.contextIcon)
        private val actionRow: View = itemView.findViewById(R.id.actionRow)

        fun bind(reminder: Reminder, readOnly: Boolean) {
            // In the main list a project is a minimal card: "<Project> begins", no notes or actions;
            // tapping it opens it on the Projects page, where it is edited.
            val compactProject = reminder.isProject && mode == Mode.LIST
            title.text = if (compactProject) "${reminder.Title} begins" else reminder.Title
            pendingBadge.visibility = if (reminder.isPending) View.VISIBLE else View.GONE
            description.text = reminder.Description
            description.visibility = if (compactProject || reminder.Description.isBlank()) View.GONE else View.VISIBLE
            time.text = if (reminder.isProject && mode == Mode.PROJECTS) projectRange(reminder) else compactTime(reminder.Time)

            // events inside a project are indented a little and name the project
            val member = !reminder.isProject && reminder.ParentId.isNotBlank() && mode == Mode.LIST
            val margins = itemView.layoutParams as ViewGroup.MarginLayoutParams
            // on a project's own page every event is indented, to set it apart from the details card above
            val indented = member || mode == Mode.PROJECT_EVENTS
            margins.marginStart = ((if (indented) 32 else 16) * itemView.resources.displayMetrics.density).toInt()
            itemView.layoutParams = margins
            val parentTitle = projectTitles[reminder.ParentId]
            projectTag.visibility = if (member && parentTitle != null) View.VISIBLE else View.GONE
            projectTag.text = if (parentTitle != null) "↳ $parentTitle" else ""
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
            val iconRes = CardStyle.iconFor(reminder)
            val accentColor = CardStyle.accentFor(itemView.context, reminder, iconRes)
            // the 9dp column never changes width (so the content never shifts): selected cards
            // paint all of it, others only the left 5dp
            val unpainted = if (reminder.Id == selectedId) 0 else (4 * itemView.resources.displayMetrics.density).toInt()
            accentBar.background = android.graphics.drawable.InsetDrawable(
                android.graphics.drawable.ColorDrawable(accentColor), 0, 0, unpainted, 0)
            cardContent.background = CardStyle.cardArt(itemView.context, iconRes, accentColor, reminder.Id.hashCode().toLong())
            CardStyle.styleIcon(contextIcon, iconRes, accentColor)

            val matchesTemplate = !reminder.isProject && Template.matchKey(reminder.Title, reminder.Description, reminder.Link, reminder.Priority) in templateKeys
            templateBadge.visibility = if (matchesTemplate) View.VISIBLE else View.GONE
            if (matchesTemplate) {
                val density = itemView.resources.displayMetrics.density
                templateBadge.setTextColor(accentColor)
                templateBadge.background = GradientDrawable().apply {
                    cornerRadius = 3f * density
                    setStroke((1.5f * density).toInt(), accentColor)
                }
            }

            val isVirtual = reminder.Id.matches(Regex(".*_r\\d+$"))
            if (readOnly || compactProject) {
                editButton.visibility = View.GONE
                copyButton.visibility = View.GONE
            } else if (isOfflineMode) {
                editButton.visibility = if (isVirtual) View.GONE else View.VISIBLE
                copyButton.visibility = View.GONE
            } else {
                editButton.visibility = if (isVirtual) View.GONE else View.VISIBLE
                copyButton.visibility = View.VISIBLE
            }
            editButton.setOnClickListener {
                reminderCallback?.onEditReminder(reminder, isOfflineMode)
            }
            copyButton.setOnClickListener {
                reminderCallback?.onCopyReminder(reminder)
            }
            // "+": add this event to a project (needs the server to check the date range)
            val canJoinProject = !readOnly && !isOfflineMode && !reminder.isProject &&
                !reminder.isPending && (mode == Mode.LIST || mode == Mode.PROJECT_EVENTS)
            projectButton.visibility = if (canJoinProject) View.VISIBLE else View.GONE
            projectButton.setOnClickListener { onAddToProject?.invoke(reminder) }
            val link = reminder.Link
            if (!link.isNullOrBlank() && !compactProject) {
                linkButton.visibility = View.VISIBLE
                linkButton.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    itemView.context.startActivity(intent)
                }
            } else {
                linkButton.visibility = View.GONE
                linkButton.setOnClickListener(null)
            }
            val ctx = itemView.context
            val density = ctx.resources.displayMetrics.density
            cardRoot.background = GradientDrawable().apply {
                cornerRadius = 12f * density
                setColor(ContextCompat.getColor(ctx, R.color.card_surface))
                setStroke((density + 0.5f).toInt(), ContextCompat.getColor(ctx, R.color.card_stroke))
            }
            time.background = GradientDrawable().apply {
                cornerRadius = 100f * density
                // high priority keeps its red even on category-colored cards: same fill as the
                // calendar's high-priority days; otherwise a light tint of the card's accent
                setColor(
                    if (reminder.Priority <= 1) ContextCompat.getColor(ctx, R.color.high_priority)
                    else ColorUtils.setAlphaComponent(accentColor, 0x2E)
                )
            }
            actionRow.setBackgroundColor(ColorUtils.setAlphaComponent(accentColor, 0x14))
            actionRow.visibility =
                if (listOf(editButton, copyButton, linkButton, projectButton, countdown, templateBadge).any { it.visibility == View.VISIBLE })
                    View.VISIBLE else View.GONE

            // For updating the mini calendar anchored to the reminder
            itemView.setOnClickListener {
                if (reminder.isProject) {
                    onProjectClick?.invoke(reminder)
                    return@setOnClickListener
                }
                val previous = reminders.indexOfFirst { it.Id == selectedId }
                selectedId = reminder.Id
                if (previous >= 0) notifyItemChanged(previous)
                notifyItemChanged(adapterPosition)
                onReminderClick?.invoke(reminder)
            }

            itemView.setOnLongClickListener {
                copyReminderToClipboard(itemView.context, reminder)
                true
            }
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
