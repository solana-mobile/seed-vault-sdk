/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.tests

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import com.solanamobile.seedvault.Bip32DerivationPath
import com.solanamobile.seedvault.Bip44DerivationPath
import com.solanamobile.seedvault.BipLevel
import com.solanamobile.seedvault.SigningRequest
import com.solanamobile.seedvault.SigningResponse
import com.solanamobile.seedvault.Wallet
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvault.WalletContractV1.AuthToken
import com.solanamobile.seedvault.cts.data.ActivityLauncherTestCase
import com.solanamobile.seedvault.cts.data.ConditionChecker
import com.solanamobile.seedvault.cts.data.TestCaseImpl
import com.solanamobile.seedvault.cts.data.TestResult
import com.solanamobile.seedvault.cts.data.TestSessionLogger
import com.solanamobile.seedvault.cts.data.conditioncheckers.AuthorizedSeedsChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.HasSeedVaultPermissionChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.KnownSeed12AuthorizedChecker
import com.solanamobile.seedvault.cts.data.testdata.ImplementationDetails
import com.solanamobile.seedvault.cts.data.testdata.SigningDatasets
import com.solanamobile.seedvault.cts.data.testdata.maxPayloadSize
import com.solanamobile.seedvault.cts.data.tests.helper.ActionFailedException
import com.solanamobile.seedvault.cts.data.tests.helper.EmptyResponseException
import com.solanamobile.seedvault.cts.data.tests.helper.NoResultException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred

