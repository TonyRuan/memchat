package com.memorychat.app.data.local.datastore

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiKeyDefaultsTest {
    @Test
    fun resolveReturnsStoredApiKeyWhenPresent() {
        val resolved = ApiKeyDefaults.resolve(
            storedApiKey = "stored-key",
            defaultApiKey = "default-key"
        )

        assertEquals("stored-key", resolved)
    }

    @Test
    fun resolveFallsBackToDefaultApiKeyWhenStoredValueIsBlank() {
        val resolved = ApiKeyDefaults.resolve(
            storedApiKey = "   ",
            defaultApiKey = "default-key"
        )

        assertEquals("default-key", resolved)
    }

    @Test
    fun resolveFallsBackToTrimmedDefaultApiKeyWhenStoredValueIsNull() {
        val resolved = ApiKeyDefaults.resolve(
            storedApiKey = null,
            defaultApiKey = "  default-key  "
        )

        assertEquals("default-key", resolved)
    }

    @Test
    fun resolveTrimsStoredApiKeyBeforeReturningIt() {
        val resolved = ApiKeyDefaults.resolve(
            storedApiKey = "  stored-key  ",
            defaultApiKey = "default-key"
        )

        assertEquals("stored-key", resolved)
    }

    @Test
    fun defaultApiEndpointUsesTokenPlanMimoV25() {
        assertEquals("https://token-plan-cn.xiaomimimo.com/v1", ApiConfigDefaults.BASE_URL)
        assertEquals("mimo-v2.5", ApiConfigDefaults.MODEL_NAME)
    }
}
