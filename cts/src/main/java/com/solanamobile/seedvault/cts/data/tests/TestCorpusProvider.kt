/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.tests

import com.solanamobile.seedvault.cts.PrivilegedSeedVaultChecker
import com.solanamobile.seedvault.cts.data.TestCorpus
import com.solanamobile.seedvault.cts.data.TestSessionLogger
import com.solanamobile.seedvault.cts.data.testdata.ImplementationDetails
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent

@Module
@InstallIn(ActivityRetainedComponent::class)
internal object TestCorpusProvider {
    @Provides
    fun provideTestCorpus(
        acquireSeedVaultPermissionTestCase: AcquireSeedVaultPermissionTestCase,
        acquireSeedVaultPrivilegedPermissionTestCase: AcquireSeedVaultPrivilegedPermissionTestCase,
        authorizeSeed12SagaTestCase: AuthorizeSeed12SagaTestCase,
        authorizeSeed24SagaTestCase: AuthorizeSeed24SagaTestCase,
        cannotShowSeedSettingsTestCase: CannotShowSeedSettingsTestCase,
        createNewSeedTestCase: CreateNewSeedTestCase,
        deauthorizeSeed12TestCase: DeauthorizeSeed12TestCase,
        deauthorizeSeed24TestCase: DeauthorizeSeed24TestCase,
        denySignMessageTestCase: DenySignMessageTestCase,
        denySignTransactionTestCase: DenySignTransactionTestCase,
        fetchMaxPubKeyTestCase: FetchMaxPubKeyTestCase,
        fetchTooManyPubKeyTestCase: FetchTooManyPubKeyTestCase,
        fetch1PubKeyTestCase: Fetch1PubKeyTestCase,
        hasAuthorizedSeedsContentProviderTestCase: HasAuthorizedSeedsContentProviderTestCase,
        hasUnauthorizedSeedsContentProviderTestCase: HasUnauthorizedSeedsContentProviderTestCase,
        implementationLimitsContentProviderTestCase: ImplementationLimitsContentProviderTestCase,
        importSeed12TestCase: ImportSeed12TestCase,
        importSeed24TestCase: ImportSeed24TestCase,
        incorrectPinSignMessageFailureTestCase: IncorrectPinSignMessageFailureTestCase,
        incorrectPinSignTransactionFailureTestCase: IncorrectPinSignTransactionFailureTestCase,
        initialConditionsTestCase: InitialConditionsTestCase,
        noAuthorizedSeedsContentProviderTestCase: NoAuthorizedSeedsContentProviderTestCase,
        noPermissionsContentProviderCheck: NoPermissionsContentProviderCheck,
        noUnauthorizedSeedsContentProviderTestCase: NoUnauthorizedSeedsContentProviderTestCase,
        permissionedAccountFetchPubKeysTestCase: PermissionedAccountFetchPubKeysTestCase,
        reauthorizeSeed12TestCase: ReauthorizeSeed12TestCase,
        renameExistingSeedTestCase: RenameExistingSeedTestCase,
        seed12AccountsContentProviderTestCase: KnownSeed12AccountsContentProviderTestCase,
        seed24AccountsContentProviderTestCase: KnownSeed24AccountsContentProviderTestCase,
        showSeedSettingsTestCase: ShowSeedSettingsTestCase,
        sign1MessageWith1SignatureTestCase: Sign1MessageWith1SignatureTestCase,
        sign1TransactionWith1SignatureTestCase: Sign1TransactionWith1SignatureTestCase,
        signMaxMessageWithMaxSignatureBip44TestCase: SignMaxMessageWithMaxSignatureBip44TestCase,
        signMaxMessageWithMaxSignatureTestCase: SignMaxMessageWithMaxSignatureTestCase,
        signMaxTransactionWithMaxSignatureBip44TestCase: SignMaxTransactionWithMaxSignatureBip44TestCase,
        signMaxTransactionWithMaxSignatureTestCase: SignMaxTransactionWithMaxSignatureTestCase,
        signMessageRequestsExceedLimitTestCase: SignMessageRequestsExceedLimitTestCase,
        signMessageSignaturesExceedLimitTestCase: SignMessageSignaturesExceedLimitTestCase,
        signTransactionRequestsExceedLimitTestCase: SignTransactionRequestsExceedLimitTestCase,
        signTransactionSignaturesExceedLimitTestCase: SignTransactionSignaturesExceedLimitTestCase,
        logger: TestSessionLogger,
        implementationDetails: ImplementationDetails,
        privilegedSeedVaultChecker: PrivilegedSeedVaultChecker,
    ): TestCorpus {
        if (implementationDetails.IS_LEGACY_IMPLEMENTATION) {
            logger.warn("Running additional bypass for legacy implementation only.")
        }
        val isGenericBuild = !privilegedSeedVaultChecker.isPrivileged()
        return listOfNotNull(
            noPermissionsContentProviderCheck.takeIf { isGenericBuild },
            acquireSeedVaultPrivilegedPermissionTestCase.takeIf { isGenericBuild },
            acquireSeedVaultPermissionTestCase.takeIf { isGenericBuild },
            initialConditionsTestCase,
            noUnauthorizedSeedsContentProviderTestCase,
            noAuthorizedSeedsContentProviderTestCase,
            importSeed12TestCase,
            authorizeSeed12SagaTestCase.takeIf { implementationDetails.IS_LEGACY_IMPLEMENTATION },
            permissionedAccountFetchPubKeysTestCase,
            fetch1PubKeyTestCase,
            fetchMaxPubKeyTestCase,
            fetchTooManyPubKeyTestCase,
            sign1TransactionWith1SignatureTestCase,
            signMaxTransactionWithMaxSignatureTestCase,
            signMaxTransactionWithMaxSignatureBip44TestCase,
            signTransactionRequestsExceedLimitTestCase,
            signTransactionSignaturesExceedLimitTestCase,
            denySignTransactionTestCase,
            incorrectPinSignTransactionFailureTestCase.takeIf { !implementationDetails.DOES_PIN_FAILURE_WIPE_SEED_VAULT },
            sign1MessageWith1SignatureTestCase,
            signMaxMessageWithMaxSignatureTestCase,
            signMaxMessageWithMaxSignatureBip44TestCase,
            signMessageRequestsExceedLimitTestCase,
            signMessageSignaturesExceedLimitTestCase,
            denySignMessageTestCase,
            incorrectPinSignMessageFailureTestCase.takeIf { !implementationDetails.DOES_PIN_FAILURE_WIPE_SEED_VAULT },
            cannotShowSeedSettingsTestCase.takeIf { isGenericBuild && !implementationDetails.IS_LEGACY_IMPLEMENTATION },
            showSeedSettingsTestCase.takeIf { !isGenericBuild },
            deauthorizeSeed12TestCase.takeIf { isGenericBuild },
            hasUnauthorizedSeedsContentProviderTestCase.takeIf { isGenericBuild },
            reauthorizeSeed12TestCase.takeIf { isGenericBuild },
            importSeed24TestCase,
            authorizeSeed24SagaTestCase.takeIf { implementationDetails.IS_LEGACY_IMPLEMENTATION },
            hasAuthorizedSeedsContentProviderTestCase,
            seed12AccountsContentProviderTestCase,
            seed24AccountsContentProviderTestCase,
            createNewSeedTestCase,
            implementationLimitsContentProviderTestCase.takeIf { !implementationDetails.IS_LEGACY_IMPLEMENTATION },
            deauthorizeSeed24TestCase.takeIf { isGenericBuild },
            renameExistingSeedTestCase,
        )
    }
}