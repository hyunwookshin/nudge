package com.hyunwookshin.nudge

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Callback
import retrofit2.Response
import com.google.android.material.snackbar.Snackbar
import retrofit2.Call

class ReminderListFragment : Fragment() {

    private lateinit var reminderAdapter: ReminderAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_reminder_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        reminderAdapter = ReminderAdapter()
        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = reminderAdapter

        fetchReminders()
    }

    private fun fetchReminders() {
        val apiService = ApiClient.getClient().create(ApiService::class.java)
        apiService.getReminders().enqueue(object : Callback<ReminderResponse> {
            override fun onResponse(call: Call<ReminderResponse>, response: Response<ReminderResponse>) {
                if (response.isSuccessful) {
                    response.body()?.reminders?.let { reminders ->
                        reminderAdapter.setReminders(reminders)
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
