package com.novelverse.core.data

import org.junit.Assert.*
import org.junit.Test

class CssSourceTest {
    private val definition = """{"schemaVersion":1,"id":"fixture","name":"Fixture","baseUrl":"https://fiction.example","searchPath":"/search","queryParameter":"q","searchItem":"article","searchTitle":"h2","searchLink":"a","novelTitle":"h1","novelAuthor":"","catalogItem":"li","catalogLink":"a","chapterContent":"main","catalogNext":"","chapterNext":""}"""
    @Test fun searchUsesReturnedTitlesAndValidatedRelativeUrls() {
        val source=CssSource.parse(definition)
        val hits=source.searchResults("<article><h2>Actual title</h2><a href='/novel/1'>Open</a></article>","https://fiction.example/search")
        assertEquals("Actual title",hits.single().title)
        assertEquals("https://fiction.example/novel/1",hits.single().url)
    }
    @Test fun extractionPreservesParagraphsAndRemovesExecutableChrome() {
        val source=CssSource.parse(definition)
        assertEquals(listOf("First paragraph.","Second paragraph."),source.paragraphs("<main><script>bad()</script><nav>Next</nav><p>First paragraph.</p><p>Second paragraph.</p></main>",source.baseUrl))
    }
    @Test fun crossOriginLinksAreRejected() {
        val source=CssSource.parse(definition)
        try {source.validateUrl("https://other.example/book");fail("Expected origin restriction")}catch(_:IllegalArgumentException){}
    }
    @Test fun robotsLongestSpecificRuleOverridesGeneralRule() {
        val rules="User-agent: *\nDisallow: /private\nAllow: /private/public\n"
        assertFalse(RobotsPolicy.allowed(rules,"/private/chapter"))
        assertTrue(RobotsPolicy.allowed(rules,"/private/public/chapter"))
        assertTrue(RobotsPolicy.allowed(rules,"/novel/1"))
    }
    @Test(expected=IllegalArgumentException::class) fun oversizedRobotsPolicyIsRejectedRatherThanTruncated() {
        RobotsPolicy.allowed("# empty rule\n".repeat(2001)+"User-agent: *\nDisallow: /", "/chapter")
    }
    @Test fun absentContainerIsAnErrorNotFakeContent() {
        try{CssSource.parse(definition).paragraphs("<p>Chrome</p>","https://fiction.example");fail("Expected parser failure")}catch(_:IllegalStateException){}
    }
}
