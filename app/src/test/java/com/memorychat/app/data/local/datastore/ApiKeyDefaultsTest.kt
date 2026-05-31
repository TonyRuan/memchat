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
}
