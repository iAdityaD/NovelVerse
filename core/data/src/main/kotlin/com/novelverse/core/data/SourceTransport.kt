package com.novelverse.core.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class SourceTransport @Inject constructor() {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val nextRequest = ConcurrentHashMap<String, Long>()
    private val robots = ConcurrentHashMap<String, Pair<Long, String>>()
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .dns { host ->
            val addresses = Dns.SYSTEM.lookup(host)
            require(addresses.all(::publicAddress)) { "Private and local network destinations are not allowed." }
            addresses
        }.build()

    suspend fun html(source: CssSource, url: String): String = locks.getOrPut(source.baseUrl) { Mutex() }.withLock {
        val target = source.validateUrl(url)
        val rules = robots[source.baseUrl]?.takeIf { System.currentTimeMillis() - it.first < 900_000 }?.second
            ?: request(source, source.baseUrl + "/robots.txt", robotsRequest = true).also { robots[source.baseUrl] = System.currentTimeMillis() to it }
        require(RobotsPolicy.allowed(rules, java.net.URI(target).rawPath.orEmpty())) { "This path is restricted by the website's robots policy." }
        request(source, target)
    }

    private suspend fun request(source: CssSource, initial: String, robotsRequest: Boolean = false): String {
        var url = initial
        repeat(4) {
            delay((nextRequest[source.baseUrl].orEmptyTime() - System.currentTimeMillis()).coerceAtLeast(0))
            nextRequest[source.baseUrl] = System.currentTimeMillis() + 2000
            val response = execute(Request.Builder().url(source.validateUrl(url)).header("User-Agent", "NovelVerse/0.2 (personal reader)").header("Accept", "text/html,text/plain").build())
            if (response.code in 300..399) {
                url = source.validateUrl(java.net.URI(url).resolve(response.location ?: error("Redirect has no destination.")).toString())
            } else {
                if (robotsRequest && response.code == 404) return ""
                if (response.code == 429 || response.code == 503) {
                    val wait = response.retryAfter?.toLongOrNull()?.times(1000) ?: 60_000L
                    nextRequest[source.baseUrl] = System.currentTimeMillis() + wait.coerceIn(2000L, 86_400_000L)
                    error("Source is rate-limited or unavailable. Retry later.")
                }
                require(response.code in 200..299) { "Source returned HTTP ${response.code}. Access restrictions will not be bypassed." }
                require(robotsRequest || response.type.contains("html", true) || response.type.contains("text/plain", true)) { "Unsupported response type. Expected readable HTML." }
                return response.body
            }
        }
        error("Too many redirects.")
    }
    private data class Page(val code: Int, val body: String, val location: String?, val retryAfter: String?, val type: String)
    private suspend fun execute(request: Request): Page = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val page = response.use {
                        val body = it.body ?: error("Empty response.")
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        body.byteStream().use { input ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                require(output.size() + read <= 2 * 1024 * 1024) { "Response exceeds the 2 MB safety limit." }
                                output.write(buffer, 0, read)
                            }
                        }
                        Page(it.code, output.toString(body.contentType()?.charset(Charsets.UTF_8)?.name() ?: "UTF-8"), it.header("Location"), it.header("Retry-After"), it.header("Content-Type").orEmpty())
                    }
                    if (continuation.isActive) continuation.resume(page)
                } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            }
        })
    }
    private fun Long?.orEmptyTime() = this ?: 0L
    private fun publicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address
        if (bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc) return false
        if (bytes.size == 4) {
            val a = bytes[0].toInt() and 255; val b = bytes[1].toInt() and 255
            if (a == 0 || a >= 224 || (a == 100 && b in 64..127) || (a == 169 && b == 254)) return false
        }
        return true
    }
}

/** Conservative rules: all applicable wildcard/specific groups are honored. */
internal object RobotsPolicy {
    fun allowed(text: String, path: String): Boolean {
        var applicable = false
        var sawRule = false
        val rules = mutableListOf<Pair<String, Boolean>>()
        text.lineSequence().forEach { line ->
            val parts = line.substringBefore('#').split(':', limit = 2)
            if (parts.size != 2) return@forEach
            val key = parts[0].trim().lowercase(); val value = parts[1].trim()
            if (key == "user-agent") {
                if (sawRule) { applicable = false; sawRule = false }
                applicable = applicable || value == "*" || value.lowercase().contains("novelverse")
            } else if (key == "allow" || key == "disallow") {
                sawRule = true
                if (applicable && value.isNotEmpty()) rules += value to (key == "allow")
            }
        }
        val matching = rules.filter { (pattern, _) ->
            val end = pattern.endsWith('$')
            val body = pattern.removeSuffix("$").split('*').joinToString(".*") { Regex.escape(it) }
            Regex("^" + body + if (end) "$" else "").containsMatchIn(path)
        }
        val length = matching.maxOfOrNull { it.first.length } ?: return true
        return matching.filter { it.first.length == length }.any { it.second }
    }
}
