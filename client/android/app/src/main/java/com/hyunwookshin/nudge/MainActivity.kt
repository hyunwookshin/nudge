package com.hyunwookshin.nudge

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity(), ReminderCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            loadFragment(ReminderFragment())
        }
    }

    private fun loadFragment(fragment: Fragment) {
        val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.commit()
    }

    override fun onShowReminders() {
        replaceFragment(ReminderListFragment())
    }
    override fun onEditReminder(reminder: Reminder) {
        val reminderFragment = ReminderFragment.newInstance(reminder)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, reminderFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onCopyReminder(reminder: Reminder) {
        // clearing the id forces new instance, instead of updating
        // the old instance.
        reminder.Id = ""
        onEditReminder(reminder)
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun refreshFragment() {
        val current = supportFragmentManager.findFragmentById(R.id.fragment_container)
        (current as? Refreshable)?.refresh()
    }

    override fun onDeleteReminder(reminder: Reminder) {
        val apiService = ApiClient.getClient().create(ApiService::class.java)
        val call = apiService.deleteReminder(reminder)
        call.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    refreshFragment()
                } else {
                    Log.i("main", "Failed to remove reminder: ${response.raw()}")
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.i("main", "Network error: ${t.message}")
            }
        })
    }
}
