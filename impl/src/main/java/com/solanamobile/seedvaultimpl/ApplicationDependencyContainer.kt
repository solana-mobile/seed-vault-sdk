/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl

import android.content.Context
import com.solanamobile.seedvaultimpl.data.SeedRepository
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import kotlinx.coroutines.CoroutineScope
import java.nio.charset.StandardCharsets

/**
 * Dependency injection container scoped to the Application. Contents will be lazily initialized
 * where possible.
 */
class ApplicationDependencyContainer(
    private val applicationContext: Context,
    private val applicationScope: CoroutineScope) {

    val seedRepository: SeedRepository by lazy {
        SeedRepository(applicationContext, applicationScope)
    }

    // The companion object contains dependencies which should be globally available, but logically
    // are still application dependencies
    companion object {
        val sodium: LazySodiumAndroid by lazy {
            val sodiumAndroid = SodiumAndroid()
            LazySodiumAndroid(sodiumAndroid, StandardCharsets.UTF_8)
        }
    }
}