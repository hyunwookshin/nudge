package com.hyunwookshin.nudge

import android.graphics.Color
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.*
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment

class TermsOfServiceFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val scrollView = ScrollView(requireContext())
        scrollView.setBackgroundColor(Color.WHITE)

        val textView = TextView(requireContext())
        textView.setTextColor(Color.BLACK)
        textView.textSize = 14f
        textView.setPadding(40, 40, 40, 40)
        textView.text = TERMS_TEXT
        textView.movementMethod = LinkMovementMethod.getInstance()

        scrollView.addView(textView)

        return scrollView
    }

    companion object {

        private const val TERMS_TEXT = """
Nudge – Terms of Service
Last Updated: February 2026

By creating an account or using Nudge, you agree to the following Terms of Service. If you do not agree, please do not use the app.

1. Description of Service

Nudge is a reminder application that allows users to create, store, and receive notifications for scheduled reminders.

Features may include:
• Local notifications
• Cloud synchronization
• Encrypted storage
• AI-generated reminder parsing
• Optional third-party links (e.g., Google Maps)

The Service may evolve over time.

2. Eligibility

You must be at least 13 years old to use this app, and not based in E.U.

3. Accounts and Security

You are responsible for maintaining the confidentiality of your credentials and all activity under your account.

Nudge uses encryption where enabled, but no system is guaranteed to be 100% secure.

4. Encryption and Data Storage

Reminder data may be encrypted using cryptographic methods tied to your account credentials.

Loss of your password may result in permanent loss of encrypted data.

The Service does not guarantee recovery of encrypted information.

5. Notifications and Reliability

Reminder delivery depends on device settings, OS behavior, battery optimization, and notification permissions.

Nudge does not guarantee that reminders will always be delivered.

You remain responsible for important deadlines and obligations.

6. AI-Generated Content

AI parsing may produce incorrect or incomplete results.

You are responsible for reviewing reminders before relying on them.

7. Third-Party Services

The app may interact with third-party services including cloud providers, AI APIs, and operating system notification systems.

Nudge is not responsible for their reliability or policies.

8. Prohibited Use

You agree not to misuse, disrupt, reverse engineer, or abuse the Service.

9. Disclaimer of Warranties

The Service is provided "AS IS" without warranties of any kind.

10. Limitation of Liability

To the maximum extent permitted by law, Nudge shall not be liable for missed reminders, data loss, or indirect damages.

Total liability shall not exceed $10 USD.

11. Changes to Terms

These Terms may be updated periodically. Continued use constitutes acceptance.

"""
    }
}