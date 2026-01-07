package com.hyunwookshin.nudge

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Callback
import retrofit2.Response
import com.google.android.material.snackbar.Snackbar
import retrofit2.Call
import java.util.Calendar
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val reminderDateTimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") // your Android field

class ReminderListFragment : Fragment(), Refreshable {

    private lateinit var reminderAdapter: ReminderAdapter
    private lateinit var miniCalendarAdapter: MiniCalendarAdapter
    private lateinit var dateButton: Button
    private lateinit var progressBar: ProgressBar
    private var reminderCallback: ReminderCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_reminder_list, container, false)
        dateButton = view.findViewById(R.id.dateButton)
        dateButton.setOnClickListener { showDatePicker() }
        progressBar = view.findViewById(R.id.progressBar)
        val calendar = Calendar.getInstance()
        val todayDate = "${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.YEAR)}"
        dateButton.text = "Show Calendar (" + todayDate + ")"
        return view
    }

    override fun refresh() {
        fetchReminders()
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val datePicker = DatePickerDialog(
            requireContext(),
            { _, _, _, _ -> },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    // Set MainActivity as the callback
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is ReminderCallback) {
            reminderCallback = context
        } else {
            throw RuntimeException("$context must implement ReminderCallback")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMiniCalendar(view)

        // Ensure that the Edit button is wired to the callback in MainActivity.
        reminderAdapter = ReminderAdapter().apply {
            reminderCallback?.let {
                setReminderCallback(it)
            }
        }
        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = reminderAdapter
        progressBar.visibility = View.VISIBLE
        fetchReminders()
    }

    override fun onDetach() {
        super.onDetach()
        reminderCallback = null
    }

    private fun fetchReminders() {
        val apiService = ApiClient.getClient().create(ApiService::class.java)
        apiService.getReminders().enqueue(object : Callback<ReminderResponse> {
            override fun onResponse(call: Call<ReminderResponse>, response: Response<ReminderResponse>) {
                if (!isAdded || view == null) return
                progressBar.visibility = View.GONE
                if (response.isSuccessful) {
                    val reminders = response.body()?.reminders ?: emptyList()
                    Log.d("ReminderListFragment", "Reminders fetched: ${reminders.size}")

                    reminderAdapter.setReminders(reminders)
                    miniCalendarAdapter.submit(buildMiniCalendarDays(reminders))

                } else {
                    Snackbar.make(requireView(), "Failed to load reminders", Snackbar.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ReminderResponse>, t: Throwable) {
                if (!isAdded || view == null) return
                Snackbar.make(requireView(), "Network error: ${t.message}", Snackbar.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
            }
        })
    }


    private fun setupMiniCalendar(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.miniCalendarRv)
        rv.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 7)
        miniCalendarAdapter = MiniCalendarAdapter { clickedDate ->
            // Optional: scroll list to date, or filter, or open calendar view
        }
        rv.adapter = miniCalendarAdapter
    }

    private fun buildMiniCalendarDays(reminders: List<Reminder>): List<DayState> {
        val today = LocalDate.now()
        val start = today.minusDays(today.dayOfWeek.value % 7L)
        val end = start.plusDays(20) // 3 weeks (21 days)

        // date -> (count, hasHigh, hasLow)
        val agg = mutableMapOf<LocalDate, Triple<Int, Boolean, Boolean>>()

        for (r in reminders) {
            // Your Android Reminder.Time looks like "yyyy-MM-dd HH:mm" (after your fixes)
            val date = runCatching {
                LocalDate.parse(r.Time.take(10)) // "yyyy-MM-dd"
            }.getOrNull() ?: continue

            if (date.isBefore(start) || date.isAfter(end)) continue

            val prev = agg[date] ?: Triple(0, false, false)
            val count = prev.first + 1
            val hasHigh = prev.second || (r.Priority <= 1)
            val hasLow = prev.third || (r.Priority > 1)
            agg[date] = Triple(count, hasHigh, hasLow)
        }

        return (0..20).map { offset ->
            val d = start.plusDays(offset.toLong())
            val triple = agg[d] ?: Triple(0, false, false)
            DayState(
                date = d,
                count = triple.first,
                hasHigh = triple.second,
                hasLow = triple.third
            )
        }
    }

}
