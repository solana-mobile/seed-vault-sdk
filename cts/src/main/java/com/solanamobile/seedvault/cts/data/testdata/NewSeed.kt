/*
 * Copyright (c) 2025 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.testdata

import android.os.Build

object NewSeed {
    val SEED_NAME = when (Build.MODEL) {
        "Seeker" -> "Seeker Seed 3"
        else -> "NewSeed"
    }
    val SEED_PIN = when (Build.MODEL) {
        "Seeker" -> "<use existing PIN>"
        else -> "000000"
    }
}