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

    private lateinit var titleEditText: AutoCompleteTextView
    private lateinit var descriptionEditText: AutoCompleteTextView
    private lateinit var reminders: List<Reminder>
    private lateinit var dateButton: Button
    private lateinit var timeButton: Button
    private lateinit var linkEditText: EditText
    private lateinit var prioritySpinner: Spinner
    private lateinit var saveButton: Button
    private lateinit var passwordEditText: EditText
    private lateinit var snoozeSpinner: Spinner
    private lateinit var idEditText: EditText
    private lateinit var aiInputEditText: EditText
    private lateinit var aiGenerateButton: Button

    private var selectedDate: String? = null          // yyyy-MM-dd
    private var selectedTime: String? = null          // HH:mm:00

    //  Keep a single calendar for the selected date/time so pickers & saving stay consistent
    private val selectedCal: Calendar = Calendar.getInstance()

    private val apiDateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private var callback: ReminderCallback? = null

    companion object {
        private const val ARG_REMINDER = "reminder"
        private const val ARG_PREFILL_DATE = "prefill_date"

        fun newInstance(reminder: Reminder): ReminderFragment {
            val fragment = ReminderFragment()
            val args = Bundle()
            args.putParcelable(ARG_REMINDER, reminder)
            fragment.arguments = args
            return fragment
        }

        fun newInstanceForDate(date: String): ReminderFragment {
            val fragment = ReminderFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_PREFILL_DATE, date) // yyyy-MM-dd
            }
            return fragment
        }
    }

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
        idEditText = view.findViewById(R.id.idEditText)
        aiInputEditText = view.findViewById(R.id.aiInputEditText)
        aiGenerateButton = view.findViewById(R.id.aiGenerateButton)
        aiGenerateButton.setOnClickListener { generateReminderUsingAI() }

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
        passwordEditText.setText(ApiKey.key)

        val reminder: Reminder? = arguments?.getParcelable(ARG_REMINDER)
        // Prepopulation when user long-pressed date on mini calendar
        val prefillDate = arguments?.getString(ARG_PREFILL_DATE)
        if (!prefillDate.isNullOrBlank()) {
            selectedDate = prefillDate
            dateButton.text = prefillDate
        }

        // Prepopulation when user clicks "Edit" from the list view.
        reminder?.let {
            titleEditText.setText(it.Title)
            descriptionEditText.setText(it.Description)
            linkEditText.setText(it.Link)
            prioritySpinner.setSelection(it.Priority)
            idEditText.setText(it.Id)

            //  Parse existing reminder time and set date/time buttons + internal state
            // Expecting it.Time like "yyyy-MM-dd HH:mm"
            val parsed = try {
                apiDateTimeFormat.parse(it.Time)
            } catch (e: Exception) {
                null
            }

            if (parsed != null) {
                selectedCal.time = parsed

                // selectedDate = yyyy-MM-dd
                selectedDate = apiDateFormat.format(selectedCal.time)
                dateButton.text = selectedDate

                // selectedTime = HH:mm:00
                val hour = selectedCal.get(Calendar.HOUR_OF_DAY)
                val minute = selectedCal.get(Calendar.MINUTE)
                selectedTime = String.format(Locale.US, "%02d:%02d:00", hour, minute)
                timeButton.text = selectedTime
            } else {
                // If parsing fails, leave buttons as-is; user can pick again
                selectedDate = null
                selectedTime = null
            }
        }

        fetchAllReminders()
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val showRemindersButton: Button = view.findViewById(R.id.showRemindersButton)
        showRemindersButton.setOnClickListener { callback?.onShowReminders() }
    }

    private fun showDatePicker() {
        // Start picker at currently selected value (or today if none)
        val year = selectedCal.get(Calendar.YEAR)
        val month = selectedCal.get(Calendar.MONTH)
        val day = selectedCal.get(Calendar.DAY_OF_MONTH)

        val datePicker = DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                selectedCal.set(Calendar.YEAR, y)
                selectedCal.set(Calendar.MONTH, m)
                selectedCal.set(Calendar.DAY_OF_MONTH, d)

                val date = apiDateFormat.format(selectedCal.time) // yyyy-MM-dd
                selectedDate = date
                dateButton.text = date
            },
            year, month, day
        )
        datePicker.show()
    }

    private fun showTimePicker() {
        //  Start picker at currently selected value (or now if none)
        val hour = selectedCal.get(Calendar.HOUR_OF_DAY)
        val minute = selectedCal.get(Calendar.MINUTE)

        val timePicker = TimePickerDialog(
            requireContext(),
            { _, h, min ->
                selectedCal.set(Calendar.HOUR_OF_DAY, h)
                selectedCal.set(Calendar.MINUTE, min)
                selectedCal.set(Calendar.SECOND, 0)
                selectedCal.set(Calendar.MILLISECOND, 0)

                val time = String.format(Locale.US, "%02d:%02d:00", h, min)
                selectedTime = time
                timeButton.text = time
            },
            hour, minute,
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
        val read = selectedTime.toString()
        val key = passwordEditText.text.toString()
        val id = idEditText.text.toString()
        val snooze = snoozeSpinner.selectedItemPosition

        if (title.isEmpty() || description.isEmpty() || selectedDate == null || selectedTime == null ) {
            Snackbar.make(requireView(), "All fields are required", Snackbar.LENGTH_SHORT).show()
            return
        }

        // Your Reminder constructor seems to want separate date + time fields.
        val date = selectedDate!!            // yyyy-MM-dd
        val time = selectedTime!!            // HH:mm:00

        val reminder = Reminder(title, description, date, time, id, link, priority, key, snooze, read)
        sendReminder(reminder)

        // Clear all text fields except password
        titleEditText.text.clear()
        descriptionEditText.text.clear()
        linkEditText.text.clear()
        idEditText.text.clear()

        // Reset selected date/time
        selectedDate = null
        selectedTime = null
    }

    private fun sendReminder(reminder: Reminder) {
        val apiService = ApiClient.getClient().create(ApiService::class.java)
        val call = apiService.addReminder(reminder)
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

    private fun fetchAllReminders() {
        val apiService = ApiClient.getClient().create(ApiService::class.java)
        val call = apiService.getAllReminders()

        call.enqueue(object : Callback<ReminderResponse> {
            override fun onResponse(call: Call<ReminderResponse>, response: Response<ReminderResponse>) {
                if (!isAdded) return
                if (response.isSuccessful) {
                    reminders = response.body()?.reminders ?: emptyList()
                    setupAutoComplete()
                } else {
                    view?.let { v ->
                        Snackbar.make(v, "Failed to fetch reminders", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(call: Call<ReminderResponse>, t: Throwable) {
                if (!isAdded) return
                view?.let { v ->
                    Snackbar.make(v, "Network error: ${t.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun generateReminderUsingAI() {
        val text = aiInputEditText.text.toString().trim()
        val key = passwordEditText.text.toString().trim()

        if (text.isEmpty()) {
            Snackbar.make(requireView(), "Type something for AI to generate.", Snackbar.LENGTH_SHORT).show()
            return
        }
        if (key.isEmpty()) {
            Snackbar.make(requireView(), "Key is required.", Snackbar.LENGTH_SHORT).show()
            return
        }

        aiGenerateButton.isEnabled = false

        val apiService = ApiClient.getClient().create(ApiService::class.java)
        val req = AddReminderAiRequest(Text = text, Key = key)

        apiService.addReminderAI(req).enqueue(object : Callback<AddReminderAiResponse> {
            override fun onResponse(
                call: Call<AddReminderAiResponse>,
                response: Response<AddReminderAiResponse>
            ) {
                aiGenerateButton.isEnabled = true

                if (!response.isSuccessful || response.body() == null) {
                    Snackbar.make(requireView(), "AI generate failed (${response.code()})", Snackbar.LENGTH_SHORT).show()
                    return
                }

                Snackbar.make(requireView(), "Added via AI.", Snackbar.LENGTH_SHORT).show()
            }

            override fun onFailure(call: Call<AddReminderAiResponse>, t: Throwable) {
                aiGenerateButton.isEnabled = true
                Snackbar.make(requireView(), "Network error: ${t.message}", Snackbar.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupAutoComplete() {
        val titles = reminders.map { it.Title }.distinct()
        val descriptions = reminders.map { it.Description }.distinct()

        val titleAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, titles)
        val descriptionAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, descriptions)

        titleEditText.setAdapter(titleAdapter)
        descriptionEditText.setAdapter(descriptionAdapter)
    }
}
