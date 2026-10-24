package com.nexus.gamebooster.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsResolverTesterTest {
    @Test
    fun queryContainsValidHeaderAndQuestionForExampleCom() {
        val id = 0x4A2B
        val query = DnsResolverTester.makeQuery(id, "example.com")

        assertEquals(id shr 8, query[0].toInt() and 0xff)
        assertEquals(id and 0xff, query[1].toInt() and 0xff)
        assertEquals(0x01, query[2].toInt() and 0xff) // recursion desired, high flags byte
        assertEquals(0x00, query[3].toInt() and 0xff)
        assertEquals(1, query[5].toInt() and 0xff) // one question
        assertEquals(0, query[4].toInt() and 0xff) // no answer records in query

        val expectedQuestion = byteArrayOf(
            7, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0,
            0, 1, // QTYPE A
            0, 1, // QCLASS IN
        )
        assertEquals(expectedQuestion.size, query.size - 12)
        assertTrue(query.copyOfRange(12, query.size).contentEquals(expectedQuestion))
    }
}
