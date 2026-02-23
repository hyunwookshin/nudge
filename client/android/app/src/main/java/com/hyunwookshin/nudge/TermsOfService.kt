package com.hyunwookshin.nudge

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment

class TermsOfServiceFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val scroll = ScrollView(requireContext())
        scroll.setBackgroundColor(android.graphics.Color.WHITE)

        val text = TextView(requireContext())
        text.setPadding(40, 40, 40, 40)
        text.setTextColor(android.graphics.Color.BLACK)
        text.textSize = 14f

        text.text = """
            Terms of Service
            
            Welcome to Nudge.
            
            By using this app you agree that:
            
            • You are responsible for your reminders.
            • We are not liable for missed reminders.
            • Your data may be encrypted and stored securely.
            • Service may change at any time.
            
            (More legal boring stuff here later.)
        """.trimIndent()

        scroll.addView(text)
        return scroll
    }
}