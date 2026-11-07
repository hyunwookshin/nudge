package com.hyunwookshin.nudge

import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/** The "+" on an event: pick a project (or none), with the server checking the date range. */
object ProjectMembership {

    /** Asks the server for the projects, shows Add to Project, and calls [onChanged] after a change. */
    fun openAddToProject(fragment: Fragment, event: Reminder, isOffline: Boolean, onChanged: () -> Unit) {
        val api = ApiClient.getClient().create(ApiService::class.java)
        api.getReminders(kind = Reminder.KIND_PROJECT).enqueue(object : Callback<ReminderResponse> {
            override fun onResponse(call: Call<ReminderResponse>, response: Response<ReminderResponse>) {
                if (!fragment.isAdded || fragment.view == null) return
                val projects = response.body()?.reminders
                if (!response.isSuccessful || projects == null) {
                    Snackbar.make(fragment.requireView(), "Could not load projects", Snackbar.LENGTH_SHORT).show()
                    return
                }
                AddToProjectDialog.show(
                    fragment.requireContext(), event, projects.sortedByDescending { it.Time },
                    onGoToProjects = {
                        fragment.parentFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, ProjectListFragment.newInstance(isOffline = isOffline))
                            .addToBackStack(null)
                            .commit()
                    },
                    onConfirm = { projectId, projectTitle -> setProject(fragment, event, projectId, projectTitle, onChanged) },
                )
            }

            override fun onFailure(call: Call<ReminderResponse>, t: Throwable) {
                if (fragment.isAdded && fragment.view != null) {
                    Snackbar.make(fragment.requireView(), "Network error: ${t.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun setProject(fragment: Fragment, event: Reminder, projectId: String, projectTitle: String, onChanged: () -> Unit) {
        val api = ApiClient.getClient().create(ApiService::class.java)
        api.setProject(SetProjectRequest(event.Id, projectId)).enqueue(object : Callback<MessageResponse> {
            override fun onResponse(call: Call<MessageResponse>, response: Response<MessageResponse>) {
                if (!fragment.isAdded || fragment.view == null) return
                val message = if (response.isSuccessful) {
                    onChanged()
                    if (projectId.isEmpty()) "Removed from project" else "Added to $projectTitle"
                } else {
                    ApiErrors.message(response, "Could not update the project")   // e.g. "Not part of the project's date range"
                }
                Snackbar.make(fragment.requireView(), message, Snackbar.LENGTH_LONG).show()
            }

            override fun onFailure(call: Call<MessageResponse>, t: Throwable) {
                if (fragment.isAdded && fragment.view != null) {
                    Snackbar.make(fragment.requireView(), "Network error: ${t.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        })
    }
}
