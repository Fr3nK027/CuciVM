package app.vmbox.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class IdentityTest {
    @Test fun luhnKnownValue() {
        // 49015420323751 -> check digit 8 (classic IMEI example)
        assertEquals(8, Identity.luhnDigit("49015420323751"))
    }

    @Test fun imeiIsTestRangeAndValid() {
        repeat(100) {
            val imei = Identity.imei(Random(it))
            assertEquals(15, imei.length)
            assertTrue(imei.startsWith("001"))
            assertEquals(imei.last() - '0', Identity.luhnDigit(imei.dropLast(1)))
        }
    }

    @Test fun macIsLocalUnicast() {
        repeat(100) {
            val first = Identity.mac(Random(it)).substring(0, 2).toInt(16)
            assertEquals(0x02, first and 0x03)
        }
    }
}
