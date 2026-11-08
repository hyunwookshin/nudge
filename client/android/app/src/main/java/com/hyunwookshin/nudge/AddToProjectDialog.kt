package com.hyunwookshin.nudge

import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout

/** "Add to Project" for an event (the "+" on its card). */
object AddToProjectDialog {

    private const val NOT_IN_RANGE = "Not part of the project's date range"

    /** Both are yyyy-MM-dd, so comparing the strings compares the dates. */
    fun inRange(event: Reminder, project: Reminder): Boolean =
        event.Time.take(10) >= project.Time.take(10) && event.Time.take(10) <= project.EndDate

    /**
     * [projects] are all of the user's projects; only those whose dates cover the event are offered
     * (plus the event's current one). [onGoToProjects] runs when none can be offered.
     * [onConfirm] gets the chosen project's id, or "" to take the event out of its project.
     * The date range is checked here for a quick answer; the server checks it again.
     */
    fun show(
        context: Context,
        event: Reminder,
        projects: List<Reminder>,
        onGoToProjects: () -> Unit,
        onConfirm: (projectId: String, projectTitle: String) -> Unit,
    ) {
        val themed = R.style.RoundedMaterialDialog
        val eligible = projects.filter { inRange(event, it) || it.Id == event.ParentId }
        if (eligible.isEmpty()) {
            MaterialAlertDialogBuilder(context, themed)
                .setTitle("Add to Project")
                .setMessage(
                    if (projects.isEmpty()) "No projects created yet."
                    else "No project covers ${event.Time.take(10)}. Create one that does, or change the event's date."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Go to Projects") { _, _ -> onGoToProjects() }
                .show()
                .also { dialog ->
                    dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
                        .setTextColor(ContextCompat.getColor(context, R.color.colorTextPrimary))
                }
            return
        }

        // entries: the projects, plus a way out when the event is already in one
        val entries = mutableListOf<Pair<String, Reminder?>>()
        if (event.ParentId.isNotBlank()) entries.add("None (remove from project)" to null)
        eligible.forEach { entries.add("${it.Title}  ·  ${it.Time.take(10)} – ${it.EndDate}" to it) }

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_to_project, null)
        val layout = view.findViewById<TextInputLayout>(R.id.projectPickLayout)
        val pick = view.findViewById<AutoCompleteTextView>(R.id.projectPick)
        pick.setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, entries.map { it.first }))
        var chosen = entries.indexOfFirst { it.second?.Id == event.ParentId }.takeIf { it >= 0 }
            ?: if (entries.size == 1 && entries[0].second != null) 0 else -1
        if (chosen >= 0) pick.setText(entries[chosen].first, false)
        pick.setOnItemClickListener { _, _, position, _ -> chosen = position; layout.error = null }

        val dialog = MaterialAlertDialogBuilder(context, themed)
            .setTitle("Add to Project")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm", null)   // set below, so a bad choice keeps the dialog open
            .show()
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
            .setTextColor(ContextCompat.getColor(context, R.color.colorTextPrimary))
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (chosen < 0) {
                layout.error = "Pick a project"
                return@setOnClickListener
            }
            val project = entries[chosen].second
            if (project != null && !inRange(event, project)) {
                layout.error = NOT_IN_RANGE
                return@setOnClickListener
            }
            dialog.dismiss()
            onConfirm(project?.Id ?: "", project?.Title ?: "")
        }
    }
}
