/*
 * Copyright (c) 2025 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.testdata

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class NewSeed @Inject constructor(implementationDetails: ImplementationDetails) {
    val SEED_NAME = implementationDetails.generateSeedName(2)
    val SEED_PIN = when (implementationDetails.IS_PIN_CONFIGURABLE_PER_SEED) {
        true -> "000000"
        false -> "<use existing PIN>"
    }
}