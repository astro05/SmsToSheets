package com.sms2sheets.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SheetColumnTest {

    @Test
    fun `serializes and deserializes custom column subset preserving order`() {
        val chosen = listOf(
            SheetColumn.TIMESTAMP,
            SheetColumn.SENDER,
            SheetColumn.EXTRACTED_AMOUNT
        )
        val raw = SheetColumn.serialize(chosen)
        assertEquals("TIMESTAMP,SENDER,EXTRACTED_AMOUNT", raw)
        assertEquals(chosen, SheetColumn.deserialize(raw))
    }

    @Test
    fun `falls back to default columns when raw is blank or invalid`() {
        assertEquals(SheetColumn.defaultList, SheetColumn.deserialize(""))
        assertEquals(SheetColumn.defaultList, SheetColumn.deserialize("UNKNOWN_COL"))
    }
}
