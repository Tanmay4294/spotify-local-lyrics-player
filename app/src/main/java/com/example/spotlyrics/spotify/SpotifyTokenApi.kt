package com.example.spotlyrics.spotify

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object SpotifyTokenApi {
    private const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"

    suspend fun exchangeCodeForToken(
        code: String,
        verifier: String,
        clientId: String,
        redirectUri: String
    ): Result<SpotifyToken> = withContext(Dispatchers.IO) {
        try {
            val params = mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to redirectUri,
                "client_id" to clientId,
                "code_verifier" to verifier
            )
            val responseJson = postFormUrlEncoded(TOKEN_ENDPOINT, params)
            val jsonObject = JsonParser.parseString(responseJson).asJsonObject

            if (jsonObject.has("error")) {
                val errorMsg = jsonObject.get("error_description")?.asString
                    ?: jsonObject.get("error")?.asString
                    ?: "Unknown token exchange error"
                return@withContext Result.failure(Exception(errorMsg))
            }

            val accessToken = jsonObject.get("access_token").asString
            val tokenType = jsonObject.get("token_type")?.asString ?: "Bearer"
            val expiresInSeconds = jsonObject.get("expires_in").asLong
            val refreshToken = jsonObject.get("refresh_token")?.asString
            val scope = jsonObject.get("scope")?.asString ?: ""

            val expiresAtEpochSeconds = (System.currentTimeMillis() / 1000) + expiresInSeconds
            val token = SpotifyToken(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtEpochSeconds = expiresAtEpochSeconds,
                tokenType = tokenType,
                scope = scope
            )
            Result.success(token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshToken(
        refreshToken: String,
        clientId: String
    ): Result<SpotifyToken> = withContext(Dispatchers.IO) {
        try {
            val params = mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "client_id" to clientId
            )
            val responseJson = postFormUrlEncoded(TOKEN_ENDPOINT, params)
            val jsonObject = JsonParser.parseString(responseJson).asJsonObject

            if (jsonObject.has("error")) {
                val errorMsg = jsonObject.get("error_description")?.asString
                    ?: jsonObject.get("error")?.asString
                    ?: "Unknown token refresh error"
                return@withContext Result.failure(Exception(errorMsg))
            }

            val accessToken = jsonObject.get("access_token").asString
            val tokenType = jsonObject.get("token_type")?.asString ?: "Bearer"
            val expiresInSeconds = jsonObject.get("expires_in").asLong
            val newRefreshToken = jsonObject.get("refresh_token")?.asString ?: refreshToken
            val scope = jsonObject.get("scope")?.asString ?: ""

            val expiresAtEpochSeconds = (System.currentTimeMillis() / 1000) + expiresInSeconds
            val token = SpotifyToken(
                accessToken = accessToken,
                refreshToken = newRefreshToken,
                expiresAtEpochSeconds = expiresAtEpochSeconds,
                tokenType = tokenType,
                scope = scope
            )
            Result.success(token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun postFormUrlEncoded(urlString: String, params: Map<String, String>): String {
        val body = params.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }

        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(body)
            writer.flush()
        }

        val statusCode = conn.responseCode
        val inputStream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
        val responseBody = inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

        if (statusCode !in 200..299 && !responseBody.contains("error")) {
            throw Exception("HTTP $statusCode: $responseBody")
        }

        return responseBody
    }
}
