package com.coc.autobot

import android.app.Application

/**
 * DISCLAIMER: This project is strictly for educational and research purposes only.
 * Using automation tools violates Clash of Clans Terms of Service and may result in permanent account bans.
 * The user assumes all risks associated with using this software.
 *
 * Application class for CoCAutoBot.
 * Initializes global application state and dependencies.
 */
class CoCAutoBotApp : Application() {

    companion object {
        lateinit var instance: CoCAutoBotApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
