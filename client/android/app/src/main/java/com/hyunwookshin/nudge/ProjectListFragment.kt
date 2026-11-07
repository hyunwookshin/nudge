package com.hyunwookshin.nudge

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * The Projects page: every project as a card (the same cards as events, with the date range on the
 * chip). This is where projects are created (+), edited and deleted; the main list only shows them
 * as minimal "<Project> begins" cards that open the project here.
 */
class ProjectListFragment : Fragment(), Refreshable {

    companion object {
        private const val ARG_HIGHLIGHT = "highlight"
        private const val ARG_IS_OFFLINE = "is_offline"

        /** [highlightId]: the project to scroll to and highlight (when opened from its card in the list). */
        fun newInstance(highlightId: String? = null, isOffline: Boolean = false): ProjectListFragment =
            ProjectListFragment().apply {
                arguments = Bundle().apply {
                    if (!highlightId.isNullOrBlank()) putString(ARG_HIGHLIGHT, highlightId)
                    putBoolean(ARG_IS_OFFLINE, isOffline)
                }
            }
    }

    private var callback: ReminderCallback? = null
    private lateinit var adapter: ReminderAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var offlineBanner: TextView
    private var isOffline = false
    private var highlightId: String? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callback = context as? ReminderCallback
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_project_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isOffline = arguments?.getBoolean(ARG_IS_OFFLINE, false) ?: false
        highlightId = arguments?.getString(ARG_HIGHLIGHT)
        emptyText = view.findViewById(R.id.emptyText)
        offlineBanner = view.findViewById(R.id.offlineBanner)
        view.findViewById<View>(R.id.goBackButton).setOnClickListener { parentFragmentManager.popBackStack() }

        adapter = ReminderAdapter().apply {
            mode = ReminderAdapter.Mode.PROJECTS
            callback?.let { setReminderCallback(it) }
            // tapping a project opens its own page (events in isolation)
            onProjectClick = { project ->
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, ProjectDetailFragment.newInstance(project, isOffline))
                    .addToBackStack(null)
                    .commit()
            }
        }
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        adapter.attachSwipeToDelete(recyclerView)

        view.findViewById<FloatingActionButton>(R.id.newProjectButton).setOnClickListener {
            CreateProjectDialog.show(this, isOffline) { refresh() }
        }

        showOffline(isOffline)
        loadCached()
        refresh()
    }

    private fun dao() = AppDb.get(requireContext()).reminderDao()

    private fun show(unsorted: List<Reminder>) {
        // newest first, by start date
        val projects = unsorted.sortedByDescending { it.Time }
        adapter.setReminders(projects)
        emptyText.visibility = if (projects.isEmpty()) View.VISIBLE else View.GONE
        // opened from a project's card in the list: scroll to it and give it the thick accent bar
        val target = highlightId ?: return
        val index = projects.indexOfFirst { it.Id == target }
        if (index >= 0) {
            adapter.select(target)
            (recyclerView.layoutManager as LinearLayoutManager)
                .scrollToPositionWithOffset(index, (24 * resources.displayMetrics.density).toInt())
            highlightId = null   // only the first time
        }
    }

    private fun showOffline(offline: Boolean) {
        isOffline = offline
        adapter.setOfflineMode(offline)
        offlineBanner.visibility = if (offline) View.VISIBLE else View.GONE
    }

    private fun loadCached() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val cached = dao().getProjects().map { it.toDomain() }
            withContext(Dispatchers.Main) { if (isAdded && view != null) show(cached) }
        }
    }

    /** Reloads every project from the server (and keeps a copy in Room for offline). */
    override fun refresh() {
        val apiService = ApiClient.getClient().create(ApiService::class.java)
        apiService.getReminders(kind = Reminder.KIND_PROJECT).enqueue(object : Callback<ReminderResponse> {
            override fun onResponse(call: Call<ReminderResponse>, response: Response<ReminderResponse>) {
                if (!isAdded || view == null) return
                val projects = response.body()?.reminders
                if (!response.isSuccessful || projects == null) {
                    showOffline(true)
                    return
                }
                showOffline(false)
                show(projects)
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    dao().upsertAll(projects.map { it.toEntity() })
                }
            }

            override fun onFailure(call: Call<ReminderResponse>, t: Throwable) {
                if (isAdded && view != null) showOffline(true)
            }
        })
    }
}
