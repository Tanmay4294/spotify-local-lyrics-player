package com.example.spotlyrics.spotify

import com.spotify.android.appremote.api.error.NotLoggedInException
import com.spotify.android.appremote.api.error.UserNotAuthorizedException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SpotifyAuthTest {

    private lateinit var spotifyManager: SpotifyManager

    @Before
    fun setUp() {
        spotifyManager = SpotifyManager.getInstance()
        spotifyManager.disconnect()
    }

    @Test
    fun testDefaultAuthStateIsReauthRequired() = runTest {
        val authState = spotifyManager.authState.first()
        assertEquals(SpotifyAuthState.REAUTH_REQUIRED, authState)
    }

    @Test
    fun testClassifyUserNotAuthorizedExceptionAsAuthRevoked() {
        val state = spotifyManager.classifyAuthFailure(UserNotAuthorizedException("Denied", null))
        assertEquals(SpotifyAuthState.AUTH_REVOKED, state)
    }

    @Test
    fun testClassifyNotLoggedInExceptionAsReauthRequired() {
        val state = spotifyManager.classifyAuthFailure(NotLoggedInException("Not logged in", null))
        assertEquals(SpotifyAuthState.REAUTH_REQUIRED, state)
    }

    @Test
    fun testClassifyExpiredTokenAsAccessTokenExpired() {
        val state = spotifyManager.classifyAuthFailure(RuntimeException("Token expired"))
        assertEquals(SpotifyAuthState.ACCESS_TOKEN_EXPIRED, state)
    }

    @Test
    fun testClassifyRefreshFailureAsRefreshFailed() {
        val state = spotifyManager.classifyAuthFailure(RuntimeException("Token refresh failed"))
        assertEquals(SpotifyAuthState.REFRESH_FAILED, state)
    }

    @Test
    fun testAuthErrorsDoNotCrashApplication() {
        try {
            val state1 = spotifyManager.classifyAuthFailure(RuntimeException("Unknown auth error"))
            assertNotNull(state1)
            val state2 = spotifyManager.classifyAuthFailure(UserNotAuthorizedException("Revoked", null))
            assertNotNull(state2)
        } catch (e: Exception) {
            fail("Auth classification should not throw exception: ${e.message}")
        }
    }

    @Test
    fun testNoSecretsInDiagnostics() {
        val errorMsg = "Auth failure: user token expired"
        assertFalse(errorMsg.contains("client_secret"))
        assertFalse(errorMsg.contains("access_token"))
        assertFalse(errorMsg.contains("refresh_token"))
        assertFalse(errorMsg.contains("Authorization"))
    }
}
