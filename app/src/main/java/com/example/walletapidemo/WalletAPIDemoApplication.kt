/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.example.walletapidemo

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class WalletAPIDemoApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var dependencyContainer: ApplicationDependencyContainer

    override fun onCreate() {
        super.onCreate()
        dependencyContainer = ApplicationDependencyContainer(this, applicationScope)
    }
}