package com.moagauge.pleos.network

import org.junit.Assert.*
import org.junit.Test

class DefaultNetworkStateTest {
    @Test fun internetWithoutValidationRemainsOffline() {
        val state = DefaultNetworkState<String>()
        state.available("wifi")
        assertFalse(state.capabilities("wifi", internet = true, validated = false))
        assertTrue(state.capabilities("wifi", internet = true, validated = true))
        assertFalse(state.capabilities("wifi", internet = false, validated = true))
    }

    @Test fun staleLossAndCapabilitiesCannotOverrideReplacementNetwork() {
        val state = DefaultNetworkState<String>()
        state.available("old")
        state.capabilities("old", true, true)
        state.available("new")
        state.capabilities("new", true, true)
        assertTrue(state.lost("old"))
        assertTrue(state.capabilities("old", false, false))
        assertFalse(state.lost("new"))
        assertFalse(state.capabilities("new", true, true))
    }

    @Test fun synchronousSeedDoesNotOverwriteANewerCallback() {
        val state = DefaultNetworkState<String>()
        val revision = state.revision
        state.available("new")
        state.capabilities("new", true, true)
        assertTrue(state.seedIfUnchanged(revision, null, false))
    }

    @Test fun initialConnectedSnapshotAndRepeatedAvailableRemainOnline() {
        val state = DefaultNetworkState<String>()
        assertTrue(state.seedIfUnchanged(state.revision, "wifi", true))
        assertTrue(state.available("wifi"))
        assertFalse(state.lost("wifi"))
        assertFalse(state.available("cellular"))
        assertTrue(state.capabilities("cellular", true, true))
    }
}
