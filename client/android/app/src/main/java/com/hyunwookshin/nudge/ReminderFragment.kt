package com.hyunwookshin.nudge

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class ReminderFragment : Fragment() {

    private lateinit var titleEditText: EditText
    private lateinit var descriptionEditText: EditText
    private lateinit var dateButton: Button
    private lateinit var timeButton: Button
    private lateinit var linkEditText: EditText
    private lateinit var prioritySpinner: Spinner
    private lateinit var saveButton: Button
    private lateinit var passwordEditText: EditText
    private lateinit var snoozeSpinner: Spinner

    private var selectedDate: String? = null
    private var selectedTime: String? = null

    private var callback: ReminderCallback? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is ReminderCallback) {
            callback = context
        } else {
            throw RuntimeException("$context must implement ReminderCallback")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_reminder, container, false)
        titleEditText = view.findViewById(R.id.titleEditText)
        descriptionEditText = view.findViewById(R.id.descriptionEditText)
        dateButton = view.findViewById(R.id.dateButton)
        timeButton = view.findViewById(R.id.timeButton)
        linkEditText = view.findViewById(R.id.linkEditText)
        passwordEditText = view.findViewById(R.id.passwordEditText)
        prioritySpinner = view.findViewById(R.id.prioritySpinner)
        saveButton = view.findViewById(R.id.saveButton)
        snoozeSpinner = view.findViewById(R.id.snoozeSpinner)

        dateButton.setOnClickListener { showDatePicker() }
        timeButton.setOnClickListener { showTimePicker() }
        saveButton.setOnClickListener { saveReminder() }
        linkEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
            }
            false
        }
        passwordEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
            }
            false
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val showRemindersButton: Button = view.findViewById(R.id.showRemindersButton)
        // Button to show reminders
        showRemindersButton.setOnClickListener {
            callback?.onShowReminders()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val datePicker = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(
                    GregorianCalendar(year, month, dayOfMonth).time
                )
                selectedDate = date
                dateButton.text = date
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val timePicker = TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                val time = String.format("%02d:%02d:00", hourOfDay, minute)
                selectedTime = time
                timeButton.text = time
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )
        timePicker.show()
    }

    private fun hideKeyboard() {
        val inputMethodManager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    private fun saveReminder() {
        val title = titleEditText.text.toString()
        val description = descriptionEditText.text.toString()
        val link = linkEditText.text.toString()
        val priority = prioritySpinner.selectedItemPosition
        val date = selectedDate.toString()
        val time = selectedTime.toString()
        val read = selectedTime.toString()
        val key = passwordEditText.text.toString()
        val snooze = snoozeSpinner.selectedItemPosition

        if (title.isEmpty() || description.isEmpty() || selectedDate == null || selectedTime == null ) {
            Snackbar.make(requireView(), "All fields are required", Snackbar.LENGTH_SHORT).show()
            return
        }

        val reminder = Reminder(title, description, date, time, link, priority, key, snooze, read)
        sendReminder(reminder)

        // Clear all text fields except password
        titleEditText.text.clear()
        descriptionEditText.text.clear()
        linkEditText.text.clear()
    }

    private fun sendReminder(reminder: Reminder) {
        val apiService = ApiClient.getClient().create(ApiService::class.java)
        val call = apiService.addReminder(reminder)
        hideKeyboard()
        call.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Snackbar.make(requireView(), "Reminder added successfully", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(requireView(), "Failed to add reminder", Snackbar.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Snackbar.make(requireView(), "Network error: ${t.message}", Snackbar.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }
}
