package com.pawix25.shrnk

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(context: Context) {

    private val dataStore = context.dataStore

    companion object {
        val IMAGE_QUALITY = intPreferencesKey("image_quality")
        val VIDEO_PRESET = stringPreferencesKey("video_preset")
        val CUSTOM_SIZE_MB = stringPreferencesKey("custom_size_mb")
        val COPY_METADATA = booleanPreferencesKey("copy_metadata")
        val THEME = stringPreferencesKey("theme")
    }

    suspend fun setTheme(theme: String) {
        dataStore.edit { it[THEME] = theme }
    }

    suspend fun setImageQuality(quality: Int) {
        dataStore.edit { it[IMAGE_QUALITY] = quality }
    }

    suspend fun setVideoPreset(preset: String) {
        dataStore.edit { it[VIDEO_PRESET] = preset }
    }

    suspend fun setCustomSizeMb(size: String) {
        dataStore.edit { it[CUSTOM_SIZE_MB] = size }
    }

    suspend fun setCopyMetadata(enabled: Boolean) {
        dataStore.edit { it[COPY_METADATA] = enabled }
    }

    val imageQuality: Flow<Int> = dataStore.data.map {
        it[IMAGE_QUALITY] ?: 80
    }

    val videoPreset: Flow<String> = dataStore.data.map {
        it[VIDEO_PRESET] ?: VideoPreset.MEDIUM.name
    }

    val customSizeMb: Flow<String> = dataStore.data.map {
        it[CUSTOM_SIZE_MB] ?: ""
    }

    val copyMetadata: Flow<Boolean> = dataStore.data.map {
        it[COPY_METADATA] ?: true
    }

    val theme: Flow<String> = dataStore.data.map {
        it[THEME] ?: "System"
    }
}

