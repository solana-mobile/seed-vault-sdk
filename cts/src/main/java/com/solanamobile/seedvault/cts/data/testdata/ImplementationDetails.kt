/*
 * Copyright (c) 2025 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.testdata

import android.os.Build
import androidx.annotation.IntRange
import com.solanamobile.seedvault.cts.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

internal sealed interface ImplementationDetails {
    fun generateSeedName(@IntRange(from = 0) i: Int): String
    val IS_PIN_CONFIGURABLE_PER_SEED: Boolean
    val MAX_SIGNING_REQUESTS: Int
    val MAX_REQUESTED_SIGNATURES: Int
    val MAX_REQUESTED_PUBLIC_KEYS: Int
    val IS_LEGACY_IMPLEMENTATION: Boolean

    data object Generic : ImplementationDetails {
        override fun generateSeedName(i: Int) = "Test ${i + 1}"
        override val IS_PIN_CONFIGURABLE_PER_SEED: Boolean = true
        override val MAX_SIGNING_REQUESTS: Int = 3
        override val MAX_REQUESTED_SIGNATURES: Int = 3
        override val MAX_REQUESTED_PUBLIC_KEYS: Int = 10
        override val IS_LEGACY_IMPLEMENTATION: Boolean = false
    }

    data object Saga : ImplementationDetails {
        override fun generateSeedName(i: Int): String = if (i == 0) {
            "My Saga Seed"
        } else {
            "My Saga Seed ${i + 1}"
        }
        override val IS_PIN_CONFIGURABLE_PER_SEED: Boolean = true
        override val MAX_SIGNING_REQUESTS: Int = 3
        override val MAX_REQUESTED_SIGNATURES: Int = 3
        override val MAX_REQUESTED_PUBLIC_KEYS: Int = 10
        override val IS_LEGACY_IMPLEMENTATION: Boolean = true
    }

    sealed class SeekerCommon : ImplementationDetails {
        override fun generateSeedName(i: Int): String = "Seeker Seed ${i + 1}"
        override val IS_PIN_CONFIGURABLE_PER_SEED: Boolean = false
        override val IS_LEGACY_IMPLEMENTATION: Boolean = false
    }

    data object SeekerStandard : SeekerCommon() {
        override val MAX_SIGNING_REQUESTS: Int = 3
        override val MAX_REQUESTED_SIGNATURES: Int = 3
        override val MAX_REQUESTED_PUBLIC_KEYS: Int = 10
    }

    data object SeekerPrivileged : SeekerCommon() {
        override val MAX_SIGNING_REQUESTS: Int = 10
        override val MAX_REQUESTED_SIGNATURES: Int = 3
        override val MAX_REQUESTED_PUBLIC_KEYS: Int = 30
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object ImplementationDetailsModule {
    @Provides
    fun provideImplementationDetails() : ImplementationDetails {
        return when (Build.MODEL) {
            "Saga" -> ImplementationDetails.Saga
            "Seeker" -> when (BuildConfig.FLAVOR) {
                "Privileged" -> ImplementationDetails.SeekerPrivileged
                else -> ImplementationDetails.SeekerStandard
            }
            else -> ImplementationDetails.Generic
        }
    }
}