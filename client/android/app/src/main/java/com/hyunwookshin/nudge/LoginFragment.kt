package com.hyunwookshin.nudge

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginFragment : Fragment() {

    interface LoginCallback {
        fun onLoginSuccess()
    }

    private var callback: LoginCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        callback = activity as? LoginCallback
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val signupBtn = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.signupButton)
        signupBtn.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SignupFragment())
                .addToBackStack(null)
                .commit()
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_login, container, false)

        val usernameEt = v.findViewById<EditText>(R.id.usernameEditText)
        val passwordEt = v.findViewById<EditText>(R.id.passwordEditText)
        val loginBtn = v.findViewById<Button>(R.id.loginButton)

        loginBtn.setOnClickListener {
            val username = usernameEt.text.toString().trim()
            val password = passwordEt.text.toString()

            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(requireContext(), "Enter username + password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginBtn.isEnabled = false

            val api = ApiClient.getClient().create(ApiService::class.java)
            api.login(LoginRequest(username, password))
                .enqueue(object : Callback<LoginResponse> {
                    override fun onResponse(call: Call<LoginResponse>, resp: Response<LoginResponse>) {
                        loginBtn.isEnabled = true

                        if (!resp.isSuccessful || resp.body() == null) {
                            Toast.makeText(requireContext(), "Login failed (${resp.code()})", Toast.LENGTH_SHORT).show()
                            return
                        }

                        val body = resp.body()!!
                        AuthStore.saveToken(requireContext(), body.token, body.username)

                        callback?.onLoginSuccess()
                    }

                    override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                        loginBtn.isEnabled = true
                        Toast.makeText(requireContext(), "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                })
        }

        return v
    }
}