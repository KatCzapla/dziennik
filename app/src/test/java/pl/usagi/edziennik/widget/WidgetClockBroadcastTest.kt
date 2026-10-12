package pl.usagi.edziennik.widget

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetClockBroadcastTest {
    @Test fun dateChangeTriggersLocalRedraw() {
        assertTrue(isWidgetClockBroadcast(Intent.ACTION_DATE_CHANGED))
    }

    @Test fun timezoneChangeTriggersLocalRedraw() {
        assertTrue(isWidgetClockBroadcast(Intent.ACTION_TIMEZONE_CHANGED))
    }

    @Test fun timeAdjustmentTriggersLocalRedraw() {
        assertTrue(isWidgetClockBroadcast(Intent.ACTION_TIME_CHANGED))
    }

    @Test fun rebootTriggersLocalRedraw() {
        assertTrue(isWidgetClockBroadcast(Intent.ACTION_BOOT_COMPLETED))
    }

    @Test fun unrelatedBroadcastIsNotHandledAsClockUpdate() {
        assertFalse(isWidgetClockBroadcast(null))
        assertFalse(isWidgetClockBroadcast("unrelated"))
    }
}
