/*
 * Copyright (c) 2025 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.testdata

import android.content.ComponentName
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
    val DOES_PIN_FAILURE_WIPE_SEED_VAULT: Boolean
    val MAX_SIGNING_REQUESTS: Int
    val MAX_REQUESTED_SIGNATURES: Int
    val MAX_REQUESTED_PUBLIC_KEYS: Int
    val IS_LEGACY_IMPLEMENTATION: Boolean
    val ACTION_AUTHORIZE_SEED_ACCESS_COMPONENT_NAME: ComponentName
    val ACTION_CREATE_SEED_COMPONENT_NAME: ComponentName
    val ACTION_IMPORT_SEED_COMPONENT_NAME: ComponentName
    val ACTION_SEED_SETTINGS_COMPONENT_NAME: ComponentName
}

private sealed class GenericCommon : ImplementationDetails {
    override fun generateSeedName(i: Int) = "Test ${i + 1}"
    override val IS_PIN_CONFIGURABLE_PER_SEED: Boolean = true
    override val DOES_PIN_FAILURE_WIPE_SEED_VAULT: Boolean = false
    override val MAX_SIGNING_REQUESTS: Int = 3
    override val MAX_REQUESTED_SIGNATURES: Int = 3
    override val MAX_REQUESTED_PUBLIC_KEYS: Int = 10
    override val IS_LEGACY_IMPLEMENTATION: Boolean = false
}

private data object GenericStandard : GenericCommon() {
    override val ACTION_AUTHORIZE_SEED_ACCESS_COMPONENT_NAME: ComponentName = ComponentName(
        "com.solanamobile.seedvaultimpl",
        "com.solanamobile.seedvaultimpl.authActivityGenericAlias"
    )
    override val ACTION_CREATE_SEED_COMPONENT_NAME: ComponentName = ComponentName(
        "com.solanamobile.seedvaultimpl",
        "com.solanamobile.seedvaultimpl.seedDetailsGenericAlias"
    )
    override val ACTION_IMPORT_SEED_COMPONENT_NAME: ComponentName = ComponentName(
        "com.solanamobile.seedvaultimpl",
        "com.solanamobile.seedvaultimpl.seedDetailsGenericAlias"
    )
    override val ACTION_SEED_SETTINGS_COMPONENT_NAME: ComponentName
        get() = throw NotImplementedError("Non-privileged Seed Vault implementations do not support ACTION_SEED_SETTINGS")
}

private data object GenericPrivileged : GenericCommon() {
    override val ACTION_AUTHORIZE_SEED_ACCESS_COMPONENT_NAME: ComponentName = ComponentName(
        "com.solanamobile.seedvaultimpl",
        "com.solanamobile.seedvaultimpl.authActivityPrivilegedAlias"
    )
    override val ACTION_CREATE_SEED_COMPONENT_NAME: ComponentName = ComponentName(
        "com.solanamobile.seedvaultimpl",
        "com.solanamobile.seedvaultimpl.seedDetailsPrivilegedAlias"
    )
    override val ACTION_IMPORT_SEED_COMPONENT_NAME: ComponentName = ComponentName(
        "com.solanamobile.seedvaultimpl",
        "com.solanamobile.seedvaultimpl.seedDetailsPrivilegedAlias"
    )
    override val ACTION_SEED_SETTINGS_COMPONENT_NAME: ComponentName = ComponentName(
        "com.solanamobile.seedvaultimpl",
        "com.solanamobile.seedvaultimpl.seedDetailsPrivilegedAlias"
    )
}

private data object Saga : ImplementationDetails {
    override fun generateSeedName(i: Int): String = if (i == 0) {
        "My Saga Seed"
    } else {
        "My Saga Seed ${i + 1}"
    }
    override val IS_PIN_CONFIGURABLE_PER_SEED: Boolean = true
    override val DOES_PIN_FAILURE_WIPE_SEED_VAULT: Boolean = true
    override val MAX_SIGNING_REQUESTS: Int = 3
    override val MAX_REQUESTED_SIGNATURES: Int = 3
    override val MAX_REQUESTED_PUBLIC_KEYS: Int = 10
    override val IS_LEGACY_IMPLEMENTATION: Boolean = true
    override val ACTION_AUTHORIZE_SEED_ACCESS_COMPONENT_NAME: ComponentName = ComponentName(
        "com.solanamobile.seedvaultimpl",
        "com.solanamobile.seedvaultimpl.ui.AuthorizeActivity"
    )
    override val ACTION_CREATE_SEED_COMPONENT_NAME: ComponentName = ComponentName(
        "com.osomprivacy.seedvaultmgmt",
        "com.osomprivacy.seedvaultmgmt.ui.SeedCreateAndImportActivity"
    )
    override val ACTION_IMPORT_SEED_COMPONENT_NAME: ComponentName = ComponentName(
        "com.osomprivacy.seedvaultmgmt",
        "com.osomprivacy.seedvaultmgmt.ui.SeedCreateAndImportActivity"
    )
    override val ACTION_SEED_SETTINGS_COMPONENT_NAME: ComponentName
        get() = throw NotImplementedError("Non-privileged Seed Vault implementations do not support ACTION_SEED_SETTINGS")
}

private sealed class SeekerCommon : ImplementationDetails {
    override fun generateSeedName(i: Int): String = "Seeker Seed ${i + 1}"
    override val IS_PIN_CONFIGURABLE_PER_SEED: Boolean = false
    override val DOES_PIN_FAILURE_WIPE_SEED_VAULT: Boolean = true
    override val IS_LEGACY_IMPLEMENTATION: Boolean = false
}

private data object SeekerStandard : SeekerCommon() {
    override val MAX_SIGNING_REQUESTS: Int = 3
    override val MAX_REQUESTED_SIGNATURES: Int = 3
    override val MAX_REQUESTED_PUBLIC_KEYS: Int = 10
    override val ACTION_AUTHORIZE_SEED_ACCESS_COMPONENT_NAME: ComponentName = ComponentName(
        "com.solanamobile.seedvaultimpl",
        "com.solanamobile.seedvaultimpl.authorizeActivityGenericAlias"
    )
    override val ACTION_CREATE_SEED_COMPONENT_NAME: ComponentName = ComponentName(
        "com.solanamobile.seedvaultimpl",
        "com.solanamobile.seedvaultimpl.seedDetailGenericAlias"
    )
    override val ACTION_IMPORT_SEED_COMPONENT_NAME: ComponentName = ComponentName(
        "com.solanamobile.seedvaultimpl",
        "com.solanamobile.seedvaultimpl.seedDetailGenericAlias"
    )
    override val ACTION_SEED_SETTINGS_COMPONENT_NAME: ComponentName
        get() = throw NotImplementedError("Non-privileged Seed Vault implementations do not support ACTION_SEED_SETTINGS")
}

private data object SeekerPrivileged : SeekerCommon() {
    override val MAX_SIGNING_REQUESTS: Int = 10
    override val MAX_REQUESTED_SIGNATURES: Int = 3
    override val MAX_REQUESTED_PUBLIC_KEYS: Int = 30
    override val ACTION_AUTHORIZE_SEED_ACCESS_COMPONENT_NAME: ComponentName = ComponentName(
        "com.solanamobile.seedvaultimpl",
        "com.solanamobile.seedvaultimpl.authorizeActivityPrivilegedAlias"
    )
    override val ACTION_CREATE_SEED_COMPONENT_NAME: ComponentName = ComponentName(
        "com.solanamobile.seedvaultimpl",
        "com.solanamobile.seedvaultimpl.seedDetailPrivilegedAlias"
    )
    override val ACTION_IMPORT_SEED_COMPONENT_NAME: ComponentName = ComponentName(
        "com.solanamobile.seedvaultimpl",
        "com.solanamobile.seedvaultimpl.seedDetailPrivilegedAlias"
    )
    override val ACTION_SEED_SETTINGS_COMPONENT_NAME: ComponentName = ComponentName(
        "com.solanamobile.seedvaultimpl",
        "com.solanamobile.seedvaultimpl.seedDetailPrivilegedAlias"
    )
}

@Module
@InstallIn(SingletonComponent::class)
internal object ImplementationDetailsModule {
    @Provides
    fun provideImplementationDetails() : ImplementationDetails {
        return when (Build.MODEL) {
            "Saga" -> Saga
            "Seeker" -> when (BuildConfig.FLAVOR) {
                "Privileged" -> SeekerPrivileged
                else -> SeekerStandard
            }
            else -> when (BuildConfig.FLAVOR) {
                "Privileged" -> GenericPrivileged
                else -> GenericStandard
            }
        }
    }
}