package com.nexus.gamebooster

import android.app.Application
import com.nexus.gamebooster.core.ServiceLocator

/**
 * Application entry point. Wires the manual dependency graph (ServiceLocator)
 * before any UI can touch it.
 */
class NexusGameBoosterApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
