package app.melosome.claimconnect.ai

import org.junit.Assert.*
import org.junit.Test

class QuotaTextTest {
    @Test fun coversEveryQuotaState() {
        assertEquals("Demo answers in this build — nothing leaves your device",
            quotaText(null, null, false, true))
        assertEquals("Unlimited checks unlocked", quotaText(0, 10, true, false))
        assertEquals("Free checks included each month", quotaText(null, null, false, false))
        assertEquals("Free checks used up", quotaText(0, 10, false, false))
        assertEquals("2 of 10 free checks left — almost out", quotaText(2, 10, false, false))
        assertEquals("7 of 10 free checks left", quotaText(7, 10, false, false))
        assertEquals("Live analysis · billed to your Meta key", quotaText(null, null, false, false, true))
    }
}
