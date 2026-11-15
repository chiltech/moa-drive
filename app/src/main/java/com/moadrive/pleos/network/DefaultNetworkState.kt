package com.moadrive.pleos.network

/** Tracks only the current default network; late events cannot retire its replacement. */
internal class DefaultNetworkState<T> {
    var revision: Long = 0
        private set
    private var current: T? = null
    private var online = false

    fun available(network: T): Boolean {
        revision++
        if (current != network) {
            current = network
            online = false
        }
        return online
    }

    fun capabilities(network: T, internet: Boolean, validated: Boolean): Boolean {
        revision++
        if (current == network) online = internet && validated
        return online
    }

    fun lost(network: T): Boolean {
        revision++
        if (current == network) {
            current = null
            online = false
        }
        return online
    }

    fun seedIfUnchanged(expectedRevision: Long, network: T?, online: Boolean): Boolean {
        if (revision == expectedRevision) {
            current = network
            this.online = network != null && online
            revision++
        }
        return this.online
    }
}
