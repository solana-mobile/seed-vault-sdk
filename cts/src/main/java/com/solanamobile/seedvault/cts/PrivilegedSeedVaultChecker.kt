/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivilegedSeedVaultChecker @Inject constructor() {
    fun isPrivileged(): Boolean {
        @Suppress("KotlinConstantConditions")
        return BuildConfig.FLAVOR == "Privileged"
    }
}