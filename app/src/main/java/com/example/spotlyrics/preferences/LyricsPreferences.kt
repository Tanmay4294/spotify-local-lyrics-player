package com.example.spotlyrics.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.spotlyrics.lyrics.LyricsProviderChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.lyricsDataStore: DataStore<Preferences> by preferencesDataStore(name = "lyrics_preferences")

class LyricsPreferences private constructor(private val context: Context) {
    private val providerKey = stringPreferencesKey("lyrics_provider")

    val selectedProvider: Flow<LyricsProviderChoice> = context.lyricsDataStore.data
        .map { preferences ->
            val choiceString = preferences[providerKey] ?: LyricsProviderChoice.LRCLIB.name
            try {
                LyricsProviderChoice.valueOf(choiceString)
            } catch (e: Exception) {
                LyricsProviderChoice.LRCLIB
            }
        }

    suspend fun setLyricsProvider(provider: LyricsProviderChoice) {
        context.lyricsDataStore.edit { preferences ->
            preferences[providerKey] = provider.name
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: LyricsPreferences? = null

        fun getInstance(context: Context): LyricsPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LyricsPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun getProviderFlow(context: Context): Flow<LyricsProviderChoice> {
            return getInstance(context).selectedProvider
        }

        suspend fun setProvider(context: Context, provider: LyricsProviderChoice) {
            getInstance(context).setLyricsProvider(provider)
        }
    }
}
