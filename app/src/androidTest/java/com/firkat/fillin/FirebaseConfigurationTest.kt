package com.firkat.fillin

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the real SDK wiring without signing in or contacting the budget server. */
@RunWith(AndroidJUnit4::class)
class FirebaseConfigurationTest {
    @Test
    fun firebaseAndGoogleCredentialsAreAvailableWhenConfigured() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (!BuildConfig.FIREBASE_CONFIGURED) {
            assertTrue(FirebaseApp.getApps(context).isEmpty())
            return
        }

        val app = FirebaseApp.getInstance()
        assertEquals("fillin-77e97", app.options.projectId)
        assertTrue(app.options.applicationId.isNotBlank())
        assertEquals(app, FirebaseAuth.getInstance(app).app)
        assertNotNull(CredentialManager.create(context))

        // Google Sign-In needs the Web OAuth client generated after enabling the provider.
        val clientIdResource = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        assertTrue("Google Sign-In Web client is missing from google-services.json", clientIdResource != 0)
        assertTrue(context.getString(clientIdResource).endsWith(".apps.googleusercontent.com"))
    }
}