internal abstract class SignNTransactionsMSignaturesTestCase(
    preConditions: List<ConditionChecker>,
    private val authorizedSeedsChecker: AuthorizedSeedsChecker,
    private val logger: TestSessionLogger,
    private val expectedException: Exception? = null,
    private val signingRequests: () -> List<SigningRequest>,
    private val expectedSignatures: List<SigningResponse>? = null
) : TestCaseImpl(
    preConditions = preConditions
), ActivityLauncherTestCase {
    private lateinit var launcher: ActivityResultLauncher<SignTransactionsInput>
    private var completionSignal: CompletableDeferred<ArrayList<SigningResponse>>? = null

    data class SignTransactionsInput(
        @AuthToken val authToken: Long,
        val requests: List<SigningRequest>
    )

    class SignTransactionIntentContract :
        ActivityResultContract<SignTransactionsInput, Result<ArrayList<SigningResponse>>>() {

        override fun createIntent(context: Context, input: SignTransactionsInput): Intent =
            Wallet.signTransactions(context, input.authToken, ArrayList(input.requests))

        override fun parseResult(
            resultCode: Int,
            intent: Intent?
        ): Result<ArrayList<SigningResponse>> {
            return try {
                val result = onSignTransactionsResult(resultCode, intent)
                Log.d(TAG, "Transaction signed: signatures=$result")
                Result.success(result)
            } catch (e: ActionFailedException) {
                Log.e(TAG, "Transaction signing failed", e)
                Result.failure(e)
            } catch (e: EmptyResponseException) {
                Log.e(TAG, "Transaction signing failed", e)
                Result.failure(e)
            } catch (e: NoResultException) {
                Log.e(TAG, "Transaction signing failed", e)
                Result.failure(e)
            }
        }

        @Throws(ActionFailedException::class)
        fun onSignTransactionsResult(
            resultCode: Int,
            result: Intent?
        ): ArrayList<SigningResponse> {
            if (resultCode != Activity.RESULT_OK) {
                throw ActionFailedException("Sign Transaction", resultCode)
            } else if (result == null) {
                throw NoResultException()
            }

            @Suppress("DEPRECATION")
            val signingResponses = result.getParcelableArrayListExtra<SigningResponse>(
                WalletContractV1.EXTRA_SIGNING_RESPONSE
            )

            if (signingResponses == null) {
                throw EmptyResponseException()
            }

            return signingResponses
        }

        companion object {
            const val TAG = "SignTransactionIntentContract"
        }
    }

    override fun registerActivityLauncher(arc: ActivityResultCaller) {
        launcher =
            arc.registerForActivityResult(SignTransactionIntentContract()) { signingResponse ->
                completionSignal?.run {
                    completionSignal = null
                    signingResponse.fold(
                        onSuccess = {
                            if (expectedSignatures != null) {
                                if (expectedSignatures != it) {
                                    completeExceptionally(
                                        IllegalStateException("Signature mismatch\nExpected ${expectedSignatures}\nReceived $it")
                                    )
                                } else {
                                    complete(it)
                                }
                            } else {
                                complete(it)
                            }
                        },
                        onFailure = { completeExceptionally(it) }
                    )
                }
            }
    }

    override suspend fun doExecute(): TestResult {
        @AuthToken val authToken = authorizedSeedsChecker.findMatchingSeed()
        if (authToken == null) {
            logger.warn("$id: Failed locating seed for `signTransactions`")
            return TestResult.FAIL
        }

        val signal = CompletableDeferred<ArrayList<SigningResponse>>()
        assert(completionSignal == null) { "Completion signal non-null" }
        completionSignal = signal

        val requests = signingRequests()
        launcher.launch(SignTransactionsInput(authToken, requests))

        try {
            signal.await()
        } catch (e: Exception) {
            Log.e("SignTransactionTestCase", "Transaction failed", e)
            if (expectedException == null) {
                return TestResult.FAIL
            }
            if (e is ActionFailedException && expectedException is ActionFailedException) {
                return if (expectedException.errorCode == e.errorCode) TestResult.PASS else TestResult.FAIL
            }
            if (e is EmptyResponseException && expectedException is EmptyResponseException) {
                return TestResult.PASS
            }
            if (e is NoResultException && expectedException is NoResultException) {
                return TestResult.PASS
            }
            return TestResult.FAIL
        }

        return if (expectedException == null) TestResult.PASS else TestResult.FAIL
    }

    companion object {
        @JvmStatic
        protected fun getLimits(context: Context): Pair<Int, Int> {
            val implementationLimits = Wallet.getImplementationLimitsForPurpose(
                context,
                WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION
            )
            val maxSigningRequests =
                implementationLimits[WalletContractV1.IMPLEMENTATION_LIMITS_MAX_SIGNING_REQUESTS]!!.toInt()
            val maxRequestedSignatures =
                implementationLimits[WalletContractV1.IMPLEMENTATION_LIMITS_MAX_REQUESTED_SIGNATURES]!!.toInt()

            return maxSigningRequests to maxRequestedSignatures
        }

        @JvmStatic
        protected fun signMTransactionsWithNSignatures(
            context: Context,
            transactions: Int,
            signaturesPerTransaction: Int,
            useBip44DerivationPaths: Boolean,
            payloadSize: Int = SigningDatasets.PAYLOAD_SIZE_DEFAULT
        ): ArrayList<SigningRequest> {
            val implementationLimits = Wallet.getImplementationLimitsForPurpose(
                context,
                WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION
            )
            val maxRequestedSignatures =
                implementationLimits[WalletContractV1.IMPLEMENTATION_LIMITS_MAX_REQUESTED_SIGNATURES]!!.toInt()
            check(maxRequestedSignatures <= SigningDatasets.SIGNATURES_INCREMENT)

            val signingRequests = (0 until transactions).map { i ->
                val derivationPaths = (0 until signaturesPerTransaction).map { j ->
                    val accountIndex = i * SigningDatasets.SIGNATURES_INCREMENT + j
                    if (useBip44DerivationPaths) {
                        Bip44DerivationPath.newBuilder()
                            .setAccount(BipLevel(accountIndex, true)).build().toUri()
                    } else {
                        Bip32DerivationPath.newBuilder()
                            .appendLevel(BipLevel(44, true))
                            .appendLevel(BipLevel(501, true))
                            .appendLevel(BipLevel(accountIndex, true))
                            .build().toUri()
                    }
                }
                SigningRequest(createFakeTransaction(i, payloadSize), derivationPaths)
            }

            return ArrayList(signingRequests)
        }

        /**
         * One signing request per entry in [payloadSizes], each requesting
         * [signaturesPerTransaction] signatures, so that a single request in a batch can differ
         * from its siblings in payload size.
         */
        @JvmStatic
        protected fun signTransactionsWithPayloadSizes(
            payloadSizes: List<Int>,
            signaturesPerTransaction: Int
        ): ArrayList<SigningRequest> {
            val signingRequests = payloadSizes.mapIndexed { i, payloadSize ->
                val derivationPaths = (0 until signaturesPerTransaction).map { j ->
                    Bip32DerivationPath.newBuilder()
                        .appendLevel(BipLevel(44, true))
                        .appendLevel(BipLevel(501, true))
                        .appendLevel(BipLevel(i * SigningDatasets.SIGNATURES_INCREMENT + j, true))
                        .build().toUri()
                }
                SigningRequest(createFakeTransaction(i, payloadSize), derivationPaths)
            }

            return ArrayList(signingRequests)
        }

        @JvmStatic
        protected fun createFakeTransaction(
            i: Int,
            payloadSize: Int = SigningDatasets.PAYLOAD_SIZE_DEFAULT
        ): ByteArray {
            return ByteArray(payloadSize) { i.toByte() }
        }
    }
}

