package com.pocketdesk.app

import com.pocketdesk.app.input.AimPointer
import org.junit.Assert.*
import org.junit.Test

class AimPointerTest {
    @Test fun firstSampleDoesNotJump() {
        val aim = AimPointer()
        assertEquals(0 to 0, aim.update(0f, 0f, 9.8f, 0f, 0f, 0f, 1_000_000_000))
    }

    @Test fun turningLeftAndUpMovesPointer() {
        val aim = AimPointer(1.8f)
        aim.update(0f, 0f, 9.8f, 0f, 0f, 0f, 1_000_000_000)
        val moved = aim.update(0f, -0.5f, 9.78f, 0f, 0f, 1f, 1_100_000_000)
        assertTrue("actual=$moved", moved.first < 0 && moved.second > 0)
    }

    @Test fun centerRecalibratesAfterPhoneChangesPosition() {
        val aim = AimPointer(1.8f)
        aim.update(0f, 0f, 9.8f, 0f, 0f, 0f, 1_000_000_000)
        aim.update(0f, 1f, 9.7f, 0f, 0f, 0f, 1_100_000_000)
        aim.center()
        assertEquals(0 to 0, aim.update(0f, -5f, 8.4f, 0f, 0f, 1f, 1_200_000_000))
    }

    @Test fun centerStopsAccumulatedYaw() {
        val aim = AimPointer(1.8f)
        aim.update(0f, 0f, 9.8f, 0f, 0f, 0f, 1_000_000_000)
        repeat(5) { aim.update(0f, 0f, 9.8f, 0f, 0f, -1f, 1_100_000_000 + it * 100_000_000L) }
        aim.center()
        assertEquals(0 to 0, aim.update(0f, 0f, 9.8f, 0f, 0f, 0f, 2_000_000_000))
    }
}
