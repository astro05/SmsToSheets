package com.sms2sheets.app.domain

import com.sms2sheets.app.data.db.MatchMode
import com.sms2sheets.app.data.db.RoutingRuleEntity
import java.security.MessageDigest

data class ExtractedFields(
    val amount: String = "",
    val code: String = "",
    val customExtract: String = ""
)

object MessageExtractor {

    private val currencyAmountRegex = Regex(
        pattern = """(?i)(?:(?:BDT|TK\.?|TAKA|USD|EUR|GBP|INR|RS\.?|SGD|AED|SAR)\s*[:\-]?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)|([$€£₹৳])\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)|([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:BDT|TK\.?|TAKA|USD|EUR|GBP|INR|RS\.?))"""
    )

    private val labeledAmountRegex = Regex(
        pattern = """(?i)(?:amount|amt|paid|received|debited|credited|total|balance)\s*(?:of|is|:|-)?\s*(?:BDT|TK\.?|USD|INR|RS\.?|[$€£₹৳])?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    )

    private val otpRegex = Regex(
        pattern = """(?i)(?:otp|code|pin|verification|passcode|token)\s*(?:is|:|-)?\s*([0-9]{4,8})"""
    )

    private val standaloneOtpRegex = Regex(
        pattern = """(?i)\b([0-9]{4,8})\b(?=\s+(?:is your|for|to verify))"""
    )

    private val trxIdRegex = Regex(
        pattern = """(?i)(?:trx\s*id|txn\s*id|transaction\s*id|ref(?:\s*id|\s*no)?)\s*[:\-#]?\s*([A-Z0-9]{5,24})"""
    )

    fun normalizeDigits(input: String): String =
        input.filter { it.isDigit() }

    fun buildSmsKey(sender: String, timestampMillis: Long, body: String): String {
        val raw = "${sender.trim().lowercase()}|$timestampMillis|${body.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    fun matchesRule(sender: String, body: String, rule: RoutingRuleEntity): Boolean {
        if (!rule.isEnabled) return false

        val senderMatches = matchesSenderFilter(sender, rule.senderFilter, rule.matchMode)
        if (!senderMatches) return false

        val keyword = rule.keywordFilter.trim()
        if (keyword.isEmpty()) return true

        val terms = keyword.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return terms.any { term -> body.contains(term, ignoreCase = true) }
    }

    fun matchesSenderFilter(
        sender: String,
        senderFilter: String,
        matchMode: MatchMode
    ): Boolean {
        val filterTokens = senderFilter
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (filterTokens.isEmpty()) return false
        if (filterTokens.any { it == "*" }) return true

        val trimmedSender = sender.trim()
        val senderDigits = normalizeDigits(trimmedSender)

        return filterTokens.any { token ->
            when (matchMode) {
                MatchMode.EXACT -> {
                    if (trimmedSender.equals(token, ignoreCase = true)) {
                        true
                    } else {
                        val tokenDigits = normalizeDigits(token)
                        tokenDigits.length >= 6 && senderDigits.length >= 6 &&
                            (senderDigits == tokenDigits ||
                                senderDigits.takeLast(10) == tokenDigits.takeLast(10))
                    }
                }

                MatchMode.CONTAINS -> {
                    if (trimmedSender.contains(token, ignoreCase = true)) {
                        true
                    } else {
                        val tokenDigits = normalizeDigits(token)
                        tokenDigits.length >= 4 && senderDigits.contains(tokenDigits)
                    }
                }

                MatchMode.REGEX -> {
                    runCatching {
                        Regex(token, RegexOption.IGNORE_CASE).containsMatchIn(trimmedSender)
                    }.getOrDefault(false)
                }
            }
        }
    }

    fun extractFields(
        body: String,
        extractAmount: Boolean,
        extractOtp: Boolean,
        customRegex: String = ""
    ): ExtractedFields {
        val amount = if (extractAmount) extractAmount(body) else ""
        val code = if (extractOtp) extractCodeOrOtp(body) else ""
        val custom = if (customRegex.isNotBlank()) extractCustom(body, customRegex) else ""
        return ExtractedFields(
            amount = amount,
            code = code,
            customExtract = custom
        )
    }

    fun extractAmount(body: String): String {
        val currencyMatch = currencyAmountRegex.find(body)
        if (currencyMatch != null) {
            return currencyMatch.value.trim()
        }
        val labeledMatch = labeledAmountRegex.find(body)
        if (labeledMatch != null) {
            return labeledMatch.groupValues.getOrNull(1)?.trim().orEmpty()
        }
        return ""
    }

    fun extractCodeOrOtp(body: String): String {
        val otpMatch = otpRegex.find(body) ?: standaloneOtpRegex.find(body)
        if (otpMatch != null) {
            return otpMatch.groupValues.getOrNull(1)?.trim().orEmpty()
        }
        val trxMatch = trxIdRegex.find(body)
        if (trxMatch != null) {
            return trxMatch.groupValues.getOrNull(1)?.trim().orEmpty()
        }
        return ""
    }

    fun extractCustom(body: String, pattern: String): String {
        return runCatching {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(body) ?: return@runCatching ""
            if (match.groupValues.size > 1 && match.groupValues[1].isNotBlank()) {
                match.groupValues[1].trim()
            } else {
                match.value.trim()
            }
        }.getOrDefault("")
    }
}
