package com.hyunwookshin.nudge

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDate

/**
 * One project on its own page: its details on top (type, dates, where it stands, notes, how many
 * events) and below it only that project's events, past ones included. Back returns to the Projects list.
 */
class ProjectDetailFragment : Fragment(), Refreshable {

    companion object {
        private const val ARG_PROJECT = "project"
        private const val ARG_IS_OFFLINE = "is_offline"

        // remembered across project pages for the life of the app
        private var calendarShown = true

        fun newInstance(project: Reminder, isOffline: Boolean = false): ProjectDetailFragment =
            ProjectDetailFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PROJECT, project)
                    putBoolean(ARG_IS_OFFLINE, isOffline)
                }
            }
    }

    private var callback: ReminderCallback? = null
    private lateinit var project: Reminder
    private lateinit var adapter: ReminderAdapter
    private lateinit var emptyText: TextView
    private lateinit var offlineBanner: TextView
    private var isOffline = false
    private var eventCount = 0
    private var events: List<Reminder> = emptyList()
    private lateinit var calendar: MiniCalendarAdapter
    private lateinit var calendarMonth: TextView
    private lateinit var recyclerView: RecyclerView
    private var calAnchor: LocalDate = LocalDate.now()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callback = context as? ReminderCallback
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_project_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        project = requireArguments().getParcelable(ARG_PROJECT)!!
        isOffline = requireArguments().getBoolean(ARG_IS_OFFLINE, false)
        emptyText = view.findViewById(R.id.emptyText)
        offlineBanner = view.findViewById(R.id.offlineBanner)

        view.findViewById<View>(R.id.goBackButton).setOnClickListener { parentFragmentManager.popBackStack() }
        view.findViewById<View>(R.id.editProjectButton).setOnClickListener { callback?.onEditReminder(project, isOffline) }

        adapter = ReminderAdapter().apply {
            mode = ReminderAdapter.Mode.PROJECT_EVENTS
            callback?.let { setReminderCallback(it) }
            // the "+" here moves an event to another project or takes it out of this one
            onAddToProject = { event ->
                ProjectMembership.openAddToProject(this@ProjectDetailFragment, event, isOffline) { refresh() }
            }
        }
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        adapter.attachSwipeToDelete(recyclerView)

        setupCalendar(view)
        showOffline(isOffline)
        bindHeader()
        loadCached()
        refresh()
    }

    /** The list page's calendar, fed only this project's events; the project's span is its band. */
    private fun setupCalendar(view: View) {
        calendarMonth = view.findViewById(R.id.miniCalendarMonth)
        val rv = view.findViewById<RecyclerView>(R.id.miniCalendarRv)
        rv.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 7)
        calendar = MiniCalendarAdapter(onDayClick = { day ->
            if (java.time.YearMonth.from(day) != java.time.YearMonth.from(calAnchor)) calAnchor = day   // a neighbouring month's day: move there
            renderCalendar()
            val key = day.toString()
            val idx = events.sortedBy { it.Time }.indexOfFirst { it.Time.take(10) >= key }
            if (idx >= 0) recyclerView.post { (recyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(idx, 0) }
        })
        rv.adapter = calendar
        rv.addItemDecoration(ProjectBandDecoration(requireContext(), calendar))
        view.findViewById<View>(R.id.miniCalPrev).setOnClickListener { calAnchor = calAnchor.plusMonths(-1).withDayOfMonth(10); renderCalendar() }
        view.findViewById<View>(R.id.miniCalNext).setOnClickListener { calAnchor = calAnchor.plusMonths(1).withDayOfMonth(10); renderCalendar() }
        calAnchor = startingAnchor()
        renderCalendar()

        val container = view.findViewById<View>(R.id.miniCalendarContainer)
        val pill = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.calendarTogglePill)
        fun applyVisibility() {
            container.visibility = if (calendarShown) View.VISIBLE else View.GONE
            pill.text = if (calendarShown) "Hide Calendar" else "Show Calendar"
        }
        applyVisibility()
        pill.setOnClickListener { calendarShown = !calendarShown; applyVisibility() }
    }

    /** Today while the project is running, else the project's first day (the month worth looking at). */
    private fun startingAnchor(): LocalDate {
        val start = runCatching { LocalDate.parse(project.Time.take(10)) }.getOrNull() ?: return LocalDate.now()
        val end = runCatching { LocalDate.parse(project.EndDate) }.getOrNull() ?: return start
        val today = LocalDate.now()
        return if (today.isBefore(start)) start else if (today.isAfter(end)) start else today
    }

    private fun renderCalendar() {
        if (view == null) return
        calendar.submit(MiniCalendarMath.buildDays(events, calAnchor))
        calendarMonth.text = calAnchor.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))
        val start = runCatching { LocalDate.parse(project.Time.take(10)) }.getOrNull()
        val end = runCatching { LocalDate.parse(project.EndDate) }.getOrNull()
        val ctx = requireContext()
        calendar.setBands(
            if (start != null && end != null)
                listOf(ProjectBand(start, end, CardStyle.accentFor(ctx, project, CardStyle.iconFor(project))))
            else emptyList()
        )
    }

    private fun dao() = AppDb.get(requireContext()).reminderDao()

    private fun bindHeader() {
        val root = requireView()
        val ctx = requireContext()
        val icon = CardStyle.iconFor(project)
        val accent = CardStyle.accentFor(ctx, project, icon)

        root.findViewById<TextView>(R.id.projectTitle).text = project.Title
        root.findViewById<View>(R.id.headerAccent).setBackgroundColor(accent)
        CardStyle.styleIcon(root.findViewById<ImageView>(R.id.typeIcon), icon, accent)
        root.findViewById<TextView>(R.id.typeLabel).text =
            if (project.ProjectType.isBlank()) "Project" else ProjectTypes.byKey(project.ProjectType).label

        val start = runCatching { LocalDate.parse(project.Time.take(10)) }.getOrNull()
        val end = runCatching { LocalDate.parse(project.EndDate) }.getOrNull()
        val range = root.findViewById<TextView>(R.id.rangeLabel)
        val status = root.findViewById<TextView>(R.id.statusLabel)
        if (start != null && end != null) {
            range.text = "${ProjectInfo.rangeLabel(start, end, LocalDate.now())}  ·  ${ProjectInfo.durationLabel(start, end)}"
            status.text = ProjectInfo.status(start, end, LocalDate.now())
        } else {
            range.text = ""
            status.text = ""
        }
        val density = resources.displayMetrics.density
        range.background = GradientDrawable().apply {
            cornerRadius = 100f * density
            setColor(ColorUtils.setAlphaComponent(accent, 0x2E))
        }

        val notes = root.findViewById<TextView>(R.id.descriptionLabel)
        notes.text = project.Description
        notes.visibility = if (project.Description.isBlank()) View.GONE else View.VISIBLE
        root.findViewById<TextView>(R.id.eventCountLabel).text = ProjectInfo.eventCountLabel(eventCount)
    }

    private fun showEvents(events: List<Reminder>) {
        this.events = events
        eventCount = events.size
        adapter.setReminders(events.sortedBy { it.Time })
        renderCalendar()
        emptyText.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
        view?.findViewById<TextView>(R.id.eventCountLabel)?.text = ProjectInfo.eventCountLabel(eventCount)
    }

    private fun showOffline(offline: Boolean) {
        isOffline = offline
        adapter.setOfflineMode(offline)
        offlineBanner.visibility = if (offline) View.VISIBLE else View.GONE
    }

    private fun loadCached() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val cached = dao().getByParent(project.Id).map { it.toDomain() }
            withContext(Dispatchers.Main) { if (isAdded && view != null) showEvents(cached) }
        }
    }

    /** Reloads the project and its events (past ones too) from the server; keeps a copy for offline. */
    override fun refresh() {
        val apiService = ApiClient.getClient().create(ApiService::class.java)
        apiService.getReminders(parent = project.Id).enqueue(object : Callback<ReminderResponse> {
            override fun onResponse(call: Call<ReminderResponse>, response: Response<ReminderResponse>) {
                if (!isAdded || view == null) return
                if (response.code() == 404) {
                    // deleted elsewhere
                    Snackbar.make(requireView(), "This project no longer exists", Snackbar.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                    return
                }
                val body = response.body()
                if (!response.isSuccessful || body == null) {
                    showOffline(true)
                    return
                }
                showOffline(false)
                body.project?.let { project = it; bindHeader(); renderCalendar() }
                // an older server ignores ?parent= and sends everything: only keep this project's events
                val events = body.reminders.filter { it.ParentId == project.Id }
                showEvents(events)
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    dao().upsertAll((listOf(project) + events).map { it.toEntity() })
                }
            }

            override fun onFailure(call: Call<ReminderResponse>, t: Throwable) {
                if (isAdded && view != null) showOffline(true)
            }
        })
    }
}
