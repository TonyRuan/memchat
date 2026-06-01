package com.memorychat.app.data.local.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.memorychat.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object ApiKeyDefaults {
    fun resolve(storedApiKey: String?, defaultApiKey: String): String {
        val stored = storedApiKey?.trim().orEmpty()
        return stored.ifEmpty { defaultApiKey.trim() }
    }
}

class SettingsDataStore(private val context: Context) {
    companion object {
        val PROVIDER_TYPE = stringPreferencesKey("provider_type")
        val BASE_URL = stringPreferencesKey("base_url")
        val MODEL_NAME = stringPreferencesKey("model_name")
        val DEFAULT_USE_MEMORY = booleanPreferencesKey("default_use_memory")
        val DEFAULT_GENERATE_MEMORY = booleanPreferencesKey("default_generate_memory")
        val DEFAULT_PERSONA_ID = stringPreferencesKey("default_persona_id")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        private const val ENCRYPTED_PREFS_NAME = "encrypted_settings"
        private const val KEY_API_KEY = "api_key"
    }

    // EncryptedSharedPreferences for sensitive data (API Key)
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        ENCRYPTED_PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val providerType: Flow<String> = context.dataStore.data.map { it[PROVIDER_TYPE] ?: "mimo" }
    val baseUrl: Flow<String> = context.dataStore.data.map { it[BASE_URL] ?: "https://api.xiaomimimo.com/v1" }
    val modelName: Flow<String> = context.dataStore.data.map { it[MODEL_NAME] ?: "mimo-v2.5" }
    val defaultUseMemory: Flow<Boolean> = context.dataStore.data.map { it[DEFAULT_USE_MEMORY] ?: true }
    val defaultGenerateMemory: Flow<Boolean> = context.dataStore.data.map { it[DEFAULT_GENERATE_MEMORY] ?: true }
    val defaultPersonaId: Flow<String> = context.dataStore.data.map { it[DEFAULT_PERSONA_ID] ?: "" }
    val maxTokens: Flow<Int> = context.dataStore.data.map { it[MAX_TOKENS] ?: 8192 }

    private fun resolvedApiKey(): String {
        return ApiKeyDefaults.resolve(
            storedApiKey = encryptedPrefs.getString(KEY_API_KEY, ""),
            defaultApiKey = BuildConfig.DEFAULT_API_KEY
        )
    }

    // API Key uses EncryptedSharedPreferences with callbackFlow to emit current value
    val apiKey: Flow<String> = callbackFlow {
        trySend(resolvedApiKey())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == KEY_API_KEY) {
                trySend(ApiKeyDefaults.resolve(prefs.getString(KEY_API_KEY, ""), BuildConfig.DEFAULT_API_KEY))
            }
        }
        encryptedPrefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { encryptedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    suspend fun saveProviderType(value: String) { context.dataStore.edit { it[PROVIDER_TYPE] = value } }
    suspend fun saveBaseUrl(value: String) { context.dataStore.edit { it[BASE_URL] = value } }
    suspend fun saveModelName(value: String) { context.dataStore.edit { it[MODEL_NAME] = value } }
    suspend fun saveDefaultUseMemory(value: Boolean) { context.dataStore.edit { it[DEFAULT_USE_MEMORY] = value } }
    suspend fun saveDefaultGenerateMemory(value: Boolean) { context.dataStore.edit { it[DEFAULT_GENERATE_MEMORY] = value } }
    suspend fun saveDefaultPersonaId(value: String) { context.dataStore.edit { it[DEFAULT_PERSONA_ID] = value } }
    suspend fun saveMaxTokens(value: Int) { context.dataStore.edit { it[MAX_TOKENS] = value } }
    suspend fun getMemoryExtractionWatermark(conversationId: String): Long {
        val key = longPreferencesKey("memory_extraction_watermark_$conversationId")
        return context.dataStore.data.map { it[key] ?: 0L }.first()
    }
    suspend fun saveMemoryExtractionWatermark(conversationId: String, value: Long) {
        val key = longPreferencesKey("memory_extraction_watermark_$conversationId")
        context.dataStore.edit { it[key] = value }
    }

    // API Key save uses EncryptedSharedPreferences
    suspend fun saveApiKey(value: String) {
        encryptedPrefs.edit().putString(KEY_API_KEY, value).apply()
    }
}
