package com.hyunwookshin.nudge

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.snackbar.Snackbar
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText

class SignupFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_signup, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userEt = view.findViewById<TextInputEditText>(R.id.signupUsernameEditText)
        val passEt = view.findViewById<TextInputEditText>(R.id.signupPasswordEditText)
        val confirmEt = view.findViewById<TextInputEditText>(R.id.signupPasswordConfirmEditText)

        view.findViewById<View>(R.id.backToLoginButton).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<View>(R.id.createAccountButton).setOnClickListener {
            val u = userEt.text?.toString()?.trim().orEmpty()
            val p = passEt.text?.toString().orEmpty()
            val c = confirmEt.text?.toString().orEmpty()

            if (u.isBlank() || p.isBlank() || c.isBlank()) {
                Snackbar.make(view, "All fields are required", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (p != c) {
                Snackbar.make(view, "Passwords do not match", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Snackbar.make(view, "Signup TODO: wire API", Snackbar.LENGTH_SHORT).show()
        }
    }
}