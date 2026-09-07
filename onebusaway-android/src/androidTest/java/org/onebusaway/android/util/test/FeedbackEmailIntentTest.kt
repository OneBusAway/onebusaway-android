package org.onebusaway.android.util.test

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import androidx.core.net.MailTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.R
import org.onebusaway.android.util.ExternalIntents

@RunWith(AndroidJUnit4::class)
class FeedbackEmailIntentTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    // Capture the real launch without opening a mail app or sending any feedback.
    private class EmailContext(val noEmailApp: Boolean = false) :
        ContextWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext
        ) {
        lateinit var launched: Intent

        override fun startActivity(intent: Intent) {
            launched = intent
            if (noEmailApp) throw ActivityNotFoundException("No email app installed")
        }
    }

    @Test
    fun feedbackOpensEmailComposerWithRecipientAndReport() {
        val context = EmailContext()
        val email = context.getString(R.string.ri_app_feedback_email)
        instrumentation.runOnMainSync { ExternalIntents.sendEmail(context, email, null) }

        val intent = context.launched
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertNull(intent.type)
        val emailFilter = IntentFilter(Intent.ACTION_SENDTO).apply { addDataScheme("mailto") }
        assertTrue(emailFilter.match(context.contentResolver, intent, false, "test") >= 0)
        val shareFilter = IntentFilter(Intent.ACTION_SEND).apply { addDataType("*/*") }
        assertTrue(shareFilter.match(context.contentResolver, intent, false, "test") < 0)

        // AndroidX implements RFC 6068; android.net.MailTo decodes before splitting query fields
        // and misreads encoded ampersands inside a report body as additional headers.
        val draft = MailTo.parse(requireNotNull(intent.data))
        assertEquals(email, draft.to)
        assertArrayEquals(arrayOf(email), intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals(context.getString(R.string.bug_report_subject, context.getString(R.string.app_name)), draft.subject)
        assertEquals(intent.getStringExtra(Intent.EXTRA_SUBJECT), draft.subject)
        assertEquals(intent.getStringExtra(Intent.EXTRA_TEXT), draft.body)
        assertFalse(draft.body.isNullOrBlank())
    }

    @Test
    fun locationAndTripUrlSurviveMailtoEncodingWithoutAddingHeaders() {
        val context = EmailContext()
        val email = "feedback+android@example.com"
        val location = "47.61, -122.33 (Pine & 3rd #1 / café)"
        val tripUrl = "https://example.com/plan?from=A%20B&to=C+D&subject=other#route"
        instrumentation.runOnMainSync {
            ExternalIntents.sendEmail(context, email, location, tripUrl, true)
        }

        val draft = MailTo.parse(requireNotNull(context.launched.data))
        assertEquals(email, draft.to)
        assertEquals(
            context.getString(R.string.bug_report_subject_trip_plan_fail, context.getString(R.string.app_name)),
            draft.subject
        )
        assertEquals(context.launched.getStringExtra(Intent.EXTRA_TEXT), draft.body)
        val body = requireNotNull(draft.body)
        assertTrue(body.contains(location))
        assertTrue(body.contains(tripUrl))
        assertEquals(setOf("to", "subject", "body"), draft.headers?.keys)
    }

    @Test
    fun missingEmailAppIsHandledWithoutLaunchingAShareChooser() {
        val context = EmailContext(noEmailApp = true)
        instrumentation.runOnMainSync {
            ExternalIntents.sendEmail(context, "feedback@example.com", null)
        }
        assertEquals(Intent.ACTION_SENDTO, context.launched.action)
    }
}
