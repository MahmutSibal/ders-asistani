package com.example

import com.example.data.model.ConnectionMode
import com.example.data.model.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleRobolectricTest {

    @Test
    fun `connection mode labels and descriptions are properly configured`() {
        val mode = ConnectionMode.N8N_WEBHOOK
        assertEquals("n8n Webhook (Docker)", mode.label)

        val config = ServerConfig(mode = ConnectionMode.BUILTIN_AI)
        assertEquals(ConnectionMode.BUILTIN_AI, config.mode)
    }
}
