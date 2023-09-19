package apis.google

import Main
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.calendar.CalendarScopes
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader

object GoogleAuth {
    private val jsonFactory: JsonFactory = GsonFactory.getDefaultInstance()
    private val scopes = listOf(CalendarScopes.CALENDAR_READONLY)
    private const val tokensDirectoryPath = "tokens"
    private val redirectUri: String
    private val receiver: LocalServerReceiver
    private val flow: GoogleAuthorizationCodeFlow

    init {
        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        val input = this.javaClass.getResourceAsStream("/credentials.json")

        val clientSecrets = GoogleClientSecrets.load(jsonFactory, input?.let { InputStreamReader(it) })

        receiver = LocalServerReceiver.Builder().setPort(8888).build()

        redirectUri = receiver.redirectUri

        flow = GoogleAuthorizationCodeFlow.Builder(httpTransport, jsonFactory, clientSecrets, scopes)
            .setDataStoreFactory(FileDataStoreFactory(File(tokensDirectoryPath)))
            .setAccessType("offline")
            .build()
    }

    fun getSavedCredentials(userId: String): Credential? {
        // Check if the user already has credentials saved
        return flow.loadCredential(userId)
    }

    fun hasSavedCredentials(userId: String): Boolean {
        return getSavedCredentials(userId) != null
    }

    fun generateCredentialUrl(): String {
        // Generate the authorization URL needed to generate new credentials
        return flow.newAuthorizationUrl()
            .setRedirectUri(redirectUri)
            .build()
    }

    fun removeCredentials(userId: String) {
        flow.credentialDataStore.delete(userId)
    }

    fun cancelWait() = receiver.stop()

    fun waitForCredentials(userId: String): Credential? {
        Main.report(userId)
        try {
            val code = receiver.waitForCode() ?: return null
            Main.report("code$code")
            val response = flow.newTokenRequest(code)
                .setRedirectUri(redirectUri)
                .execute()
            Main.report("response $response")
            return flow.createAndStoreCredential(response, userId)
        } finally {
            receiver.stop()
        }
    }
}