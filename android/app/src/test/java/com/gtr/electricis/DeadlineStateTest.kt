package com.gtr.electricis

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeadlineStateTest {
    @Test fun threeHourBoundaryAndPeruOffset() {
        val item=ReminderItem(-1,"Proyecto","2026-11-12","2026-11-12T18:00:00-05:00","project")
        val edge=java.time.Instant.parse("2026-11-12T20:00:00Z")
        assertEquals(180L,minutesLeft(item.deadline_at!!,edge))
        assertEquals("por_vencer",reminderState(item,edge))
        assertNull(reminderState(item,edge.minusSeconds(60)))
        assertEquals("retrasada",reminderState(item,edge.plusSeconds(10800)))
        assertEquals("por_vencer",reminderState(item,edge.plusSeconds(10740)))
    }

    @Test fun classifiesDatesAcrossMonthBoundary() {
        val today=LocalDate.of(2026,11,30)
        assertEquals("retrasada",deadlineState("2026-11-29",today))
        assertEquals("por_vencer",deadlineState("2026-11-30",today))
        assertEquals("por_vencer",deadlineState("2026-12-01",today))
        assertNull(deadlineState("2026-12-02",today))
        assertNull(deadlineState("2026-02-30",today))
    }
}
