package com.pocketdesk.app.input

class HostSelection {
    @Volatile var automaticAddress: String? = null
    @Volatile var requestedAddress: String? = null
        private set
    @Volatile var lastConnectedAddress: String? = null
        private set
    @Volatile var disconnected = false
        private set
    @Volatile var generation = 0L
        private set

    @Synchronized fun request(address: String): Long {
        generation++
        disconnected = false
        requestedAddress = address
        automaticAddress = address
        return generation
    }

    @Synchronized fun disconnect() {
        generation++
        disconnected = true
        requestedAddress = null
        automaticAddress = null
    }

    @Synchronized fun target(pluggedAddress: String? = null): String? =
        if (disconnected) null else requestedAddress ?: automaticAddress ?: lastConnectedAddress ?: pluggedAddress

    @Synchronized fun accepts(address: String): Boolean =
        !disconnected && (target() == null || target() == address)

    @Synchronized fun isCurrent(generation: Long, address: String): Boolean =
        this.generation == generation && accepts(address)

    @Synchronized fun connected(address: String) {
        if (accepts(address)) lastConnectedAddress = address
    }
}
