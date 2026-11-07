package com.hyunwookshin.nudge

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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

/**
 * The Templates page. Works like the reminder list: shows the Room cache first, then refreshes
 * from the server, and uploads templates saved while offline once the server is reachable.
 * Tapping a template opens the editor for a new event (date prefilled when started from a day).
 */
class TemplateListFragment : Fragment() {

    companion object {
        private const val ARG_DATE = "date"
        private const val ARG_IS_OFFLINE = "is_offline"

        /** [date] (yyyy-MM-dd) is set when opened from a calendar day's long-press menu. */
        fun newInstance(date: String? = null, isOffline: Boolean = false): TemplateListFragment =
            TemplateListFragment().apply {
                arguments = Bundle().apply {
                    if (!date.isNullOrBlank()) putString(ARG_DATE, date)
                    putBoolean(ARG_IS_OFFLINE, isOffline)
                }
            }
    }

    private lateinit var adapter: TemplateAdapter
    private lateinit var emptyText: TextView
    private lateinit var offlineBanner: TextView
    private var isOffline = false
    private val date: String? get() = arguments?.getString(ARG_DATE)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_template_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isOffline = arguments?.getBoolean(ARG_IS_OFFLINE, false) ?: false
        emptyText = view.findViewById(R.id.emptyText)
        offlineBanner = view.findViewById(R.id.offlineBanner)
        date?.let { view.findViewById<TextView>(R.id.subtitle).text = "Pick a template for $it" }
        view.findViewById<View>(R.id.goBackButton).setOnClickListener { parentFragmentManager.popBackStack() }

        adapter = TemplateAdapter(onUse = ::useTemplate, onDelete = ::deleteTemplate)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        adapter.attachSwipeToDelete(recyclerView)

        loadCachedTemplates()
        fetchTemplates()
    }

    private fun dao() = AppDb.get(requireContext()).templateDao()

    private fun show(templates: List<Template>) {
        adapter.setTemplates(templates)
        emptyText.visibility = if (templates.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showOffline(offline: Boolean) {
        isOffline = offline
        adapter.setOfflineMode(offline)
        offlineBanner.visibility = if (offline) View.VISIBLE else View.GONE
    }

    private fun loadCachedTemplates() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val cached = dao().getAll().map { it.toDomain() }
            withContext(Dispatchers.Main) { if (isAdded) show(cached) }
        }
    }

    private fun fetchTemplates() {
        val apiService = ApiClient.getClient().create(ApiService::class.java)
        apiService.getTemplates().enqueue(object : Callback<TemplateResponse> {
            override fun onResponse(call: Call<TemplateResponse>, response: Response<TemplateResponse>) {
                if (!isAdded) return
                val server = response.body()?.templates
                if (!response.isSuccessful || server == null) {
                    showOffline(true)
                    return
                }
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    dao().replaceAll(server.map { it.toEntity() })
                    val all = dao().getAll().map { it.toDomain() }
                    val hasPending = all.any { it.isPending }
                    withContext(Dispatchers.Main) {
                        if (!isAdded) return@withContext
                        showOffline(false)
                        show(all)
                        if (hasPending) syncPendingTemplates()
                    }
                }
            }

            override fun onFailure(call: Call<TemplateResponse>, t: Throwable) {
                if (isAdded) showOffline(true)
            }
        })
    }

    /** Uploads templates saved offline (server assigns the real id), then refreshes. */
    private fun syncPendingTemplates() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val apiService = ApiClient.getClient().create(ApiService::class.java)
            var uploaded = 0
            for (entity in dao().getPending()) {
                val template = entity.toDomain().copy(Id = "", Key = entity.pendingKey)
                val ok = try { apiService.addTemplate(template).execute().isSuccessful } catch (e: Exception) { false }
                if (ok) {
                    dao().deleteById(entity.id)
                    uploaded++
                }
            }
            if (uploaded > 0) withContext(Dispatchers.Main) { if (isAdded) fetchTemplates() }
        }
    }

    private fun deleteTemplate(template: Template) {
        if (template.isPending) {
            // never reached the server: only the local copy exists
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) { dao().deleteById(template.Id) }
            return
        }
        val apiService = ApiClient.getClient().create(ApiService::class.java)
        apiService.deleteTemplate(template).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (!isAdded) return
                if (response.isSuccessful) {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) { dao().deleteById(template.Id) }
                } else {
                    Snackbar.make(requireView(), "Failed to delete template", Snackbar.LENGTH_SHORT).show()
                    loadCachedTemplates()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                if (!isAdded) return
                Snackbar.make(requireView(), "Network error: ${t.message}", Snackbar.LENGTH_SHORT).show()
                loadCachedTemplates()
            }
        })
    }

    private fun useTemplate(template: Template) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ReminderFragment.newInstanceFromTemplate(template, date, isOffline))
            .addToBackStack(null)
            .commit()
    }
}
