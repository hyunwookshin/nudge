package com.hyunwookshin.nudge

import android.app.DatePickerDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDate

/**
 * New project dialog (the "+" on the Projects page): title, description, start and end date and a
 * type. A project is a reminder that begins at [START_TIME] on its start date and runs until its end date.
 */
object CreateProjectDialog {

    private const val START_TIME = "09:00:00"

    fun show(fragment: Fragment, isOffline: Boolean, onCreated: () -> Unit) {
        val context = fragment.requireContext()
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_create_project, null)
        val title = view.findViewById<TextInputEditText>(R.id.projectTitle)
        val description = view.findViewById<TextInputEditText>(R.id.projectDescription)
        val startButton = view.findViewById<MaterialButton>(R.id.projectStart)
        val endButton = view.findViewById<MaterialButton>(R.id.projectEnd)
        val typeView = view.findViewById<AutoCompleteTextView>(R.id.projectType)
        val error = view.findViewById<TextView>(R.id.projectError)

        var start: LocalDate? = null
        var end: LocalDate? = null
        var type = ProjectTypes.all.first()
        typeView.setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, ProjectTypes.all.map { it.label }))
        typeView.setText(type.label, false)
        typeView.setOnItemClickListener { _, _, position, _ -> type = ProjectTypes.all[position] }

        fun pickDate(initial: LocalDate?, onPicked: (LocalDate) -> Unit) {
            val d = initial ?: LocalDate.now()
            DatePickerDialog(context, { _, y, m, day -> onPicked(LocalDate.of(y, m + 1, day)) },
                d.year, d.monthValue - 1, d.dayOfMonth).show()
        }
        startButton.setOnClickListener {
            pickDate(start) { picked ->
                start = picked
                startButton.text = picked.toString()
                // keep the range valid while the user is picking
                if (end != null && end!!.isBefore(picked)) { end = picked; endButton.text = picked.toString() }
                error.visibility = View.GONE
            }
        }
        endButton.setOnClickListener {
            pickDate(end ?: start) { picked -> end = picked; endButton.text = picked.toString(); error.visibility = View.GONE }
        }

        val dialog = MaterialAlertDialogBuilder(context, R.style.RoundedMaterialDialog)
            .setTitle("New Project")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create", null)   // set below so validation keeps the dialog open
            .show()
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
            .setTextColor(ContextCompat.getColor(context, R.color.colorTextPrimary))
        val create = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)

        fun fail(message: String) { error.text = message; error.visibility = View.VISIBLE; create.isEnabled = true }

        create.setOnClickListener {
            val name = title.text.toString().trim()
            val s = start
            val e = end
            when {
                name.isEmpty() -> return@setOnClickListener fail("A title is required")
                s == null -> return@setOnClickListener fail("Pick a start date")
                e == null -> return@setOnClickListener fail("Pick an end date")
                e.isBefore(s) -> return@setOnClickListener fail("The end date must be on or after the start date")
            }
            create.isEnabled = false
            val project = Reminder(
                Title = name, Description = description.text.toString().trim(),
                Date = s.toString(), Time = START_TIME, Id = "", Link = "", Priority = 2,
                Key = ApiKey.key, Snooze = 1, Read = "", Repeat = 0,
                Kind = Reminder.KIND_PROJECT, EndDate = e.toString(), ProjectType = type.key,
            )
            if (isOffline) {
                saveLocally(fragment, project) { dialog.dismiss(); onCreated() }
                return@setOnClickListener
            }
            ApiClient.getClient().create(ApiService::class.java).addReminder(project)
                .enqueue(object : Callback<Void> {
                    override fun onResponse(call: Call<Void>, response: Response<Void>) {
                        if (response.isSuccessful) { dialog.dismiss(); onCreated() }
                        else fail(ApiErrors.message(response, "Could not create the project"))
                    }

                    override fun onFailure(call: Call<Void>, t: Throwable) {
                        // no connection: keep it on the phone and upload it later, like offline reminders
                        saveLocally(fragment, project) { dialog.dismiss(); onCreated() }
                    }
                })
        }
    }

    private fun saveLocally(fragment: Fragment, p: Reminder, done: () -> Unit) {
        val entity = ReminderEntity(
            id = "pending_${System.currentTimeMillis()}",
            title = p.Title, description = p.Description, date = p.Date, time = "${p.Date} ${p.Time}",
            link = "", priority = p.Priority, snooze = p.Snooze, read = "",
            isPending = true, pendingKey = p.Key,
            kind = p.Kind, endDate = p.EndDate, projectType = p.ProjectType, parentId = "",
        )
        fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            AppDb.get(fragment.requireContext()).reminderDao().upsert(entity)
            withContext(Dispatchers.Main) { done() }
        }
    }
}
