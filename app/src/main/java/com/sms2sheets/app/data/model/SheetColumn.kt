package com.sms2sheets.app.data.model

enum class SheetColumn(
    val key: String,
    val headerTitle: String,
    val defaultSelected: Boolean
) {
    MESSAGE_ID("id", "Message ID", defaultSelected = true),
    TIMESTAMP("timestamp", "Date / Time", defaultSelected = true),
    SENDER("sender", "Sender", defaultSelected = true),
    BODY("body", "Message Body", defaultSelected = true),
    EXTRACTED_AMOUNT("extractedAmount", "Extracted Amount", defaultSelected = true),
    EXTRACTED_CODE("extractedCode", "Extracted Code / OTP", defaultSelected = true),
    CUSTOM_EXTRACT("customExtract", "Custom Extract", defaultSelected = false),
    RULE_NAME("ruleName", "Rule Name", defaultSelected = true),
    SYNC_MODE("source", "Sync Mode", defaultSelected = true),
    UPLOADED_AT("uploadedAt", "Uploaded At", defaultSelected = true);

    companion object {
        val defaultList: List<SheetColumn> = entries.filter { it.defaultSelected }

        val defaultSerialized: String = serialize(defaultList)

        fun serialize(columns: List<SheetColumn>): String {
            val clean = columns.distinct()
            return (if (clean.isEmpty()) defaultList else clean).joinToString(",") { it.name }
        }

        fun deserialize(raw: String?): List<SheetColumn> {
            if (raw.isNullOrBlank()) return defaultList
            val parsed = raw.split(",")
                .mapNotNull { token ->
                    val trimmed = token.trim()
                    entries.firstOrNull { it.name == trimmed }
                }
                .distinct()
            return parsed.ifEmpty { defaultList }
        }
    }
}
