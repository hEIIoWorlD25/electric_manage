package com.gtr.electricis

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeadlineStateTest {
    @Test fun classifiesDatesAcrossMonthBoundary() {
        val today=LocalDate.of(2026,11,30)
        assertEquals("retrasada",deadlineState("2026-11-29",today))
        assertEquals("por_vencer",deadlineState("2026-11-30",today))
        assertEquals("por_vencer",deadlineState("2026-12-01",today))
        assertNull(deadlineState("2026-12-02",today))
        assertNull(deadlineState("2026-02-30",today))
    }
}
