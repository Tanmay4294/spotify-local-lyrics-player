package com.example.spotlyrics.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "appearance_preferences")

class AppearancePreferences private constructor(private val context: Context) {
    private val modeKey = stringPreferencesKey("appearance_mode")

    val appearanceMode: Flow<AppearanceMode> = context.dataStore.data
        .map { preferences ->
            val modeString = preferences[modeKey] ?: AppearanceMode.AlbumColor.name
            try {
                AppearanceMode.valueOf(modeString)
            } catch (e: Exception) {
                if (modeString == "ALBUM_COLOR") AppearanceMode.AlbumColor
                else if (modeString == "DYNAMIC_ALBUM_ART") AppearanceMode.DynamicAlbumArt
                else if (modeString == "DYNAMIC_BACKGROUND") AppearanceMode.DynamicBackground
                else AppearanceMode.AlbumColor
            }
        }

    suspend fun setAppearanceMode(mode: AppearanceMode) {
        context.dataStore.edit { preferences ->
            preferences[modeKey] = mode.name
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppearancePreferences? = null

        fun getInstance(context: Context): AppearancePreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppearancePreferences(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun getModeFlow(context: Context): Flow<AppearanceMode> {
            return getInstance(context).appearanceMode
        }

        suspend fun setMode(context: Context, mode: AppearanceMode) {
            getInstance(context).setAppearanceMode(mode)
        }
    }
}
