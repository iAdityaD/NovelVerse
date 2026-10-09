package com.novelverse.core.domain

import org.junit.Assert.*
import org.junit.Test

class SourceDefinitionValidatorTest {
    private val valid = SourceDefinition(1, "public-fiction", "Public Fiction", "https://fiction.example",
        setOf(SourceCapability.CHAPTER), mapOf("chapter.content" to "article"))
    @Test fun `minimal bounded chapter definition validates structurally`() {
        assertTrue(SourceDefinitionValidator.errors(valid).isEmpty())
    }
    @Test fun `credentials and unexpected URL components are rejected`() {
        for (url in listOf("http://fiction.example", "https://user:secret@fiction.example", "file:///chapter", "https://fiction.example:8443", "https://fiction.example?q=a", "https://fiction.example/chapters")) {
            assertTrue(url, SourceDefinitionValidator.errors(valid.copy(baseUrl = url)).isNotEmpty())
        }
    }
    @Test fun `advertised capability must have its selectors`() {
        assertTrue(SourceDefinitionValidator.errors(valid.copy(capabilities = setOf(SourceCapability.SEARCH))).any { it.contains("search.item") })
    }
    @Test fun `unsupported schema and unbounded selectors fail`() {
        assertTrue(SourceDefinitionValidator.errors(valid.copy(schemaVersion = 2)).isNotEmpty())
        assertTrue(SourceDefinitionValidator.errors(valid.copy(selectors = mapOf("chapter.content" to "a".repeat(501)))).isNotEmpty())
    }
}
