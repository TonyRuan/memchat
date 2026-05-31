package com.memorychat.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {
    companion object {
        val PROVIDER_TYPE = stringPreferencesKey("provider_type")
        val BASE_URL = stringPreferencesKey("base_url")
        val MODEL_NAME = stringPreferencesKey("model_name")
        val API_KEY = stringPreferencesKey("api_key")
        val DEFAULT_USE_MEMORY = booleanPreferencesKey("default_use_memory")
        val DEFAULT_GENERATE_MEMORY = booleanPreferencesKey("default_generate_memory")
        val DEFAULT_PERSONA_ID = stringPreferencesKey("default_persona_id")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
    }

    val providerType: Flow<String> = context.dataStore.data.map { it[PROVIDER_TYPE] ?: "mimo" }
    val baseUrl: Flow<String> = context.dataStore.data.map { it[BASE_URL] ?: "https://api.xiaomimimo.com/v1" }
    val modelName: Flow<String> = context.dataStore.data.map { it[MODEL_NAME] ?: "mimo-v2.5" }
    val apiKey: Flow<String> = context.dataStore.data.map { it[API_KEY] ?: "" }
    val defaultUseMemory: Flow<Boolean> = context.dataStore.data.map { it[DEFAULT_USE_MEMORY] ?: true }
    val defaultGenerateMemory: Flow<Boolean> = context.dataStore.data.map { it[DEFAULT_GENERATE_MEMORY] ?: true }
    val defaultPersonaId: Flow<String> = context.dataStore.data.map { it[DEFAULT_PERSONA_ID] ?: "" }
    val maxTokens: Flow<Int> = context.dataStore.data.map { it[MAX_TOKENS] ?: 8192 }

    suspend fun saveProviderType(value: String) { context.dataStore.edit { it[PROVIDER_TYPE] = value } }
    suspend fun saveBaseUrl(value: String) { context.dataStore.edit { it[BASE_URL] = value } }
    suspend fun saveModelName(value: String) { context.dataStore.edit { it[MODEL_NAME] = value } }
    suspend fun saveApiKey(value: String) { context.dataStore.edit { it[API_KEY] = value } }
    suspend fun saveDefaultUseMemory(value: Boolean) { context.dataStore.edit { it[DEFAULT_USE_MEMORY] = value } }
    suspend fun saveDefaultGenerateMemory(value: Boolean) { context.dataStore.edit { it[DEFAULT_GENERATE_MEMORY] = value } }
    suspend fun saveDefaultPersonaId(value: String) { context.dataStore.edit { it[DEFAULT_PERSONA_ID] = value } }
    suspend fun saveMaxTokens(value: Int) { context.dataStore.edit { it[MAX_TOKENS] = value } }
}




