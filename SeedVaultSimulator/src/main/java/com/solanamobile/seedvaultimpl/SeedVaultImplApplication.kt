/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class SeedVaultImplApplication : Application() {
    val dependencyContainer: ApplicationDependencyContainer by lazy {
        ApplicationDependencyContainer(this)
    }
}