internal class Sign1TransactionWith1SignatureTestCase @Inject constructor(
    @ApplicationContext context: Context,
    logger: TestSessionLogger,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
) : SignNTransactionsMSignaturesTestCase(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker),
    authorizedSeedsChecker = knownSeed12AuthorizedChecker,
    logger = logger,
    signingRequests = {
        signMTransactionsWithNSignatures(context, 1, 1, false)
    },
    expectedSignatures = SigningDatasets.expectedSignatures(
        SigningDatasets.PAYLOAD_SIZE_DEFAULT, 1, 1
    )
), ActivityLauncherTestCase {
    override val id: String = "s1t1s"
    override val description: String = "Sign 1 transaction with 1 signature"
    override val instructions: String = "Approve transaction when prompted."
}

internal class Sign1TransactionV1With1SignatureTestCase @Inject constructor(
    @ApplicationContext context: Context,
    logger: TestSessionLogger,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
) : SignNTransactionsMSignaturesTestCase(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker),
    authorizedSeedsChecker = knownSeed12AuthorizedChecker,
    logger = logger,
    signingRequests = {
        signMTransactionsWithNSignatures(
            context, 1, 1, false, SigningDatasets.maxTransactionV1PayloadSize(1)
        )
    },
    expectedSignatures = SigningDatasets.expectedSignatures(
        SigningDatasets.maxTransactionV1PayloadSize(1), 1, 1
    )
), ActivityLauncherTestCase {
    override val id: String = "s1t1sv1"
    override val description: String =
        "Sign 1 payload of ${SigningDatasets.maxTransactionV1PayloadSize(1)} bytes with 1 " +
                "signature, filling a Transaction V1"
    override val instructions: String = "Approve transaction when prompted."
}

internal class SignMaxTransactionWithMaxSignatureTestCase @Inject constructor(
    @ApplicationContext private val context: Context,
    logger: TestSessionLogger,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    implementationDetails: ImplementationDetails
) : SignNTransactionsMSignaturesTestCase(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker),
    authorizedSeedsChecker = knownSeed12AuthorizedChecker,
    logger = logger,
    signingRequests = {
        val limits = getLimits(context)
        signMTransactionsWithNSignatures(context, limits.first, limits.second, false)
    },
    expectedSignatures = SigningDatasets.expectedSignatures(
        SigningDatasets.PAYLOAD_SIZE_DEFAULT,
        implementationDetails.MAX_SIGNING_REQUESTS,
        implementationDetails.MAX_REQUESTED_SIGNATURES
    )
), ActivityLauncherTestCase {
    override val id: String = "smaxtmaxs"
    override val description: String
        get() {
            val limits = getLimits(context)
            return "Sign ${limits.first} transaction with ${limits.second} signature"
        }
    override val instructions: String = "Approve transaction when prompted."
}

internal class SignMaxTransactionV1WithMaxSignatureTestCase @Inject constructor(
    @ApplicationContext private val context: Context,
    logger: TestSessionLogger,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    implementationDetails: ImplementationDetails
) : SignNTransactionsMSignaturesTestCase(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker),
    authorizedSeedsChecker = knownSeed12AuthorizedChecker,
    logger = logger,
    signingRequests = {
        val limits = getLimits(context)
        signMTransactionsWithNSignatures(
            context, limits.first, limits.second, false,
            SigningDatasets.maxTransactionV1PayloadSize(limits.second)
        )
    },
    expectedSignatures = SigningDatasets.expectedSignatures(
        SigningDatasets.maxTransactionV1PayloadSize(implementationDetails.MAX_REQUESTED_SIGNATURES),
        implementationDetails.MAX_SIGNING_REQUESTS,
        implementationDetails.MAX_REQUESTED_SIGNATURES
    )
), ActivityLauncherTestCase {
    override val id: String = "smaxtmaxsv1"
    override val description: String
        get() {
            val limits = getLimits(context)
            return "Sign ${limits.first} payloads of " +
                    "${SigningDatasets.maxTransactionV1PayloadSize(limits.second)} bytes with " +
                    "${limits.second} signatures, filling a Transaction V1"
        }
    override val instructions: String = "Approve transaction when prompted."
}

