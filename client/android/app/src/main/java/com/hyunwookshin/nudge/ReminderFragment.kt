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
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReminderFragment : Fragment() {

    private lateinit var pageTitle: TextView
    private lateinit var titleEditText: AutoCompleteTextView
    private lateinit var descriptionEditText: AutoCompleteTextView
    private lateinit var reminders: List<Reminder>
    private lateinit var dateButton: Button
    private lateinit var timeButton: Button
    private lateinit var linkEditText: EditText
    private lateinit var prioritySpinner: ChoiceChips
    private lateinit var repeatSpinner: ChoiceChips
    private lateinit var saveButton: Button
    private lateinit var saveTemplateButton: Button
    private lateinit var projectFields: View
    private lateinit var repeatRow: View
    private lateinit var endDateButton: Button
    private lateinit var projectTypeSpinner: Spinner
    private var selectedEndDate: String? = null       // yyyy-MM-dd, projects only
    // The reminder being edited. Its project fields (kind, project link, ...) must survive the
    // save, because the form only shows some of them.
    private var editing: Reminder? = null
    // Template.matchKey() of every saved template: Save as Template stays off while the form matches one
    private val templateKeys = mutableSetOf<String>()
    private lateinit var passwordEditText: EditText
    private lateinit var snoozeSpinner: Spinner
    private lateinit var idEditText: EditText
    private lateinit var aiInputEditText: EditText
    private lateinit var aiGenerateButton: Button
    private lateinit var aiBox: LinearLayout

    private lateinit var topLoading: com.google.android.material.progressindicator.LinearProgressIndicator
    private var inFlightCount = 0

    private var selectedDate: String? = null          // yyyy-MM-dd
    private var selectedTime: String? = null          // HH:mm:00

    //  Keep a single calendar for the selected date/time so pickers & saving stay consistent
    private val selectedCal: Calendar = Calendar.getInstance()

    private val apiDateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private var callback: ReminderCallback? = null

    private lateinit var voiceButton: ImageButton
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: Intent? = null
    private var isListening = false
    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startListening()
            } else {
                if (!isAdded) return@registerForActivityResult
                view?.let { Snackbar.make(it, "Mic permission denied", Snackbar.LENGTH_SHORT).show() }
            }
        }

    companion object {
        private const val ARG_REMINDER = "reminder"
        private const val ARG_PREFILL_DATE = "prefill_date"
        private const val ARG_PARENT_ID = "parent_id"
        private const val ARG_PARENT_START = "parent_start"
        private const val ARG_PARENT_END = "parent_end"
        private const val ARG_IS_OFFLINE = "is_offline"
        private const val ARG_TEMPLATE = "template"

        fun newInstance(reminder: Reminder, isOffline: Boolean = false): ReminderFragment {
            val fragment = ReminderFragment()
            fragment.arguments = Bundle().apply {
                putParcelable(ARG_REMINDER, reminder)
                putBoolean(ARG_IS_OFFLINE, isOffline)
            }
            return fragment
        }

        fun newInstance(isOffline: Boolean = false): ReminderFragment {
            val fragment = ReminderFragment()
            fragment.arguments = Bundle().apply {
                putBoolean(ARG_IS_OFFLINE, isOffline)
            }
            return fragment
        }

        /** New event prefilled from a template; [date] is set when started from a calendar day. */
        fun newInstanceFromTemplate(template: Template, date: String? = null, isOffline: Boolean = false): ReminderFragment {
            val fragment = ReminderFragment()
            fragment.arguments = Bundle().apply {
                putParcelable(ARG_TEMPLATE, template)
                if (!date.isNullOrBlank()) putString(ARG_PREFILL_DATE, date)
                putBoolean(ARG_IS_OFFLINE, isOffline)
            }
            return fragment
        }

        /** New event that joins [project]; the date starts at today, or the nearest day inside the project. */
        fun newInstanceForProject(project: Reminder, isOffline: Boolean = false): ReminderFragment {
            val start = project.Time.take(10)
            val today = java.time.LocalDate.now().toString()
            val fragment = ReminderFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_PREFILL_DATE, today.coerceIn(start, project.EndDate.ifBlank { start }))
                putString(ARG_PARENT_ID, project.Id)
                putString(ARG_PARENT_START, start)
                putString(ARG_PARENT_END, project.EndDate)
                putBoolean(ARG_IS_OFFLINE, isOffline)
            }
            return fragment
        }

        fun newInstanceForDate(date: String, isOffline: Boolean = false): ReminderFragment {
            val fragment = ReminderFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_PREFILL_DATE, date)
                putBoolean(ARG_IS_OFFLINE, isOffline)
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
        topLoading = view.findViewById(R.id.topLoading)
        pageTitle = view.findViewById(R.id.pageTitle)
        titleEditText = view.findViewById(R.id.titleEditText)
        descriptionEditText = view.findViewById(R.id.descriptionEditText)
        dateButton = view.findViewById(R.id.dateButton)
        timeButton = view.findViewById(R.id.timeButton)
        linkEditText = view.findViewById(R.id.linkEditText)
        passwordEditText = view.findViewById(R.id.passwordEditText)
        prioritySpinner = view.findViewById(R.id.prioritySpinner)
        repeatSpinner = view.findViewById(R.id.repeatSpinner)
        prioritySpinner.setEntries(resources.getStringArray(R.array.priority_chips).toList())
        repeatSpinner.setEntries(resources.getStringArray(R.array.repeat_chips).toList())
        saveButton = view.findViewById(R.id.saveButton)
        snoozeSpinner = view.findViewById(R.id.snoozeSpinner)
        idEditText = view.findViewById(R.id.idEditText)
        aiInputEditText = view.findViewById(R.id.aiInputEditText)
        aiBox = view.findViewById(R.id.aiBox)
        aiGenerateButton = view.findViewById(R.id.aiGenerateButton)
        aiGenerateButton.setOnClickListener { generateReminderUsingAI() }
        voiceButton = view.findViewById(R.id.voiceButton)
        voiceButton.setOnClickListener { onVoiceClick() }
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            voiceButton.isEnabled = false
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())

        // Speech
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            // Optional: prompt shown in some UIs
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your reminder…")
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                voiceButton.setImageResource(R.drawable.ic_stop)

                // see color/mic_tint.xml
                voiceButton.isActivated = true
                voiceButton.contentDescription = "Stop recording"
            }

            override fun onResults(results: Bundle?) {
                val matches = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?: arrayListOf()

                val text = matches.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    // OVERRIDE
                    aiInputEditText.setText(text)
                    aiInputEditText.setSelection(aiInputEditText.text.length)
                }
                stopListeningUi()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Optional: show partial transcription live
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!partial.isNullOrBlank()) {
                    aiInputEditText.setText(partial)
                    aiInputEditText.setSelection(aiInputEditText.text.length)
                }
            }

            override fun onError(error: Int) {
                stopListeningUi()
                if (!isAdded) return
                view?.let { Snackbar.make(it, "Speech error: $error", Snackbar.LENGTH_SHORT).show() }
            }

            override fun onEndOfSpeech() {
                // Recognizer stops on its own; results will come next
            }

            // Unused callbacks
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onRmsChanged(rmsdB: Float) {}
        })

        dateButton.setOnClickListener { showDatePicker() }
        timeButton.setOnClickListener { showTimePicker() }
        saveButton.setOnClickListener { saveReminder() }
        projectFields = view.findViewById(R.id.projectFields)
        repeatRow = view.findViewById(R.id.repeatRow)
        endDateButton = view.findViewById(R.id.endDateButton)
        projectTypeSpinner = view.findViewById(R.id.projectTypeSpinner)
        endDateButton.setOnClickListener { pickEndDate() }
        saveTemplateButton = view.findViewById(R.id.saveTemplateButton)
        saveTemplateButton.setOnClickListener { saveAsTemplate() }

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

        val isOffline = arguments?.getBoolean(ARG_IS_OFFLINE, false) ?: false
        if (isOffline) {
            aiBox.visibility = View.GONE
            topLoading.visibility = View.GONE
        }

        // the AI box creates the event on the server directly (no project, no date-range check), so a
        // project's new event is filled in by hand
        if (!arguments?.getString(ARG_PARENT_ID).isNullOrBlank()) aiBox.visibility = View.GONE

        val reminder: Reminder? = arguments?.getParcelable(ARG_REMINDER)
        // Prepopulation when user long-pressed date on mini calendar
        val prefillDate = arguments?.getString(ARG_PREFILL_DATE)
        if (!prefillDate.isNullOrBlank()) {
            pageTitle.text = "Add Reminder"
            selectedDate = prefillDate
            showDate()
            aiInputEditText.hint = "Generate using AI for $selectedDate"
        }

        // Prepopulation when user clicks "Edit" from the list view.
        reminder?.let {
            // hide ai box since user is editing
            aiBox.visibility = View.GONE
            pageTitle.text = "Edit Reminder"
            titleEditText.setText(it.Title)
            descriptionEditText.setText(it.Description)
            linkEditText.setText(it.Link)
            prioritySpinner.setSelection(it.Priority)
            repeatSpinner.setSelection(when (it.Repeat) {
                7 -> 1
                14 -> 2
                else -> 0
            })
            idEditText.setText(it.Id)
            editing = it
            if (it.isProject) setupProjectEditing(it)

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
                showDate()

                // selectedTime = HH:mm:00
                val hour = selectedCal.get(Calendar.HOUR_OF_DAY)
                val minute = selectedCal.get(Calendar.MINUTE)
                selectedTime = String.format(Locale.US, "%02d:%02d:00", hour, minute)
                showTime()
            } else {
                // If parsing fails, leave buttons as-is; user can pick again
                selectedDate = null
                selectedTime = null
            }
        }

        // Prepopulation when user starts a new event from a template (no id: saving creates a new event)
        val template: Template? = arguments?.getParcelable(ARG_TEMPLATE)
        template?.let {
            aiBox.visibility = View.GONE
            pageTitle.text = "Add Reminder"
            titleEditText.setText(it.Title)
            descriptionEditText.setText(it.Description)
            linkEditText.setText(it.Link)
            prioritySpinner.setSelection(it.Priority)
        }

        fetchAllReminders()
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val goBackButton: View = view.findViewById(R.id.goBackButton)
        goBackButton.setOnClickListener { callback?.onAbortEditReminder() }
        watchFormForTemplateMatch()

        if (!NotificationUtils.areNotificationsEnabled(requireContext())) {
            showNotificationDisabledDialog()
            return
        }
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
                showDate()
            },
            year, month, day
        )
        datePicker.show()
    }

    private fun beginLoading() {
        inFlightCount++
        val isOffline = arguments?.getBoolean(ARG_IS_OFFLINE, false) ?: false
        if (inFlightCount == 1 && !isOffline) {
            topLoading.visibility = View.VISIBLE
            topLoading.isIndeterminate = true
        }
    }

    private fun endLoading() {
        if (inFlightCount > 0) inFlightCount--
        if (inFlightCount == 0) {
            topLoading.visibility = View.GONE
        }
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
                showTime()
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

    /** Project mode of the editor: end date and type fields, no repeat, no "Save as Template". */
    private fun setupProjectEditing(p: Reminder) {
        projectFields.visibility = View.VISIBLE
        repeatRow.visibility = View.GONE   // not requireView(): this runs inside onCreateView, before the view exists
        saveTemplateButton.visibility = View.GONE
        pageTitle.text = if (p.Title.isBlank()) "New Project" else "${p.Title} begins"
        selectedEndDate = p.EndDate.ifBlank { null }
        showEndDate()
        projectTypeSpinner.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, ProjectTypes.all.map { it.label })
        projectTypeSpinner.setSelection(ProjectTypes.all.indexOfFirst { it.key == p.ProjectType }.coerceAtLeast(0))
    }

    /** "Fri, Nov 7" (with the year when it isn't this year); [ymd] is yyyy-MM-dd. */
    private fun prettyDate(ymd: String): String = runCatching {
        val d = java.time.LocalDate.parse(ymd)
        val pattern = if (d.year == java.time.LocalDate.now().year) "EEE, MMM d" else "EEE, MMM d, yyyy"
        d.format(java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }.getOrDefault(ymd)

    /** "9:00 AM" in the phone's own style; [hms] is HH:mm:ss. */
    private fun prettyTime(hms: String): String = runCatching {
        java.time.LocalTime.parse(hms).format(
            java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
    }.getOrDefault(hms)

    // the buttons are rows: they show the chosen value, or invite to pick one
    private fun showDate() { dateButton.text = selectedDate?.let(::prettyDate) ?: "Add date" }
    private fun showTime() { timeButton.text = selectedTime?.let(::prettyTime) ?: "Add time" }
    private fun showEndDate() { endDateButton.text = selectedEndDate?.let(::prettyDate) ?: "End date" }

    private fun pickEndDate() {
        val base = runCatching { java.time.LocalDate.parse(selectedEndDate ?: selectedDate) }.getOrNull()
            ?: java.time.LocalDate.now()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            selectedEndDate = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
            showEndDate()
        }, base.year, base.monthValue - 1, base.dayOfMonth).show()
    }

    private fun saveReminder() {
        val title = titleEditText.text.toString()
        val description = descriptionEditText.text.toString()
        val link = linkEditText.text.toString()
        val priority = prioritySpinner.selectedItemPosition
        val repeat = when (repeatSpinner.selectedItemPosition) {
            1 -> 7
            2 -> 14
            else -> 0
        }
        val read = selectedTime.toString()
        val key = passwordEditText.text.toString()
        val id = idEditText.text.toString()
        val snooze = 1 // snoozeSpinner.selectedItemPosition

        val isProject = editing?.isProject == true
        // a project's notes are optional; it also needs an end date that is not before its start
        if (title.isEmpty() || (description.isEmpty() && !isProject) || selectedDate == null || selectedTime == null ||
            (isProject && selectedEndDate == null)) {
            Snackbar.make(requireView(), "All fields are required", Snackbar.LENGTH_SHORT).show()
            return
        }
        if (isProject && selectedEndDate!! < selectedDate!!) {
            Snackbar.make(requireView(), "The end date must be on or after the start date", Snackbar.LENGTH_SHORT).show()
            return
        }

        // a new event for a project has to fall inside the project's dates
        val parentStart = arguments?.getString(ARG_PARENT_START)
        val parentEnd = arguments?.getString(ARG_PARENT_END)
        if (editing == null && !parentStart.isNullOrBlank() && !parentEnd.isNullOrBlank() &&
            (selectedDate!! < parentStart || selectedDate!! > parentEnd)) {
            Snackbar.make(requireView(), "Not part of the project's date range ($parentStart – $parentEnd)", Snackbar.LENGTH_LONG).show()
            return
        }

        // Your Reminder constructor seems to want separate date + time fields.
        val date = selectedDate!!            // yyyy-MM-dd
        val time = selectedTime!!            // HH:mm:00

        val reminder = Reminder(
            title, description, date, time, id, link, priority, key, snooze, read,
            if (isProject) 0 else repeat,
            Kind = editing?.Kind ?: Reminder.KIND_EVENT,
            EndDate = if (isProject) selectedEndDate!! else "",
            ProjectType = if (isProject) ProjectTypes.all[projectTypeSpinner.selectedItemPosition].key else "",
            ParentId = editing?.ParentId ?: arguments?.getString(ARG_PARENT_ID) ?: "",
        )
        val isOffline = arguments?.getBoolean(ARG_IS_OFFLINE, false) ?: false
        if (isOffline) {
            saveLocally(reminder)
        } else {
            sendReminder(reminder)
        }

        // Clear all text fields except password
        titleEditText.text.clear()
        descriptionEditText.text.clear()
        linkEditText.text.clear()
        idEditText.text.clear()

        // Reset selected date/time
        selectedDate = null
        selectedTime = null
    }

    private fun formMatchKey(): String = Template.matchKey(
        titleEditText.text.toString(), descriptionEditText.text.toString(),
        linkEditText.text.toString(), prioritySpinner.selectedItemPosition)

    private fun updateSaveTemplateButton() {
        val matches = formMatchKey() in templateKeys
        saveTemplateButton.isEnabled = !matches
        saveTemplateButton.alpha = if (matches) 0.4f else 1f
    }

    /** Keeps Save as Template disabled while the form equals an existing template (until it is edited). */
    private fun watchFormForTemplateMatch() {
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = updateSaveTemplateButton()
        }
        listOf(titleEditText, descriptionEditText, linkEditText).forEach { it.addTextChangedListener(watcher) }
        prioritySpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: View?, position: Int, id: Long) = updateSaveTemplateButton()
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val keys = withContext(Dispatchers.IO) {
                AppDb.get(requireContext()).templateDao().getAll().map { it.toDomain().matchKey() }
            }
            templateKeys.addAll(keys)
            updateSaveTemplateButton()
        }
    }

    /** Saves the form's content (no date/time/recurrence) as a reusable template. */
    private fun saveAsTemplate() {
        val title = titleEditText.text.toString()
        val description = descriptionEditText.text.toString()
        if (title.isEmpty() || description.isEmpty()) {
            Snackbar.make(requireView(), "Title and description are required", Snackbar.LENGTH_SHORT).show()
            return
        }
        val template = Template(
            Title = title,
            Description = description,
            Link = linkEditText.text.toString(),
            Priority = prioritySpinner.selectedItemPosition,
            Key = passwordEditText.text.toString(),
        )
        val isOffline = arguments?.getBoolean(ARG_IS_OFFLINE, false) ?: false
        if (isOffline) saveTemplateLocally(template) else sendTemplate(template)
    }

    private fun sendTemplate(template: Template) {
        val apiService = ApiClient.getClient().create(ApiService::class.java)
        beginLoading()
        apiService.addTemplate(template).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                endLoading()
                if (!isAdded) return
                if (response.isSuccessful) {
                    showTemplateSavedDialog(template.Title, savedLocally = false)
                } else {
                    Snackbar.make(requireView(), "Failed to save template", Snackbar.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                endLoading()
                if (!isAdded) return
                // keep it locally and let the Templates page upload it later
                saveTemplateLocally(template)
            }
        })
    }

    private fun saveTemplateLocally(template: Template) {
        val entity = TemplateEntity(
            id = "pending_${System.currentTimeMillis()}",
            title = template.Title,
            description = template.Description,
            link = template.Link,
            priority = template.Priority,
            isPending = true,
            pendingKey = template.Key,
        )
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            AppDb.get(requireContext()).templateDao().upsert(entity)
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                showTemplateSavedDialog(template.Title, savedLocally = true)
            }
        }
    }

    /** Confirms a saved template and offers to open the Templates page or keep editing. */
    private fun showTemplateSavedDialog(title: String, savedLocally: Boolean) {
        // the form now matches a template: disabled until it is edited again
        templateKeys.add(formMatchKey())
        updateSaveTemplateButton()
        val isOffline = arguments?.getBoolean(ARG_IS_OFFLINE, false) ?: false
        val message = "“$title” was added to your templates." +
            if (savedLocally) "\nIt's saved on this phone and will sync when you're online." else ""
        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.RoundedMaterialDialog)
            .setTitle("Template saved")
            .setMessage(message)
            .setNegativeButton("Stay", null)
            .setPositiveButton("Go to Templates") { _, _ ->
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, TemplateListFragment.newInstance(isOffline = isOffline))
                    .addToBackStack(null)
                    .commit()
            }
            .show()
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.colorTextPrimary))
    }

    private fun sendReminder(reminder: Reminder) {
        val apiService = ApiClient.getClient().create(ApiService::class.java)
        val call = apiService.addReminder(reminder)
        beginLoading()
        call.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                endLoading()
                if (response.isSuccessful) {
                    if (callback != null && callback is ReminderCallback?) {
                        callback?.onReminderUpdated()
                    } else {
                        Snackbar.make(
                            requireView(),
                            "Reminder added successfully",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    // e.g. "Not part of the project's date range"
                    Snackbar.make(requireView(), ApiErrors.message(response, "Failed to add reminder"), Snackbar.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                endLoading()
                Snackbar.make(requireView(), "Network error: ${t.message}", Snackbar.LENGTH_SHORT).show()
            }
        })
    }

    private fun saveLocally(reminder: Reminder) {
        // For edits, keep the original ID so the server upserts correctly on sync
        val pendingId = if (reminder.Id.isNotBlank()) reminder.Id else "pending_${System.currentTimeMillis()}"
        val dateTime = "${reminder.Date} ${reminder.Time}"  // "yyyy-MM-dd HH:mm:ss"
        val entity = ReminderEntity(
            id = pendingId,
            title = reminder.Title,
            description = reminder.Description,  // stored clean; badge shown in UI via isPending
            date = reminder.Date,
            time = dateTime,
            link = reminder.Link,
            priority = reminder.Priority,
            snooze = reminder.Snooze,
            read = "",
            isPending = true,
            pendingKey = reminder.Key,
            kind = reminder.Kind,
            endDate = reminder.EndDate,
            projectType = reminder.ProjectType,
            parentId = reminder.ParentId,
        )
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            AppDb.get(requireContext()).reminderDao().upsert(entity)
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                Snackbar.make(requireView(), "Saved locally — will sync when online", Snackbar.LENGTH_LONG).show()
                callback?.onReminderUpdated()
            }
        }
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    private fun fetchAllReminders() {
        val apiService = ApiClient.getClient().create(ApiService::class.java)
        val call = apiService.getReminders(include = 30)
        beginLoading()

        call.enqueue(object : Callback<ReminderResponse> {
            override fun onResponse(call: Call<ReminderResponse>, response: Response<ReminderResponse>) {
                endLoading()
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
                endLoading()
                view?.let { v ->
                    Snackbar.make(v, "Network error: ${t.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun generateReminderUsingAI() {
        var text = aiInputEditText.text.toString().trim()
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
        // Add date information if needed.
        // Prepopulation when user long-pressed date on mini calendar
        val prefillDate = arguments?.getString(ARG_PREFILL_DATE)
        if (!prefillDate.isNullOrBlank()) {
            selectedDate = prefillDate
        }
        selectedDate?.let { text += " on $it." }
        val req = AddReminderAiRequest(Text = text, Key = key)

        beginLoading()
        apiService.addReminderAI(req).enqueue(object : Callback<AddReminderAiResponse> {
            override fun onResponse(
                call: Call<AddReminderAiResponse>,
                response: Response<AddReminderAiResponse>
            ) {
                aiGenerateButton.isEnabled = true
                endLoading()
                if (!response.isSuccessful || response.body() == null) {
                    Snackbar.make(requireView(), "AI generate failed (${response.code()})", Snackbar.LENGTH_SHORT).show()
                    return
                }
                if (response.isSuccessful) {
                    if (callback != null && callback is ReminderCallback?) {
                        callback?.onReminderUpdated()
                    } else {
                        Snackbar.make(
                            requireView(),
                            "Reminder added successfully via AI",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            override fun onFailure(call: Call<AddReminderAiResponse>, t: Throwable) {
                aiGenerateButton.isEnabled = true
                endLoading()
                Snackbar.make(requireView(), "Network error: ${t.message}", Snackbar.LENGTH_SHORT).show()
            }
        })
    }

    private fun onVoiceClick() {
        if (isListening) {
            speechRecognizer?.stopListening()
            stopListeningUi()
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) startListening()
        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startListening() {
        try {
            speechRecognizer?.startListening(speechIntent)
        } catch (e: Exception) {
            Snackbar.make(requireView(), "Cannot start speech: ${e.message}", Snackbar.LENGTH_SHORT).show()
            stopListeningUi()
        }
    }

    private fun stopListeningUi() {
        isListening = false
        voiceButton.setImageResource(R.drawable.ic_mic)

        // see color/mic_tint.xml
        voiceButton.isActivated = false
    }

    private fun setupAutoComplete() {
        val titles = reminders.map { it.Title }.distinct()
        val descriptions = reminders.map { it.Description }.distinct()

        val titleAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, titles)
        val descriptionAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, descriptions)

        titleEditText.setAdapter(titleAdapter)
        descriptionEditText.setAdapter(descriptionAdapter)
    }

    private fun showNotificationDisabledDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Enable Notifications")
            .setMessage("Notifications are disabled. Please enable them so reminders can alert you.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                }
                startActivity(intent)
            }
            .show()
    }

    override fun onDestroyView() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        speechIntent = null
        super.onDestroyView()
    }
}
