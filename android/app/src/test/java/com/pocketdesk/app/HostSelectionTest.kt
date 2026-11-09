package com.pocketdesk.app

import com.pocketdesk.app.input.HostSelection
import org.junit.Assert.*
import org.junit.Test

class HostSelectionTest {
    @Test fun rememberedHostOutranksUnrelatedPluggedDevice() {
        val selection = HostSelection()
        selection.automaticAddress = "pad"
        assertEquals("pad", selection.target("watch"))
        assertFalse(selection.accepts("watch"))
    }
    @Test fun latestTapInvalidatesDelayedSwitch() {
        val selection = HostSelection()
        val old = selection.request("computer")
        val current = selection.request("pad")
        assertFalse(selection.isCurrent(old, "computer"))
        assertTrue(selection.isCurrent(current, "pad"))
    }
    @Test fun disconnectRejectsLateConnectedCallbackAndRetry() {
        val selection = HostSelection()
        val request = selection.request("pad")
        selection.disconnect()
        assertFalse(selection.accepts("pad"))
        assertFalse(selection.isCurrent(request, "pad"))
        assertNull(selection.target("pad"))
    }
    @Test fun oldPeerCallbackCannotReplaceNewSelection() {
        val selection = HostSelection()
        selection.request("old")
        selection.connected("old")
        selection.request("new")
        selection.connected("old")
        assertEquals("new", selection.target())
        assertFalse(selection.accepts("old"))
    }
    @Test fun returningToAnEarlierAddressDoesNotReviveOldTimer() {
        val selection = HostSelection()
        val old = selection.request("a")
        selection.request("b")
        val current = selection.request("a")
        assertFalse(selection.isCurrent(old, "a"))
        assertTrue(selection.isCurrent(current, "a"))
    }
    @Test fun explicitReconnectAfterDisconnectWorks() {
        val selection = HostSelection()
        selection.request("pad")
        selection.disconnect()
        val current = selection.request("pad")
        assertTrue(selection.isCurrent(current, "pad"))
    }
}
