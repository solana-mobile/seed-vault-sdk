/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl

import com.solanamobile.seedvaultimpl.data.SeedRepository
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import java.nio.charset.StandardCharsets

/**
 * Dependency injection container scoped to the Application. Contents will be lazily initialized
 * where possible.
 */
class ApplicationDependencyContainer(val seedRepository: SeedRepository) {

    // The companion object contains dependencies which should be globally available, but logically
    // are still application dependencies
    companion object {
        val sodium: LazySodiumAndroid by lazy {
            val sodiumAndroid = SodiumAndroid()
            LazySodiumAndroid(sodiumAndroid, StandardCharsets.UTF_8)
        }
    }
}