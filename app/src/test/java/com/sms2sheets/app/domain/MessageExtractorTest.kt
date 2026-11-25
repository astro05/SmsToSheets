package com.sms2sheets.app.domain

import com.sms2sheets.app.data.db.MatchMode
import com.sms2sheets.app.data.db.RoutingRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageExtractorTest {

    private fun rule(
        senderFilter: String,
        matchMode: MatchMode = MatchMode.CONTAINS,
        keywordFilter: String = ""
    ) = RoutingRuleEntity(
        id = 1L,
        name = "Test Rule",
        senderFilter = senderFilter,
        matchMode = matchMode,
        keywordFilter = keywordFilter,
        destinationId = 1L
    )

    @Test
    fun `matches exact phone number ignoring country code or formatting`() {
        val r = rule(senderFilter = "01711-223344", matchMode = MatchMode.EXACT)
        assertTrue(MessageExtractor.matchesRule("+8801711223344", "Test", r))
        assertFalse(MessageExtractor.matchesRule("+8801811223344", "Test", r))
    }

    @Test
    fun `matches comma separated senders and keyword filter`() {
        val r = rule(
            senderFilter = "bKash, Nagad",
            matchMode = MatchMode.CONTAINS,
            keywordFilter = "received, payment"
        )
        assertTrue(
            MessageExtractor.matchesRule(
                "bKash",
                "You have received Tk 1,500.00 from 01711000000.",
                r
            )
        )
        assertFalse(
            MessageExtractor.matchesRule(
                "bKash",
                "Your OTP is 482910",
                r
            )
        )
    }

    @Test
    fun `extracts currency amount and trx id`() {
        val body = "You have received Tk 2,450.50 from 01712345678. TrxID 9A8B7C6D5E."
        val extracted = MessageExtractor.extractFields(
            body = body,
            extractAmount = true,
            extractOtp = true
        )
        assertEquals("Tk 2,450.50", extracted.amount)
        assertEquals("9A8B7C6D5E", extracted.code)
    }

    @Test
    fun `extracts otp and custom regex`() {
        val body = "Your verification code is 491820. Available Balance: 15,200.00"
        val extracted = MessageExtractor.extractFields(
            body = body,
            extractAmount = false,
            extractOtp = true,
            customRegex = """Balance:\s*([0-9.,]+)"""
        )
        assertEquals("491820", extracted.code)
        assertEquals("15,200.00", extracted.customExtract)
    }
}