internal class SignMaxTransactionWithMaxSignatureBip44TestCase @Inject constructor(
    @ApplicationContext private val context: Context,
    logger: TestSessionLogger,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    implementationDetails: ImplementationDetails
) : SignNTransactionsMSignaturesTestCase(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker),
    authorizedSeedsChecker = knownSeed12AuthorizedChecker,
    logger = logger,
    signingRequests = {
        val limits = getLimits(context)
        signMTransactionsWithNSignatures(context, limits.first, limits.second, true)
    },
    expectedSignatures = SigningDatasets.expectedSignatures(
        SigningDatasets.PAYLOAD_SIZE_DEFAULT,
        implementationDetails.MAX_SIGNING_REQUESTS,
        implementationDetails.MAX_REQUESTED_SIGNATURES
    )
), ActivityLauncherTestCase {
    override val id: String = "smaxtmaxsb44"
    override val description: String
        get() {
            val limits = getLimits(context)
            return "Sign ${limits.first} transaction with ${limits.second} signature using BIP-44 derivation paths"
        }
    override val instructions: String = "Approve transaction when prompted."
}

internal class SignMaxTransactionV1WithMaxSignatureBip44TestCase @Inject constructor(
    @ApplicationContext private val context: Context,
    logger: TestSessionLogger,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    implementationDetails: ImplementationDetails
) : SignNTransactionsMSignaturesTestCase(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker),
    authorizedSeedsChecker = knownSeed12AuthorizedChecker,
    logger = logger,
    signingRequests = {
        val limits = getLimits(context)
        signMTransactionsWithNSignatures(
            context, limits.first, limits.second, true,
            SigningDatasets.maxTransactionV1PayloadSize(limits.second)
        )
    },
    expectedSignatures = SigningDatasets.expectedSignatures(
        SigningDatasets.maxTransactionV1PayloadSize(implementationDetails.MAX_REQUESTED_SIGNATURES),
        implementationDetails.MAX_SIGNING_REQUESTS,
        implementationDetails.MAX_REQUESTED_SIGNATURES
    )
), ActivityLauncherTestCase {
    override val id: String = "smaxtmaxsb44v1"
    override val description: String
        get() {
            val limits = getLimits(context)
            return "Sign ${limits.first} payloads of " +
                    "${SigningDatasets.maxTransactionV1PayloadSize(limits.second)} bytes with " +
                    "${limits.second} signatures, filling a Transaction V1, using BIP-44 " +
                    "derivation paths"
        }
    override val instructions: String = "Approve transaction when prompted."
}

internal class SignTransactionRequestsExceedLimitTestCase @Inject constructor(
    @ApplicationContext private val context: Context,
    logger: TestSessionLogger,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    private val implementationDetails: ImplementationDetails
) : SignNTransactionsMSignaturesTestCase(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker),
    authorizedSeedsChecker = knownSeed12AuthorizedChecker,
    logger = logger,
    signingRequests = {
        val limits = getLimits(context)
        signMTransactionsWithNSignatures(
            context, limits.first + 1, 1, false,
            implementationDetails.maxPayloadSize(1)
        )
    },
    expectedException = ActionFailedException(
        "Sign Transaction",
        WalletContractV1.RESULT_IMPLEMENTATION_LIMIT_EXCEEDED
    ),
), ActivityLauncherTestCase {
    override val id: String = "strel"
    override val description: String
        get() {
            val limits = getLimits(context)
            return "Sign ${limits.first + 1} maximum-size transactions"
        }
    override val instructions: String = ""
}

internal class SignTransactionSignaturesExceedLimitTestCase @Inject constructor(
    @ApplicationContext private val context: Context,
    logger: TestSessionLogger,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    private val implementationDetails: ImplementationDetails
) : SignNTransactionsMSignaturesTestCase(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker),
    authorizedSeedsChecker = knownSeed12AuthorizedChecker,
    logger = logger,
    signingRequests = {
        val limits = getLimits(context)
        signMTransactionsWithNSignatures(
            context, 1, limits.second + 1, false,
            implementationDetails.maxPayloadSize(limits.second + 1)
        )
    },
    expectedException = ActionFailedException(
        "Sign Transaction",
        WalletContractV1.RESULT_IMPLEMENTATION_LIMIT_EXCEEDED
    ),
), ActivityLauncherTestCase {
    override val id: String = "stsel"
    override val description: String
        get() {
            val limits = getLimits(context)
            return "Sign request with ${limits.second + 1} signatures"
        }
    override val instructions: String = ""
}

