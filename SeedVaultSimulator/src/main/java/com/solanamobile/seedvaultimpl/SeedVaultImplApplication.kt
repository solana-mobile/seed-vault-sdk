/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl

import android.app.Application
import com.solanamobile.seedvaultimpl.data.SeedRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class SeedVaultImplApplication : Application() {
    @Inject
    lateinit var seedRepository: SeedRepository

    // TODO: remove dependencyContainer in favor of hilt/dagger injection
    val dependencyContainer: ApplicationDependencyContainer by lazy {
        ApplicationDependencyContainer(seedRepository)
    }
}