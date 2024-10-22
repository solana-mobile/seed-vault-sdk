/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.tests

import com.solanamobile.seedvault.cts.BuildConfig
import com.solanamobile.seedvault.cts.data.SagaChecker
import com.solanamobile.seedvault.cts.data.TestCorpus
import com.solanamobile.seedvault.cts.data.TestSessionLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent

@Module
@InstallIn(ActivityRetainedComponent::class)
internal object TestCorpusProvider {
    @Provides
    fun provideTestCorpus(
        noPermissionsContentProviderCheck: NoPermissionsContentProviderCheck,
        acquireSeedVaultPermissionTestCase: AcquireSeedVaultPermissionTestCase,
        acquireSeedVaultPrivilegedPermissionTestCase: AcquireSeedVaultPrivilegedPermissionTestCase,
        cannotShowSeedSettingsTestCase: CannotShowSeedSettingsTestCase,
        createNewSeedTestCase: CreateNewSeedTestCase,
        deauthorizeSeed12TestCase: DeauthorizeSeed12TestCase,
        denySignTransactionTestCase: DenySignTransactionTestCase,
        permissionedAccountFetchPubKeysTestCase: PermissionedAccountFetchPubKeysTestCase,
        fetch10PubKeyTestCase: Fetch10PubKeyTestCase,
        fetch11PubKeyTestCase: Fetch11PubKeyTestCase,
        fetch1PubKeyTestCase: Fetch1PubKeyTestCase,
        hasAuthorizedSeedsContentProviderTestCase: HasAuthorizedSeedsContentProviderTestCase,
        hasUnauthorizedSeedsContentProviderTestCase: HasUnauthorizedSeedsContentProviderTestCase,
        implementationLimitsContentProviderTestCase: ImplementationLimitsContentProviderTestCase,
        importSeed12TestCase: ImportSeed12TestCase,
        importSeed24TestCase: ImportSeed24TestCase,
        incorrectPinSignTransactionFailureTestCase: IncorrectPinSignTransactionFailureTestCase,
        initialConditionsTestCase: InitialConditionsTestCase,
        noAuthorizedSeedsContentProviderTestCase: NoAuthorizedSeedsContentProviderTestCase,
        noUnauthorizedSeedsContentProviderTestCase: NoUnauthorizedSeedsContentProviderTestCase,
        authorizeSeed12SagaTestCase: AuthorizeSeed12SagaTestCase,
        reauthorizeSeed12TestCase: ReauthorizeSeed12TestCase,
        seed12AccountsContentProviderTestCase: KnownSeed12AccountsContentProviderTestCase,
        seed24AccountsContentProviderTestCase: KnownSeed24AccountsContentProviderTestCase,
        showSeedSettingsTestCase: ShowSeedSettingsTestCase,
        sign1TransactionWith1SignatureTestCase: Sign1TransactionWith1SignatureTestCase,
        signMaxTransactionWithMaxSignatureTestCase: SignMaxTransactionWithMaxSignatureTestCase,
        signatureRequestsExceedLimitTestCase: SignatureRequestsExceedLimitTestCase,
        signingRequestsExceedLimitTestCase: SigningRequestsExceedLimitTestCase,
        deauthorizeSeed24TestCase: DeauthorizeSeed24TestCase,
        authorizeSeed24SagaTestCase: AuthorizeSeed24SagaTestCase,
        logger: TestSessionLogger,
        sagaChecker: SagaChecker,
    ): TestCorpus {
        val isSaga = sagaChecker.isSaga()
        if (isSaga) {
            logger.warn("Running additional bypass for Saga only.")
        }
        @Suppress("KotlinConstantConditions")
        val isGenericBuild = BuildConfig.FLAVOR == "Generic"
        @Suppress("KotlinConstantConditions")
        return listOfNotNull(
            noPermissionsContentProviderCheck.takeIf { isGenericBuild },
            acquireSeedVaultPrivilegedPermissionTestCase.takeIf { isGenericBuild },
            acquireSeedVaultPermissionTestCase.takeIf { isGenericBuild },
            initialConditionsTestCase,
            noUnauthorizedSeedsContentProviderTestCase,
            noAuthorizedSeedsContentProviderTestCase,
            importSeed12TestCase,
            authorizeSeed12SagaTestCase.takeIf { isSaga },
            permissionedAccountFetchPubKeysTestCase,
            fetch1PubKeyTestCase,
            fetch10PubKeyTestCase,
            fetch11PubKeyTestCase,
            sign1TransactionWith1SignatureTestCase,
            signMaxTransactionWithMaxSignatureTestCase,
            signingRequestsExceedLimitTestCase,
            signatureRequestsExceedLimitTestCase,
            denySignTransactionTestCase,
            incorrectPinSignTransactionFailureTestCase,
            cannotShowSeedSettingsTestCase.takeIf { isGenericBuild && !isSaga },
            showSeedSettingsTestCase.takeIf { !isGenericBuild },
            deauthorizeSeed12TestCase.takeIf { isGenericBuild },
            hasUnauthorizedSeedsContentProviderTestCase.takeIf { isGenericBuild },
            reauthorizeSeed12TestCase.takeIf { isGenericBuild },
            importSeed24TestCase,
            authorizeSeed24SagaTestCase.takeIf { isSaga },
            hasAuthorizedSeedsContentProviderTestCase,
            seed12AccountsContentProviderTestCase,
            seed24AccountsContentProviderTestCase,
            createNewSeedTestCase,
            implementationLimitsContentProviderTestCase.takeIf { !isSaga },
            deauthorizeSeed24TestCase.takeIf { isGenericBuild },
        )
    }
}