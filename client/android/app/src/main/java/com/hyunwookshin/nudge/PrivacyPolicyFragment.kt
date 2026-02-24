package com.hyunwookshin.nudge

import android.graphics.Color
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.*
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment

class PrivacyPolicyFragment : Fragment() {

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
        textView.text = PRIVACY_TEXT
        textView.movementMethod = LinkMovementMethod.getInstance()

        scrollView.addView(textView)

        return scrollView
    }

    companion object {

        private const val PRIVACY_TEXT = """
Nudge – Privacy Policy
Last Updated: February 2026

This Privacy Policy describes how Nudge ("we", "us", or "our") collects, uses, stores, and protects your information when you use the Nudge reminder application ("the App"). By using the App, you agree to the practices described in this policy.

1. Information We Collect

a) Account Information
When you create an account, we collect:
• Username (used as your account identifier)
• Password (stored as a salted cryptographic hash — never in plaintext)
• Authentication tokens (stored securely on your device and server)

b) Reminder Data
We collect the content you enter as reminders, including:
• Reminder title and description
• Scheduled date and time
• Priority level
• Optional links or location references
• Snooze and completion status

c) Configuration Data
We store user preferences such as:
• Timezone
• Notification delivery preferences
• Email address (if you configure email notifications)

d) Device and Usage Information
We do not collect analytics, advertising identifiers, or behavioral tracking data. We do not use crash reporting SDKs or third-party analytics frameworks.

2. How We Use Your Information

We use your information solely to:
• Authenticate your account and protect it from unauthorized access
• Store, retrieve, and deliver your reminders
• Send reminder notifications via your configured channels (in-app, email)
• Parse natural language reminder input using AI (see Section 5)
• Apply your timezone and scheduling preferences

We do not use your data for advertising, profiling, or sale to third parties.

3. Data Storage and Retention

a) Server Storage
Reminder and account data is stored on our server. Data may be encrypted at rest using cryptographic methods tied to your account credentials. If encryption is enabled, loss of your password may result in permanent, unrecoverable loss of your data — we cannot decrypt it on your behalf.

b) Local Storage
The Android app maintains a local database (Room) on your device for offline access. This data remains on your device and is subject to your device's own security settings.

c) Retention
Your data is retained for as long as your account remains active. If you wish to have your data deleted, contact us at the address in Section 9. We will process deletion requests within a reasonable timeframe.

4. Notifications and Communication

If you configure email notifications, we use your email address exclusively to deliver reminder alerts. We do not send marketing emails, newsletters, or promotional content.

Reminder delivery relies on your device OS, notification permissions, and network availability. We cannot guarantee delivery of every reminder.

5. AI-Powered Features

When you use the natural language reminder input feature, the text you enter is transmitted to the OpenAI API for processing. OpenAI parses your input to extract structured reminder fields (title, date, time, location).

• Only the reminder input text you explicitly submit for AI parsing is sent to OpenAI.
• OpenAI's data handling is governed by their own Privacy Policy (openai.com/privacy).
• We do not send your account credentials, email address, or other personal data to OpenAI.
• AI parsing is optional. You may create reminders manually without using this feature.

6. Data Sharing and Disclosure

We do not sell, rent, or share your personal information with third parties except:
• OpenAI: reminder input text sent for AI parsing (see Section 5)
• Cloud infrastructure providers: server hosting required to operate the service
• Legal obligations: if required by law, court order, or to protect the rights and safety of users

7. Security

We implement the following security measures:
• Passwords are never stored in plaintext — only salted SHA-256 hashes
• Authentication tokens are hashed before storage
• Token rotation: only the most recent tokens are retained per user
• Optional encrypted data storage using cryptographic keys derived from your credentials
• HTTPS/TLS encryption for all data in transit

No system is 100% secure. You are responsible for keeping your credentials confidential.

8. Children's Privacy

The App is not intended for users under the age of 13. We do not knowingly collect personal information from children under 13. If we become aware that a child under 13 has provided us with personal information, we will delete it promptly.

9. Geographic Restrictions

This app is not intended for use by individuals located in the European Union (EU) or European Economic Area (EEA). We do not offer the App to EU/EEA residents and do not accept signups from those regions. If you are located in the EU/EEA, please do not use this App.

10. Your Rights

You may:
• Access the reminder and account data stored for your account by using the App
• Delete individual reminders directly within the App
• Request full account and data deletion by contacting us (see Section 11)
• Stop using the App at any time

We will respond to verified data deletion requests within a reasonable timeframe.

11. Contact

If you have questions about this Privacy Policy or wish to request data deletion, contact us at:

hyunwookshin.dev@gmail.com

12. Changes to This Policy

We may update this Privacy Policy from time to time. The "Last Updated" date at the top of this page reflects the most recent revision. Continued use of the App after changes constitutes your acceptance of the updated policy.

"""
    }
}
