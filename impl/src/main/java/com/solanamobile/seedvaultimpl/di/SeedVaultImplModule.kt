package com.solanamobile.seedvaultimpl.di

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.solanamobile.seedvaultimpl.data.SeedRepository
import com.solanamobile.seedvaultimpl.usecase.BipDerivationUseCase
import com.solanamobile.seedvaultimpl.usecase.BipDerivationUseCaseImpl
import com.solanamobile.seedvaultimpl.usecase.Ed25519Slip10UseCase
import com.solanamobile.seedvaultimpl.usecase.Ed25519Slip10UseCaseImpl
import com.solanamobile.seedvaultimpl.usecase.PrepopulateKnownAccountsUseCase
import com.solanamobile.seedvaultimpl.usecase.PrepopulateKnownAccountsUseCaseImpl
import com.solanamobile.seedvaultimpl.usecase.SignTransactionUseCase
import com.solanamobile.seedvaultimpl.usecase.SignTransactionUseCaseImpl
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module
import java.nio.charset.StandardCharsets

internal val SeedVaultImplModule = module {

    /* Presentation */
    // TODO: provide ViewModels dependencies here

    /* Domain */
    factory<BipDerivationUseCase> {
        BipDerivationUseCaseImpl(
            ed25519Slip10UseCase = get()
        )
    }

    factory<Ed25519Slip10UseCase> {
        Ed25519Slip10UseCaseImpl(
            lazySodiumAndroid = get()
        )
    }

    factory<PrepopulateKnownAccountsUseCase> {
        PrepopulateKnownAccountsUseCaseImpl(
            seedRepository = get(),
            ed25519Slip10UseCase = get()
        )
    }

    factory<SignTransactionUseCase> {
        SignTransactionUseCaseImpl(
            lazySodiumAndroid = get()
        )
    }

    /* Data */
    single<LazySodiumAndroid> {
        LazySodiumAndroid(SodiumAndroid(), StandardCharsets.UTF_8)
    }

    single<SeedRepository> {
        SeedRepository(
            context = get(),
            defaultDispatcher = Dispatchers.Default
        )
    }
}