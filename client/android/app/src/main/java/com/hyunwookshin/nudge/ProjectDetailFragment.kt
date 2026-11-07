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
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        adapter.attachSwipeToDelete(recyclerView)

        showOffline(isOffline)
        bindHeader()
        loadCached()
        refresh()
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
        eventCount = events.size
        adapter.setReminders(events.sortedBy { it.Time })
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
                body.project?.let { project = it; bindHeader() }
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
