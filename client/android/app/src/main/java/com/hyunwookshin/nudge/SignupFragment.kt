package com.hyunwookshin.nudge

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.google.android.material.snackbar.Snackbar
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class SignupFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_signup, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val usernameEt = view.findViewById<TextInputEditText>(R.id.signupUsernameEditText)
        val passwordEt = view.findViewById<TextInputEditText>(R.id.signupPasswordEditText)
        val confirmEt = view.findViewById<TextInputEditText>(R.id.signupPasswordConfirmEditText)
        val signupButton = view.findViewById<MaterialButton>(R.id.createAccountButton)
        val statusText = view.findViewById<TextView>(R.id.signupStatusText)

        signupButton.setOnClickListener {

            val user = usernameEt.text?.toString()?.trim() ?: ""
            val pass = passwordEt.text?.toString()?.trim() ?: ""
            val pass2 = confirmEt.text?.toString()?.trim() ?: ""

            if (user.isEmpty() || pass.isEmpty() || pass2.isEmpty()) {
                statusText.text = "Please fill all fields"
                statusText.visibility = View.VISIBLE
                return@setOnClickListener
            }

            val api = ApiClient.getClient().create(ApiService::class.java)

            api.signup(SignupRequest(user, pass, pass2))
                .enqueue(object: Callback<LoginResponse> {

                    override fun onResponse(
                        call: Call<LoginResponse>,
                        resp: Response<LoginResponse>
                    ) {
                        if (!isAdded) return

                        if (resp.isSuccessful) {
                            val body = resp.body() ?: return

                            // Save token + username
                            AuthStore.saveToken(
                                requireContext(),
                                body.token,
                                body.username
                            )

                            // Go directly to Reminder page
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.fragment_container, ReminderListFragment())
                                .commit()

                        } else {
                            statusText.text = "Signup failed (${resp.code()})"
                            statusText.visibility = View.VISIBLE
                        }
                    }

                    override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                        if (!isAdded) return
                        statusText.text = "Network error"
                        statusText.visibility = View.VISIBLE
                    }
                })
        }
    }
}