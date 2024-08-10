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

class ReminderListFragment : Fragment() {

    private lateinit var reminderAdapter: ReminderAdapter
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

    private fun fetchReminders() {
        val apiService = ApiClient.getClient().create(ApiService::class.java)
        apiService.getReminders().enqueue(object : Callback<ReminderResponse> {
            override fun onResponse(call: Call<ReminderResponse>, response: Response<ReminderResponse>) {
                if (response.isSuccessful) {
                    response.body()?.reminders?.let { reminders ->
                        Log.d("ReminderListFragment", "Reminders fetched: ${reminders.size}")

                        reminderAdapter.setReminders(reminders)
                        progressBar.visibility = View.GONE
                    }
                } else {
                    Snackbar.make(requireView(), "Failed to load reminders", Snackbar.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ReminderResponse>, t: Throwable) {
                Snackbar.make(requireView(), "Network error: ${t.message}", Snackbar.LENGTH_SHORT).show()
            }
        })
    }
}
