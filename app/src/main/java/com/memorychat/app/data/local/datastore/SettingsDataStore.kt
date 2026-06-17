package com.memorychat.app.data.local.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.memorychat.app.BuildConfig
import com.memorychat.app.domain.model.ConversationDebugSnapshot
import com.memorychat.app.domain.model.ConversationDebugSnapshotJson
import com.memorychat.app.domain.model.ModelRuntimeDefaults
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

object ApiConfigDefaults {
    const val BASE_URL = "https://token-plan-cn.xiaomimimo.com/v1"
    const val MODEL_NAME = "mimo-v2.5"
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
        val CONTEXT_WINDOW_TOKENS = intPreferencesKey("context_window_tokens")
        val SAFETY_MARGIN_TOKENS = intPreferencesKey("safety_margin_tokens")
        val COMPRESSION_MESSAGE_TURN_THRESHOLD = intPreferencesKey("compression_message_turn_threshold")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val TOP_P = floatPreferencesKey("top_p")
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
    val baseUrl: Flow<String> = context.dataStore.data.map { it[BASE_URL] ?: ApiConfigDefaults.BASE_URL }
    val modelName: Flow<String> = context.dataStore.data.map { it[MODEL_NAME] ?: ApiConfigDefaults.MODEL_NAME }
    val defaultUseMemory: Flow<Boolean> = context.dataStore.data.map { it[DEFAULT_USE_MEMORY] ?: true }
    val defaultGenerateMemory: Flow<Boolean> = context.dataStore.data.map { it[DEFAULT_GENERATE_MEMORY] ?: true }
    val defaultPersonaId: Flow<String> = context.dataStore.data.map { it[DEFAULT_PERSONA_ID] ?: "" }
    val maxTokens: Flow<Int> = context.dataStore.data.map { it[MAX_TOKENS] ?: ModelRuntimeDefaults.MIMO_V25_MAX_COMPLETION_TOKENS }
    val contextWindowTokens: Flow<Int> = context.dataStore.data.map { it[CONTEXT_WINDOW_TOKENS] ?: ModelRuntimeDefaults.MIMO_V25_CONTEXT_WINDOW_TOKENS }
    val safetyMarginTokens: Flow<Int> = context.dataStore.data.map { it[SAFETY_MARGIN_TOKENS] ?: ModelRuntimeDefaults.DEFAULT_SAFETY_MARGIN_TOKENS }
    val compressionMessageTurnThreshold: Flow<Int> = context.dataStore.data.map { it[COMPRESSION_MESSAGE_TURN_THRESHOLD] ?: ModelRuntimeDefaults.DEFAULT_COMPRESSION_MESSAGE_TURN_THRESHOLD }
    val temperature: Flow<Float> = context.dataStore.data.map { it[TEMPERATURE] ?: ModelRuntimeDefaults.MIMO_V25_TEMPERATURE.toFloat() }
    val topP: Flow<Float> = context.dataStore.data.map { it[TOP_P] ?: ModelRuntimeDefaults.MIMO_V25_TOP_P.toFloat() }

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
    suspend fun saveContextWindowTokens(value: Int) { context.dataStore.edit { it[CONTEXT_WINDOW_TOKENS] = value } }
    suspend fun saveSafetyMarginTokens(value: Int) { context.dataStore.edit { it[SAFETY_MARGIN_TOKENS] = value } }
    suspend fun saveCompressionMessageTurnThreshold(value: Int) { context.dataStore.edit { it[COMPRESSION_MESSAGE_TURN_THRESHOLD] = value } }
    suspend fun saveTemperature(value: Float) { context.dataStore.edit { it[TEMPERATURE] = value } }
    suspend fun saveTopP(value: Float) { context.dataStore.edit { it[TOP_P] = value } }
    suspend fun getMemoryExtractionWatermark(conversationId: String): Long {
        val key = longPreferencesKey("memory_extraction_watermark_$conversationId")
        return context.dataStore.data.map { it[key] ?: 0L }.first()
    }
    suspend fun saveMemoryExtractionWatermark(conversationId: String, value: Long) {
        val key = longPreferencesKey("memory_extraction_watermark_$conversationId")
        context.dataStore.edit { it[key] = value }
    }
    suspend fun getConversationRollingSummary(conversationId: String): String {
        val key = stringPreferencesKey("conversation_rolling_summary_$conversationId")
        return context.dataStore.data.map { it[key] ?: "" }.first()
    }
    suspend fun saveConversationRollingSummary(conversationId: String, value: String) {
        val key = stringPreferencesKey("conversation_rolling_summary_$conversationId")
        context.dataStore.edit { it[key] = value }
    }
    suspend fun getConversationRollingSummaryWatermark(conversationId: String): Long {
        val key = longPreferencesKey("conversation_rolling_summary_watermark_$conversationId")
        return context.dataStore.data.map { it[key] ?: 0L }.first()
    }
    suspend fun saveConversationRollingSummaryWatermark(conversationId: String, value: Long) {
        val key = longPreferencesKey("conversation_rolling_summary_watermark_$conversationId")
        context.dataStore.edit { it[key] = value }
    }
    suspend fun getConversationDebugSnapshot(conversationId: String): ConversationDebugSnapshot? {
        val key = stringPreferencesKey("conversation_debug_snapshot_$conversationId")
        val json = context.dataStore.data.map { it[key] ?: "" }.first()
        return ConversationDebugSnapshotJson.fromJson(json)
    }
    suspend fun saveConversationDebugSnapshot(conversationId: String, snapshot: ConversationDebugSnapshot) {
        val key = stringPreferencesKey("conversation_debug_snapshot_$conversationId")
        context.dataStore.edit { it[key] = ConversationDebugSnapshotJson.toJson(snapshot) }
    }

    // API Key save uses EncryptedSharedPreferences
    suspend fun saveApiKey(value: String) {
        encryptedPrefs.edit().putString(KEY_API_KEY, value).apply()
    }
}
