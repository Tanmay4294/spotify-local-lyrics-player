package com.example.spotlyrics.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.spotlyrics.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SpotifyPkceAuthManager private constructor(
    private val context: Context,
    private val storage: SecureTokenStorage,
    private val tokenApi: SpotifyTokenApi
) {
    companion object {
        const val REDIRECT_URI = "spotlyrics://callback"
        private const val AUTH_ENDPOINT = "https://accounts.spotify.com/authorize"

        @Volatile
        private var instance: SpotifyPkceAuthManager? = null

        fun getInstance(context: Context): SpotifyPkceAuthManager {
            return instance ?: synchronized(this) {
                val appCtx = context.applicationContext
                instance ?: SpotifyPkceAuthManager(
                    appCtx,
                    SecureTokenStorage.getInstance(appCtx),
                    SpotifyTokenApi
                ).also { instance = it }
            }
        }
    }

    private val _authState = MutableStateFlow<SpotifyAuthState>(SpotifyAuthState.SignedOut)
    val authState: StateFlow<SpotifyAuthState> = _authState.asStateFlow()

    private val refreshMutex = Mutex()

    init {
        checkInitialAuthState()
    }

    private fun checkInitialAuthState() {
        val storedToken = storage.get()
        if (storedToken != null) {
            _authState.value = SpotifyAuthState.Authorized(storedToken.expiresAtEpochSeconds)
        } else {
            _authState.value = SpotifyAuthState.SignedOut
        }
    }

    fun startAuthorization(context: Context) {
        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
        if (clientId.isBlank()) {
            _authState.value = SpotifyAuthState.Error("Spotify Client ID is missing in local.properties")
            return
        }

        try {
            val verifier = PkceUtil.generateCodeVerifier()
            val challenge = PkceUtil.generateCodeChallenge(verifier)

            storage.saveCodeVerifier(verifier)

            val authUri = Uri.parse(AUTH_ENDPOINT).buildUpon()
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("code_challenge", challenge)
                .build()

            _authState.value = SpotifyAuthState.Authorizing

            val intent = Intent(Intent.ACTION_VIEW, authUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _authState.value = SpotifyAuthState.Error("Failed to start authorization: ${e.localizedMessage}")
        }
    }

    fun handleCallback(intent: Intent, scope: CoroutineScope) {
        val uri = intent.data ?: run {
            _authState.value = SpotifyAuthState.Error("Missing callback URI")
            return
        }

        val scheme = uri.scheme
        val host = uri.host
        if (scheme != "spotlyrics" || host != "callback") {
            _authState.value = SpotifyAuthState.Error("Invalid redirect callback")
            return
        }

        val error = uri.getQueryParameter("error")
        if (error != null) {
            storage.clearCodeVerifier()
            _authState.value = SpotifyAuthState.Error("Authorization error: $error")
            return
        }

        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            storage.clearCodeVerifier()
            _authState.value = SpotifyAuthState.Error("Missing authorization code")
            return
        }

        val verifier = storage.getCodeVerifier()
        storage.clearCodeVerifier()
        if (verifier.isNullOrEmpty()) {
            _authState.value = SpotifyAuthState.Error("Missing PKCE code verifier")
            return
        }

        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
        _authState.value = SpotifyAuthState.ExchangingCode

        scope.launch {
            val result = tokenApi.exchangeCodeForToken(
                code = code,
                verifier = verifier,
                clientId = clientId,
                redirectUri = REDIRECT_URI
            )
            result.onSuccess { token ->
                storage.save(token)
                _authState.value = SpotifyAuthState.Authorized(token.expiresAtEpochSeconds)
            }.onFailure { exception ->
                _authState.value = SpotifyAuthState.Error("Token exchange failed: ${exception.message}")
            }
        }
    }

    suspend fun getValidAccessToken(): String? = refreshMutex.withLock {
        val storedToken = storage.get() ?: run {
            _authState.value = SpotifyAuthState.SignedOut
            return null
        }

        if (!storedToken.isExpired()) {
            return storedToken.accessToken
        }

        val refreshToken = storedToken.refreshToken
        if (refreshToken.isNullOrEmpty()) {
            signOut()
            return null
        }

        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
        val refreshResult = tokenApi.refreshToken(refreshToken, clientId)

        return refreshResult.fold(
            onSuccess = { newToken ->
                storage.save(newToken)
                _authState.value = SpotifyAuthState.Authorized(newToken.expiresAtEpochSeconds)
                newToken.accessToken
            },
            onFailure = {
                signOut()
                null
            }
        )
    }

    fun signOut() {
        storage.clear()
        storage.clearCodeVerifier()
        _authState.value = SpotifyAuthState.SignedOut
    }
}
