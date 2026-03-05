package com.hyunwookshin.nudge

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AccountFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_account, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val usernameText = view.findViewById<TextView>(R.id.usernameText)
        val currentPasswordEt = view.findViewById<TextInputEditText>(R.id.currentPasswordEditText)
        val newPasswordEt = view.findViewById<TextInputEditText>(R.id.newPasswordEditText)
        val confirmPasswordEt = view.findViewById<TextInputEditText>(R.id.confirmPasswordEditText)
        val changePasswordButton = view.findViewById<MaterialButton>(R.id.changePasswordButton)
        val backButton = view.findViewById<MaterialButton>(R.id.backButton)
        val deleteAccountButton = view.findViewById<MaterialButton>(R.id.deleteAccountButton)

        usernameText.text = AuthStore.getUsername(requireContext()) ?: ""

        backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        deleteAccountButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext(), R.style.RoundedMaterialDialog)
                .setTitle("Delete Account")
                .setMessage("This will permanently delete your account and all your reminders. This action cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    deleteAccountButton.isEnabled = false
                    val api = ApiClient.getClient().create(ApiService::class.java)
                    api.deleteAccount().enqueue(object : Callback<MessageResponse> {
                        override fun onResponse(call: Call<MessageResponse>, response: Response<MessageResponse>) {
                            if (!isAdded) return
                            if (response.isSuccessful) {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    AppDb.get(requireContext()).reminderDao().clearAll()
                                }
                                AuthStore.clear(requireContext())
                                parentFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
                                parentFragmentManager.beginTransaction()
                                    .replace(R.id.fragment_container, LoginFragment())
                                    .commit()
                            } else {
                                deleteAccountButton.isEnabled = true
                                val msg = response.body()?.message ?: "Failed (${response.code()})"
                                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                        override fun onFailure(call: Call<MessageResponse>, t: Throwable) {
                            if (!isAdded) return
                            deleteAccountButton.isEnabled = true
                            Toast.makeText(requireContext(), "Network error", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        changePasswordButton.setOnClickListener {
            val current = currentPasswordEt.text?.toString() ?: ""
            val new = newPasswordEt.text?.toString() ?: ""
            val confirm = confirmPasswordEt.text?.toString() ?: ""

            if (current.isBlank() || new.isBlank() || confirm.isBlank()) {
                Toast.makeText(requireContext(), "All fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (new != confirm) {
                Toast.makeText(requireContext(), "New passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            changePasswordButton.isEnabled = false

            val api = ApiClient.getClient().create(ApiService::class.java)
            api.changePassword(ChangePasswordRequest(current, new, confirm))
                .enqueue(object : Callback<MessageResponse> {
                    override fun onResponse(call: Call<MessageResponse>, response: Response<MessageResponse>) {
                        if (!isAdded) return
                        changePasswordButton.isEnabled = true
                        val msg = response.body()?.message ?: if (response.isSuccessful) "Password changed" else "Failed (${response.code()})"
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        currentPasswordEt.text?.clear()
                        newPasswordEt.text?.clear()
                        confirmPasswordEt.text?.clear()
                    }

                    override fun onFailure(call: Call<MessageResponse>, t: Throwable) {
                        if (!isAdded) return
                        changePasswordButton.isEnabled = true
                        Toast.makeText(requireContext(), "Network error", Toast.LENGTH_SHORT).show()
                        currentPasswordEt.text?.clear()
                        newPasswordEt.text?.clear()
                        confirmPasswordEt.text?.clear()
                    }
                })
        }
    }
}