internal class SignTransactionPayloadExceedsMaxSizeTestCase @Inject constructor(
    @ApplicationContext context: Context,
    logger: TestSessionLogger,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    private val implementationDetails: ImplementationDetails
) : SignNTransactionsMSignaturesTestCase(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker),
    authorizedSeedsChecker = knownSeed12AuthorizedChecker,
    logger = logger,
    signingRequests = {
        signMTransactionsWithNSignatures(
            context, 1, 1, false,
            implementationDetails.maxPayloadSize(1) + 1
        )
    },
    expectedException = ActionFailedException(
        "Sign Transaction",
        WalletContractV1.RESULT_INVALID_PAYLOAD
    ),
), ActivityLauncherTestCase {
    override val id: String = "stpems"
    override val description: String
        get() = "Sign a payload of ${implementationDetails.maxPayloadSize(1) + 1} bytes with 1 " +
                "signature, one byte over what fits in a transaction"
    override val instructions: String = ""
}

internal class SignTransactionPayloadExceedsMaxSizeWithMaxSignaturesTestCase @Inject constructor(
    @ApplicationContext context: Context,
    logger: TestSessionLogger,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    private val implementationDetails: ImplementationDetails
) : SignNTransactionsMSignaturesTestCase(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker),
    authorizedSeedsChecker = knownSeed12AuthorizedChecker,
    logger = logger,
    signingRequests = {
        val signatures = implementationDetails.MAX_REQUESTED_SIGNATURES
        signMTransactionsWithNSignatures(
            context, 1, signatures, false,
            implementationDetails.maxPayloadSize(signatures) + 1
        )
    },
    expectedException = ActionFailedException(
        "Sign Transaction",
        WalletContractV1.RESULT_INVALID_PAYLOAD
    ),
), ActivityLauncherTestCase {
    override val id: String = "stpemsmaxs"
    override val description: String
        get() {
            val signatures = implementationDetails.MAX_REQUESTED_SIGNATURES
            return "Sign a payload of ${implementationDetails.maxPayloadSize(signatures) + 1} " +
                    "bytes with $signatures signatures, one byte over what fits in a transaction"
        }
    override val instructions: String = ""
}

internal class SignTransactionOneOfTwoPayloadsExceedsMaxSizeTestCase @Inject constructor(
    logger: TestSessionLogger,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    private val implementationDetails: ImplementationDetails
) : SignNTransactionsMSignaturesTestCase(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker),
    authorizedSeedsChecker = knownSeed12AuthorizedChecker,
    logger = logger,
    signingRequests = {
        signTransactionsWithPayloadSizes(
            listOf(
                implementationDetails.maxPayloadSize(1),
                implementationDetails.maxPayloadSize(1) + 1
            ),
            1
        )
    },
    expectedException = ActionFailedException(
        "Sign Transaction",
        WalletContractV1.RESULT_INVALID_PAYLOAD
    ),
), ActivityLauncherTestCase {
    override val id: String = "stpemsb"
    override val description: String
        get() = "Sign 2 payloads with 1 signature each, the first of " +
                "${implementationDetails.maxPayloadSize(1)} bytes and the second one byte over"
    override val instructions: String = ""
}

internal class DenySignTransactionTestCase @Inject constructor(
    @ApplicationContext context: Context,
    logger: TestSessionLogger,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
) : SignNTransactionsMSignaturesTestCase(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker),
    authorizedSeedsChecker = knownSeed12AuthorizedChecker,
    logger = logger,
    signingRequests = {
        signMTransactionsWithNSignatures(context, 1, 1, false)
    },
    expectedException = ActionFailedException("Sign Transaction", Activity.RESULT_CANCELED),
), ActivityLauncherTestCase {
    override val id: String = "dst"
    override val description: String = "Signature denial flow"
    override val instructions: String = "Deny transaction when prompted."
}

internal class IncorrectPinSignTransactionFailureTestCase @Inject constructor(
    @ApplicationContext context: Context,
    logger: TestSessionLogger,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
) : SignNTransactionsMSignaturesTestCase(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker),
    authorizedSeedsChecker = knownSeed12AuthorizedChecker,
    logger = logger,
    signingRequests = {
        signMTransactionsWithNSignatures(context, 1, 1, false)
    },
    expectedException = ActionFailedException(
        "Sign Transaction",
        WalletContractV1.RESULT_AUTHENTICATION_FAILED
    ),
), ActivityLauncherTestCase {
    override val id: String = "ipstf"
    override val description: String = "Incorrect pin on transaction approval flow"
    override val instructions: String = "Enter incorrect pin 5 times."
}
