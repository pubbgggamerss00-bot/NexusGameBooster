package com.nexus.gamebooster.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ThreadLocalRandom

 data class DnsResolver(val name: String, val address: String, val detail: String)
 data class DnsTestResult(val resolver: DnsResolver, val latencyMs: Long?, val error: String? = null) {
    val successful: Boolean get() = latencyMs != null && error == null
}

object DnsResolverTester {
    val defaults = listOf(
        DnsResolver("Cloudflare", "1.1.1.1", "Privacy-focused public DNS"),
        DnsResolver("Google Public DNS", "8.8.8.8", "Google public resolver"),
        DnsResolver("Quad9", "9.9.9.9", "Security-focused public DNS"),
        DnsResolver("AdGuard DNS", "94.140.14.14", "DNS with optional filtering"),
    )

    suspend fun testAll(): List<DnsTestResult> = withContext(Dispatchers.IO) {
        coroutineScope {
            defaults.map { resolver -> async(Dispatchers.IO) { testOne(resolver) } }
                .awaitAll()
                .sortedWith(compareBy<DnsTestResult> { if (it.successful) 0 else 1 }.thenBy { it.latencyMs ?: Long.MAX_VALUE })
        }
    }

    private fun testOne(resolver: DnsResolver): DnsTestResult {
        val id = ThreadLocalRandom.current().nextInt(0, 65536)
        val query = makeQuery(id, "example.com")
        return try {
            val start = System.nanoTime()
            DatagramSocket().use { socket ->
                socket.soTimeout = 1800
                socket.connect(InetAddress.getByName(resolver.address), 53)
                socket.send(DatagramPacket(query, query.size))
                val buffer = ByteArray(4096)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                val elapsed = (System.nanoTime() - start) / 1_000_000L
                require(response.length >= 12) { "Invalid DNS response" }
                val bytes = response.data
                val responseId = ((bytes[0].toInt() and 0xff) shl 8) or (bytes[1].toInt() and 0xff)
                require(responseId == id) { "DNS response ID mismatch" }
                require((bytes[2].toInt() and 0x80) != 0) { "Not a DNS response" }
                val rcode = bytes[3].toInt() and 0x0f
                require(rcode == 0) { "DNS server returned code $rcode" }
                DnsTestResult(resolver, elapsed)
            }
        } catch (e: Exception) {
            DnsTestResult(resolver, null, e.message?.take(90) ?: "Timeout or network error")
        }
    }

    internal fun makeQuery(id: Int, domain: String): ByteArray {
        val nameBytes = domain.split('.').flatMap { label -> listOf(label.length.toByte()) + label.toByteArray(Charsets.US_ASCII).toList() }.toByteArray()
        val query = ByteArray(12 + nameBytes.size + 5)
        query[0] = (id ushr 8).toByte(); query[1] = id.toByte()
        query[2] = 0x01 // recursion desired
        query[5] = 0x01 // QDCOUNT = 1
        System.arraycopy(nameBytes, 0, query, 12, nameBytes.size)
        var offset = 12 + nameBytes.size
        query[offset++] = 0
        query[offset++] = 0; query[offset++] = 1 // A
        query[offset++] = 0; query[offset] = 1 // IN
        return query
    }
}
