package com.hyunwookshin.nudge

import androidx.lifecycle.lifecycleScope
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity(), ReminderCallback, LoginFragment.LoginCallback {

    private lateinit var db: AppDb

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState != null) return

        val token = AuthStore.getToken(this)

        if (!token.isNullOrBlank()) {
            // User already logged in
            loadFragment(ReminderListFragment())
        } else {
            // No token -> show login page
            loadFragment(LoginFragment())
        }
    }

    override fun onLoginSuccess() {
        // clear backstack and go to reminders
        supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        loadFragment(ReminderListFragment())
    }

    private fun loadFragment(fragment: Fragment) {
        val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.commit()
    }

    override fun onShowReminders() {
        replaceFragment(ReminderListFragment())
    }
    override fun onEditReminder(reminder: Reminder, isOffline: Boolean) {
        val reminderFragment = ReminderFragment.newInstance(reminder, isOffline)
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

    override fun onReminderUpdated() {
        db = AppDb.get(this@MainActivity)
        lifecycleScope.launch {
            val reminders = db.reminderDao().getAll().map { it.toDomain() }

            for (r in reminders) {
                ReminderScheduler.cancelReminder(this@MainActivity, r.Id)
                FiredStore.clear(this@MainActivity, r.Id)
                ReminderScheduler.scheduleReminder(this@MainActivity, r)
            }
        }
        supportFragmentManager
            .popBackStack()
    }

    override fun onAbortEditReminder() {
        supportFragmentManager
            .popBackStack()
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
        if (reminder.isPending) {
            // Pending reminders only exist in the local DB — the server has never seen them,
            // so there's nothing to DELETE on the server. Just remove from Room directly.
            lifecycleScope.launch(Dispatchers.IO) {
                AppDb.get(this@MainActivity).reminderDao().deleteById(reminder.Id)
                withContext(Dispatchers.Main) {
                    ReminderScheduler.cancelReminder(this@MainActivity, reminder.Id)
                    refreshFragment()
                }
            }
            return
        }
        val apiService = ApiClient.getClient().create(ApiService::class.java)
        val call = apiService.deleteReminder(reminder)
        call.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    ReminderScheduler.cancelReminder(this@MainActivity, reminder.Id)
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
