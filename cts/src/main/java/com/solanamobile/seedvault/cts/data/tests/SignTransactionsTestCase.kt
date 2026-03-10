/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.tests

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
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
        private const val SIGNATURES_INCREMENT: Int = 5

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
            useBip44DerivationPaths: Boolean
        ): ArrayList<SigningRequest> {
            val implementationLimits = Wallet.getImplementationLimitsForPurpose(
                context,
                WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION
            )
            val maxRequestedSignatures =
                implementationLimits[WalletContractV1.IMPLEMENTATION_LIMITS_MAX_REQUESTED_SIGNATURES]!!.toInt()
            check(maxRequestedSignatures <= SIGNATURES_INCREMENT)

            val signingRequests = (0 until transactions).map { i ->
                val derivationPaths = (0 until signaturesPerTransaction).map { j ->
                    if (useBip44DerivationPaths) {
                        Bip44DerivationPath.newBuilder()
                            .setAccount(BipLevel(i * SIGNATURES_INCREMENT + j, true)).build().toUri()
                    } else {
                        Bip32DerivationPath.newBuilder()
                            .appendLevel(BipLevel(44, true))
                            .appendLevel(BipLevel(501, true))
                            .appendLevel(BipLevel(i * SIGNATURES_INCREMENT + j, true))
                            .build().toUri()
                    }
                }
                SigningRequest(createFakeTransaction(i), derivationPaths)
            }

            return ArrayList(signingRequests)
        }

        @JvmStatic
        protected fun createFakeTransaction(i: Int): ByteArray {
            return ByteArray(TRANSACTION_SIZE) { i.toByte() }
        }

        protected const val TRANSACTION_SIZE = 512
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
    expectedSignatures = arrayListOf(
        SigningResponse(
            listOf(
                byteArrayOf(
                    -42, -48, 114, 114, -26, 18, -123, -63,
                    123, 90, -55, -31, -97, -26, 72, 89,
                    52, -81, 73, -39, -94, -46, -128, 66, 119,
                    -100, 115, -26, -23, 86, 3, -17, 109,
                    -105, -10, 46, -114, -38, -72, -67, 92,
                    68, 59, -111, 80, -15, -4, 37, 32,
                    -42, -11, 50, 71, 31, -7, -41, 100,
                    -32, -124, 26, -119, 25, 121, 11
                )
            ),
            listOf(Uri.parse("bip32:/m/44'/501'/0'"))
        )
    )
), ActivityLauncherTestCase {
    override val id: String = "s1t1s"
    override val description: String = "Sign 1 transaction with 1 signature"
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
    expectedSignatures = arrayListOf(
        SigningResponse(
            listOf(
                byteArrayOf(-42, -48, 114, 114, -26, 18, -123, -63, 123, 90, -55, -31, -97, -26, 72, 89, 52, -81, 73, -39, -94, -46, -128, 66, 119, -100, 115, -26, -23, 86, 3, -17, 109, -105, -10, 46, -114, -38, -72, -67, 92, 68, 59, -111, 80, -15, -4, 37, 32, -42, -11, 50, 71, 31, -7, -41, 100, -32, -124, 26, -119, 25, 121, 11),
                byteArrayOf(-78, -89, 127, -100, 89, -66, -105, -97, 86, 84, 108, 48, -35, -109, -30, 57, -108, -29, -6, -98, 83, 110, -11, -50, 117, -65, -128, -67, 6, -67, -36, -47, 47, 57, 89, -30, 33, -58, -3, 66, -47, -47, 25, -110, -87, -91, 17, 121, 56, -79, -20, 85, 69, -106, 123, 110, 96, 100, -63, 40, 73, -84, -82, 10),
                byteArrayOf(121, 3, 105, 2, 116, -68, 122, -109, -26, -66, 120, 9, -93, -91, 65, 12, -107, 86, -16, -109, -32, 84, 36, -52, 127, 57, -124, 97, 124, -76, 30, 81, -18, 52, 96, 14, -67, -20, 47, 69, -37, -68, -80, -50, -84, -114, 41, -92, -71, 3, 74, -36, 120, -54, -43, -77, -39, -80, 102, 67, -82, 16, -123, 14),
                byteArrayOf(82, 24, 120, 106, 33, 48, -126, 125, 47, 67, -88, 63, -85, 62, -97, 70, -52, 70, 34, 112, -72, -76, 123, -124, -95, -55, 114, -126, -28, 31, 3, -99, -94, 53, 84, -93, -83, 37, 98, -58, 2, 37, 82, -93, -40, 117, 78, -114, -112, 73, 85, 25, 70, 107, -112, -107, -39, 110, 73, 86, -110, 88, 102, 6),
                byteArrayOf(-51, -120, 34, -80, 60, 88, -95, -5, 100, 38, 35, -6, 80, 110, 65, 44, -103, -101, 115, 65, -66, -115, -46, 43, 27, 117, 110, -42, 79, -124, 22, -20, 60, -19, 124, -124, 105, 122, -48, -55, 121, -96, 118, 27, -14, 78, 67, 48, 41, 105, 41, 30, 26, -17, -80, 9, -60, -14, -91, -8, -30, 111, 4, 4),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES),
            listOf(
                Uri.parse("bip32:/m/44'/501'/0'"),
                Uri.parse("bip32:/m/44'/501'/1'"),
                Uri.parse("bip32:/m/44'/501'/2'"),
                Uri.parse("bip32:/m/44'/501'/3'"),
                Uri.parse("bip32:/m/44'/501'/4'"),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES)
        ),
        SigningResponse(
            listOf(
                byteArrayOf(97, -25, 36, 66, 55, 113, -16, -9, 100, -101, -109, 31, -63, 29, -33, -15, 61, -120, 36, 103, -103, 85, 112, 88, -60, -48, -128, -11, -116, 46, 54, -46, 49, -59, -75, 108, -101, -105, 4, 49, -77, -97, -89, 47, -52, 56, -127, 81, 25, -24, 56, 112, -75, 81, -33, -75, 89, -44, -18, 122, -108, 108, 5, 4),
                byteArrayOf(-43, -23, -119, -16, -127, -119, -92, -119, -46, 86, -8, 70, 105, -44, -6, 0, -4, 119, 84, 8, 120, 81, 91, 124, -5, 43, 104, 107, -61, 6, 52, 10, -4, 0, -70, 123, -108, -68, 99, -125, 27, -105, -11, 93, -124, -31, 109, -73, -7, -119, 21, 111, 123, -81, 17, 51, -97, 69, 68, -97, 62, -57, 49, 6),
                byteArrayOf(98, -101, 117, -41, -37, -112, -125, 116, -124, 84, -100, -117, -92, -95, 35, -20, 26, -76, 95, 46, -24, 23, -120, 24, 39, -24, 102, 112, 21, 20, 40, 89, -60, 60, 87, -43, -122, -62, 14, 111, -41, -36, 49, 82, 84, -14, -102, -40, -46, -123, -101, 80, 40, -56, -53, 64, -27, 41, 95, -62, 127, 40, -128, 11),
                byteArrayOf(-104, -89, 71, -16, 0, -64, -44, -113, 112, 57, 30, -122, 40, 43, 85, -15, 116, -120, 23, 104, -83, 83, 75, -51, -24, 2, 119, 123, 8, 84, -18, -39, -7, 97, -25, 109, 109, 67, 103, 24, 9, 12, -12, -49, 92, -86, 116, -20, 29, -109, 22, 105, -42, 53, -86, 30, 96, -12, 68, -29, 85, -13, -48, 8),
                byteArrayOf(127, -57, -100, -79, 90, -57, 115, 63, -20, 89, -31, 3, -93, 98, 69, 70, -97, -74, 42, -85, 90, 98, -56, -65, -54, -59, 19, 100, 0, 115, 52, 122, 34, -96, 96, -66, 46, 77, -57, 42, -120, -65, 3, 7, -66, 70, 63, 52, -85, 31, -104, 20, -81, -111, -42, -70, 24, 84, 85, 35, -3, -44, 127, 6),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES),
            listOf(
                Uri.parse("bip32:/m/44'/501'/5'"),
                Uri.parse("bip32:/m/44'/501'/6'"),
                Uri.parse("bip32:/m/44'/501'/7'"),
                Uri.parse("bip32:/m/44'/501'/8'"),
                Uri.parse("bip32:/m/44'/501'/9'"),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES)
        ),
        SigningResponse(
            listOf(
                byteArrayOf(-2, -55, -48, -111, -19, -42, 83, 64, -33, 16, 71, -17, 59, -10, 111, 39, 49, 69, -107, -39, 87, 42, 2, -117, 32, -83, -52, 22, 113, -107, 31, 7, -104, 44, -89, 126, -44, -23, -103, -108, 26, -77, -19, -10, 95, -54, 52, 58, 126, 68, -39, 101, -12, -89, -29, -39, -9, 78, -128, 5, 116, 7, -96, 5),
                byteArrayOf(-114, -48, -4, 3, -11, 118, 117, 78, -86, 119, 72, 42, -61, -118, 99, 24, -6, 70, -110, 16, 41, -9, -17, 6, -43, 95, -21, 82, -40, -95, -16, -106, -23, 36, -75, 10, 97, -82, 115, 58, 68, 126, -116, 42, -30, -74, 85, 107, -63, -37, -88, 48, 98, 66, -88, 17, -14, 51, 11, -60, -78, -115, 41, 10),
                byteArrayOf(18, 2, 63, 120, 24, 38, -80, -72, -125, -110, 66, -30, -57, -115, -109, -31, 38, 23, 101, -48, -80, 43, -55, -68, -4, 26, -100, 4, 88, 103, 9, 0, -110, 59, 102, 43, 73, -83, -97, 43, -96, -39, 11, -41, -123, 43, -82, -122, 4, -67, 5, -89, 106, 125, 77, 43, -109, -80, 60, 87, -71, 38, 126, 1),
                byteArrayOf(-119, -93, 18, -11, 44, -108, -72, -25, -50, 99, -117, 124, -46, -28, -64, 46, 47, 45, -56, 99, 92, -19, -26, 100, -84, 5, -25, -124, -38, 82, -106, -46, -79, -36, -79, -4, 79, 122, -5, -63, 34, 19, -105, -68, 124, 23, -117, 52, -98, -106, 117, 96, -94, -117, -1, -49, 53, 87, 50, 84, -27, -125, 100, 10),
                byteArrayOf(118, 73, -74, -32, 33, -94, 126, -1, -47, -11, 115, 52, -49, 123, 12, 47, 113, -84, -9, -81, -9, 106, 54, 70, -95, 0, 74, -32, 33, 22, 103, -38, -56, -63, 108, 99, 122, -7, -49, 100, 103, 86, -13, 0, -45, -57, 119, 84, -19, -84, -64, 123, 111, -118, -112, -115, 81, 39, 63, 45, 14, -101, 71, 0),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES),
            listOf(
                Uri.parse("bip32:/m/44'/501'/10'"),
                Uri.parse("bip32:/m/44'/501'/11'"),
                Uri.parse("bip32:/m/44'/501'/12'"),
                Uri.parse("bip32:/m/44'/501'/13'"),
                Uri.parse("bip32:/m/44'/501'/14'"),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES)
        ),
        SigningResponse(
            listOf(
                byteArrayOf(-26, 47, -95, -6, -47, -41, 24, 103, 40, -63, 102, 62, 74, 10, 47, 23, -8, -126, 19, 50, -22, -10, 96, 101, -71, -120, -126, -94, 84, 36, -92, -42, -34, 78, -83, 74, 44, 65, -4, -41, -50, -67, 28, 80, 122, 13, -92, 72, 64, -31, 97, -110, -125, 12, -93, 10, 81, 103, 21, 48, 72, 109, -80, 5),
                byteArrayOf(76, -51, 22, -114, 33, 75, -123, 123, -115, 22, 10, -73, -77, -92, -87, -64, -30, -34, 106, 74, -19, 126, -19, 76, -88, -6, 58, 37, -57, 56, 3, -45, -47, 71, 126, -72, -75, -44, 75, -33, -104, -60, -128, 48, -36, 64, 10, -97, 81, 11, -123, -115, 63, -123, -99, -3, -115, -11, 65, 74, 2, -37, 113, 6),
                byteArrayOf(49, -92, -47, -56, 106, -126, -47, -61, -42, 94, -46, 15, -26, 72, 98, -18, 54, 9, -95, -62, 4, 85, 76, 106, -111, 58, 15, -27, 103, -46, 109, 120, -81, -93, 89, -24, 27, -125, -65, 53, -2, 102, 117, 74, 15, -43, -23, -39, -7, 123, 119, 104, 112, 103, -107, -120, -35, 48, 47, 116, -118, 10, -35, 9),
                byteArrayOf(-25, 74, -115, -84, -40, 57, -70, -16, -41, -70, -46, -50, 8, 67, -79, 26, 4, 114, 126, -85, -54, -70, -127, 76, 88, -14, 114, -95, 110, 65, 43, 48, -35, -90, -116, -21, -13, 78, 17, 0, 80, -44, -121, 31, 44, 44, -13, -36, -69, 82, -30, -30, 62, 46, -19, 40, 19, 40, -88, 9, -60, -128, -90, 13),
                byteArrayOf(-110, 61, 83, -113, 5, 105, -38, 111, 67, -114, 30, 39, 90, -110, -7, 22, 103, -1, 23, -114, 51, -84, -42, -107, -92, 105, 48, 35, 30, 81, 88, 10, -11, -5, -81, -29, -86, 36, 12, -90, 86, -81, -10, 22, 108, -102, -57, 50, -1, 111, 91, -100, -30, 33, -112, 67, -57, 118, 54, -96, 10, 25, 88, 4),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES),
            listOf(
                Uri.parse("bip32:/m/44'/501'/15'"),
                Uri.parse("bip32:/m/44'/501'/16'"),
                Uri.parse("bip32:/m/44'/501'/17'"),
                Uri.parse("bip32:/m/44'/501'/18'"),
                Uri.parse("bip32:/m/44'/501'/19'"),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES)
        ),
        SigningResponse(
            listOf(
                byteArrayOf(52, -12, 112, -27, -1, -7, 40, 94, -109, 65, 117, 3, -49, -17, -3, -83, 39, -40, 58, -17, 97, -104, 96, 56, 71, 16, 22, 75, 79, -125, 35, -120, 15, -10, 81, 93, 5, 78, -88, 18, 119, 2, -83, 73, 0, -74, -19, -103, 25, -97, 99, -6, -61, -120, 86, -114, 96, -115, -47, 78, -88, 108, -12, 14),
                byteArrayOf(-51, 31, -86, 87, 125, -3, -11, 104, -96, 26, -60, 2, -42, -62, -45, 31, 87, -33, -117, 45, 81, 69, -66, 56, -24, 127, -10, -19, -41, -127, 10, -32, 94, -30, -20, 126, -99, 99, -15, 35, 16, 116, -85, -113, 54, 3, -107, -66, 3, 77, -67, 127, 15, -21, 100, 21, 8, -103, 65, 36, 5, -96, -22, 5),
                byteArrayOf(65, -61, -41, 29, 98, 15, 112, -128, -11, -11, 113, -23, -36, -126, -44, -77, 55, -14, 110, 114, 28, 61, 94, -103, 116, 78, -108, -108, 113, -49, -13, -29, -93, -6, 38, 119, 71, -35, 65, 111, -86, -5, -125, -15, -102, -122, 61, 0, 74, -4, -32, -40, 96, 85, 30, -76, -125, -73, -2, -53, -75, -33, -73, 0),
                byteArrayOf(-104, 22, -30, 0, -68, -29, -40, -17, -113, 53, 56, 89, -72, 27, -13, 90, -34, 89, 87, -14, 9, -95, 1, 18, 90, 95, -71, -16, 21, 45, 99, -48, 49, -15, -115, 27, -96, -118, 101, 37, -58, 70, -110, 9, -16, -60, -108, 67, 11, -69, 97, -128, -52, 53, 111, -105, -34, -34, -103, -93, 19, -39, 20, 1),
                byteArrayOf(111, -99, -95, -33, -70, 82, -116, 68, -30, -52, -110, 76, -69, -21, 87, -128, -73, -33, 123, -4, -84, -1, -126, -62, 13, -53, -110, -6, -110, 27, 33, -128, -49, 98, 69, 117, -56, 48, 19, -111, 101, -76, 5, 107, 7, -30, 28, -48, -99, 111, 70, -11, 112, 74, -19, -121, 72, 126, -19, -27, 8, 9, 34, 11),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES),
            listOf(
                Uri.parse("bip32:/m/44'/501'/20'"),
                Uri.parse("bip32:/m/44'/501'/21'"),
                Uri.parse("bip32:/m/44'/501'/22'"),
                Uri.parse("bip32:/m/44'/501'/23'"),
                Uri.parse("bip32:/m/44'/501'/24'"),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES)
        ),
        SigningResponse(
            listOf(
                byteArrayOf(16, -14, -16, 6, 8, 29, 114, 14, 10, 30, 88, -34, 105, -87, -54, -117, 50, 39, 13, -103, 64, 95, -65, -78, 48, 58, 67, -11, 45, 41, 113, -27, 45, 99, -18, 99, -9, -41, 85, 123, 0, 75, 26, -76, 8, -31, 14, 28, 60, 27, 45, 82, -112, 99, 67, 116, 100, -23, -97, -4, 110, -10, 92, 14),
                byteArrayOf(-75, 37, 60, 81, -92, 110, 23, 93, 74, -127, 81, -21, -87, 21, -89, -82, 17, 90, -111, -63, 45, -65, -32, 103, -31, -8, -38, -40, 81, -98, 42, -47, -55, 76, -116, 102, -78, 69, -5, 77, -90, -34, 94, -109, 89, -41, 74, -75, 96, 52, -33, -25, 30, -106, 60, 105, 70, -93, 15, -39, 85, -28, -61, 2),
                byteArrayOf(-79, 78, 9, 4, -76, -20, -40, -8, -63, -15, -121, 90, 63, -72, -48, 94, 73, -55, 102, -76, 17, -107, 87, 9, -31, -3, 82, -21, -73, 51, 70, -72, -23, 24, 5, -36, -60, 12, 15, 48, 118, 100, 89, 66, -15, -36, -81, -10, -103, -115, -5, -55, -14, 17, 87, 121, 32, -71, -23, 118, 97, 47, 82, 3),
                byteArrayOf(4, 49, -52, 64, 110, -79, -11, -99, -79, 6, 19, -22, -107, -75, -19, -5, 52, -51, 98, -72, 27, -65, 78, 54, 30, -72, -119, -19, 53, 91, -122, -104, 4, -21, 32, -113, 0, -35, 32, -96, 4, -123, -105, 54, 103, 4, 16, -35, -11, -110, -85, -103, 120, -32, 28, -33, -25, -78, 16, 64, -1, 52, 34, 4),
                byteArrayOf(-106, 58, -37, 99, -28, -54, 81, -63, -64, -94, 37, 104, -15, -9, -105, -99, -41, -97, -45, 79, -113, 91, 93, 60, 80, -106, -92, -98, 25, -60, -125, 110, -104, -12, 98, -85, 120, -118, 108, 60, 76, -54, -70, 10, -32, 28, 45, 74, 124, 25, 115, -2, 105, -119, 17, 103, -73, 94, 97, 0, -11, 19, 6, 8),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES),
            listOf(
                Uri.parse("bip32:/m/44'/501'/25'"),
                Uri.parse("bip32:/m/44'/501'/26'"),
                Uri.parse("bip32:/m/44'/501'/27'"),
                Uri.parse("bip32:/m/44'/501'/28'"),
                Uri.parse("bip32:/m/44'/501'/29'"),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES)
        ),
        SigningResponse(
            listOf(
                byteArrayOf(123, -38, 112, 103, -27, 36, -89, 125, 17, 121, 20, -44, -71, -29, -5, 92, 55, 18, 30, 15, -69, 46, 109, 58, 45, -14, -128, -20, -53, 120, 30, 21, 21, 87, -68, -76, -9, -119, 37, 31, -127, -46, 24, 96, 96, -5, -12, 63, 92, 18, 45, 76, 12, -107, 45, -28, 62, 72, 37, 82, -112, -15, -62, 8),
                byteArrayOf(65, 64, -118, -93, 65, -126, 116, -54, 121, 104, -98, 16, 94, 75, -19, 35, -121, -89, 99, -71, -116, 15, -6, -44, -101, -25, -26, 29, 117, 17, -85, -52, -8, -74, -5, -70, -60, -116, 79, -68, -22, -87, -51, 94, 83, -34, -20, -55, 114, -118, 43, -67, -85, 106, -56, -68, -122, 73, -53, 121, -44, -101, 93, 7),
                byteArrayOf(0, -53, 64, 76, 21, -2, -120, -126, -52, -50, -76, 29, 84, -123, 127, -77, -18, -99, -83, -88, 91, -9, 95, -114, -3, 113, 16, 7, 51, -98, -89, -96, 44, -76, -62, -3, 83, -29, 22, 62, 89, 101, 85, -23, -120, 16, 121, 99, 48, 58, 127, -39, 19, 106, 47, 83, -42, 92, -60, -42, 17, -30, -127, 15),
                byteArrayOf(-66, 72, -82, -77, 61, -58, 81, 10, 74, -102, -108, 37, 114, -96, 51, -69, -100, -44, 49, -1, -108, 115, -73, -42, 48, 5, -21, -9, -101, -117, -14, -47, 56, 40, -62, -122, -53, 13, -72, -82, -82, -40, -25, 85, -96, -105, 5, -26, 71, -73, 0, 15, -67, -6, -47, 28, 99, 59, -63, 26, 29, 61, -53, 2),
                byteArrayOf(64, -48, 54, -64, -102, 62, 24, 3, -118, -80, -108, -88, 125, 20, -23, -116, 41, 21, 40, -78, -108, -2, 30, 19, 52, -105, -24, -119, -101, -72, 57, -87, 101, 47, 31, -19, 102, -113, -84, -110, -90, 118, -19, 47, 100, 68, -52, -80, 67, 4, 110, -77, -6, 25, 31, 125, -54, 43, 76, 25, 16, -87, -96, 9),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES),
            listOf(
                Uri.parse("bip32:/m/44'/501'/30'"),
                Uri.parse("bip32:/m/44'/501'/31'"),
                Uri.parse("bip32:/m/44'/501'/32'"),
                Uri.parse("bip32:/m/44'/501'/33'"),
                Uri.parse("bip32:/m/44'/501'/34'"),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES)
        ),
        SigningResponse(
            listOf(
                byteArrayOf(106, -90, 69, 30, 6, -126, -27, -111, 82, -91, 116, -45, 109, 71, -27, -10, 92, 127, 8, -92, -2, 110, -106, -26, 15, -95, -73, 82, -77, -17, 10, -19, -48, 8, 108, 126, -60, -1, 88, 47, -59, 91, 43, 108, -31, -110, 32, -114, -23, -21, 47, -9, 32, -41, 69, -53, -86, 108, -70, -93, -79, -10, -48, 8),
                byteArrayOf(71, 5, 63, 34, -39, -12, -101, 21, -44, -110, -30, 123, 43, -115, 48, -32, 80, 16, 66, -111, 45, -19, 16, 118, 82, -105, -14, -62, -39, -121, 42, -11, 47, 55, 59, 86, -120, -7, -82, 77, 117, 80, 6, -112, -30, 68, -40, 35, 4, 19, 104, -123, -9, 47, -83, -110, -97, -3, 93, -75, -99, 121, 63, 0),
                byteArrayOf(-110, -39, 49, 54, -70, -43, 103, 119, 100, -3, -33, -65, -74, -7, -45, 14, 124, -4, 68, -69, -36, 0, 69, 115, -11, -90, -94, -74, -88, -45, -94, -107, 29, -115, -81, 115, 94, -79, 66, -74, -64, 58, -41, -110, -43, 126, -17, 95, 9, -26, 83, 75, 50, 95, 86, -88, 94, -31, -20, -25, 68, 9, 115, 10),
                byteArrayOf(-116, 20, 78, -116, -55, -78, 34, 22, 17, -40, 60, -16, -11, 60, -105, 108, -1, 15, -94, 96, 66, 40, 113, 46, 23, 55, -105, 49, -29, 66, -110, 65, -48, 90, 109, -66, 47, -128, -44, 61, -93, 123, -112, 83, -93, -89, -49, 61, -119, 64, -111, -35, 3, 82, 19, 48, 101, 76, 77, 51, 104, 108, -46, 2),
                byteArrayOf(119, 50, -50, 87, 102, -39, -104, -9, 19, -56, 120, -69, -16, 34, -111, -10, 84, 111, 98, -55, -88, 85, 42, -82, -82, -80, 19, 98, -65, -119, -15, -101, 68, -28, 63, -24, 79, -99, 33, -68, -89, -106, -58, -111, 97, 121, -54, 44, 8, -123, 107, 25, 46, -75, -98, -8, 18, -102, 11, -48, 82, 44, -49, 6),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES),
            listOf(
                Uri.parse("bip32:/m/44'/501'/35'"),
                Uri.parse("bip32:/m/44'/501'/36'"),
                Uri.parse("bip32:/m/44'/501'/37'"),
                Uri.parse("bip32:/m/44'/501'/38'"),
                Uri.parse("bip32:/m/44'/501'/39'"),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES)
        ),
        SigningResponse(
            listOf(
                byteArrayOf(-72, -67, -47, 60, -74, 22, 3, 95, -16, -30, 22, -104, -92, 10, 31, 32, -42, -108, 41, 57, -67, 59, 82, -82, -18, -70, -55, -88, 111, 122, 104, -7, -7, -11, -10, 22, 29, 34, 63, 93, 52, 37, -48, 76, -15, 10, 0, 12, 94, 101, 35, -30, -128, 81, -35, 101, -4, 13, -9, -84, -91, -94, 11, 0),
                byteArrayOf(-9, -42, -30, 59, 62, 45, 47, -6, -38, -102, -4, 105, -99, -13, -60, 3, 55, 45, 24, -65, -55, 4, 17, -4, -59, 76, 45, 95, 72, 39, -66, -124, -75, 104, -103, -104, 103, -5, -122, 62, 93, -6, 114, -75, -126, -86, 37, -116, -18, -31, -43, -78, 25, 85, 63, 71, -92, 127, -13, 12, -41, -125, 41, 3),
                byteArrayOf(115, -45, 12, 69, 71, 113, -38, -62, 47, -127, 94, -29, -89, 80, -77, 38, 88, -24, -90, 59, -40, 41, 34, 34, -56, 15, 94, 119, -127, -52, 82, 40, -21, -90, -93, 10, -33, 20, -64, -107, 10, -33, -41, 8, -47, -116, 72, 1, -90, 95, -106, 99, -32, 73, -58, -57, -65, -5, -2, 0, 30, 66, 40, 9),
                byteArrayOf(93, -47, 103, -105, -104, -95, 28, 2, 90, 74, 30, -76, 78, 83, 73, -97, 45, 45, -126, 113, -73, 44, -15, 55, -83, 49, 45, -95, 125, 33, -78, -30, 47, 72, -27, -91, -93, -126, 69, 119, -47, -110, 81, -37, 119, -88, 124, -36, 12, -35, 117, 17, -92, 7, -120, 118, 79, -96, -107, -109, -28, -126, -40, 6),
                byteArrayOf(69, -111, -94, -98, 42, 62, 29, 58, -93, 57, -9, 26, 127, -103, -54, -29, -66, -104, 77, -57, 15, 12, -95, 105, 124, 16, -17, -10, -34, 64, 11, 125, -27, -110, -57, -83, -65, -59, -44, 14, 113, 57, -11, 89, 126, -26, -44, -113, 77, 1, 41, 83, -1, 15, 84, 105, 49, -53, -122, 47, 35, -59, -38, 9),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES),
            listOf(
                Uri.parse("bip32:/m/44'/501'/40'"),
                Uri.parse("bip32:/m/44'/501'/41'"),
                Uri.parse("bip32:/m/44'/501'/42'"),
                Uri.parse("bip32:/m/44'/501'/43'"),
                Uri.parse("bip32:/m/44'/501'/44'"),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES)
        ),
        SigningResponse(
            listOf(
                byteArrayOf(30, 21, -85, -72, -16, 94, 22, 96, 32, -81, -89, 88, 62, -123, -66, -38, -98, 97, 72, 67, 24, 54, -13, -105, -27, 40, 70, -75, -59, 62, 66, -28, -68, -58, -63, 37, 23, -38, 78, -105, -63, -45, 44, -35, -8, 79, -125, -109, -106, 105, 92, 7, -57, -111, -42, 48, -117, 73, -66, 81, -98, -68, 42, 13),
                byteArrayOf(-116, 123, -29, 27, 103, -75, 34, -48, 4, -35, 61, 29, 15, 57, -39, -24, -25, 64, -32, -45, 114, 117, 1, 15, -22, 84, 6, -84, -43, 72, -12, 63, -73, 10, -13, -38, 69, 24, 13, -34, -92, 85, -100, -46, 1, 43, 47, 47, 101, -35, 86, 42, 18, -56, 94, 8, 76, 115, -99, 10, -89, -36, -104, 15),
                byteArrayOf(-7, 119, 41, 0, 90, 81, 72, 60, 31, -27, -109, 83, -106, 68, 83, -25, -8, -76, 27, 3, 83, -32, -53, 113, 120, -78, -86, -108, -50, 109, 117, 114, -94, 75, -77, -78, 67, 114, 45, -65, -42, 87, -15, -107, 82, 69, -81, 33, -72, 21, 60, -111, -71, 67, 1, -103, 38, 16, 72, -66, -35, -70, -122, 8),
                byteArrayOf(61, -17, -92, -116, 7, -4, -73, -88, 111, -72, -18, 0, -127, 9, -103, -110, -68, 69, -44, -66, -23, 18, 63, 70, -74, -93, 82, -99, -6, -120, -98, -110, 121, 33, 42, -21, -89, -12, 21, -45, -3, 104, -72, -55, 24, 118, 63, 51, 115, -90, -123, -55, 30, -22, -88, 113, -76, -2, 41, 28, -89, -69, -20, 2),
                byteArrayOf(-47, 74, 63, -58, 95, -76, 77, 18, -126, -41, 104, -52, -1, -38, -76, 88, -56, -92, 63, 75, 100, 120, -61, 84, 4, -31, 94, 109, -53, 106, 71, 48, 73, -55, -95, -84, 29, -51, 53, 51, 80, -60, -77, -12, 75, 67, -58, -62, 122, 29, 93, -34, 50, 23, 63, 82, -15, 61, -39, -26, 117, 124, 16, 11),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES),
            listOf(
                Uri.parse("bip32:/m/44'/501'/45'"),
                Uri.parse("bip32:/m/44'/501'/46'"),
                Uri.parse("bip32:/m/44'/501'/47'"),
                Uri.parse("bip32:/m/44'/501'/48'"),
                Uri.parse("bip32:/m/44'/501'/49'"),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES)
        )
    ).take(implementationDetails.MAX_SIGNING_REQUESTS)
), ActivityLauncherTestCase {
    override val id: String = "smaxtmaxs"
    override val description: String
        get() {
            val limits = getLimits(context)
            return "Sign ${limits.first} transaction with ${limits.second} signature"
        }
    override val instructions: String = "Approve transaction when prompted."

    init {
        check(implementationDetails.MAX_SIGNING_REQUESTS <= 10) { "Test case implementation currently only handles up to 10 transactions per signing request" }
        check(implementationDetails.MAX_REQUESTED_SIGNATURES <= 5) { "Test case implementation currently only handles up to 5 signatures per transaction" }
    }
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
    expectedSignatures = arrayListOf(
        SigningResponse(
            listOf(
                byteArrayOf(-42, -48, 114, 114, -26, 18, -123, -63, 123, 90, -55, -31, -97, -26, 72, 89, 52, -81, 73, -39, -94, -46, -128, 66, 119, -100, 115, -26, -23, 86, 3, -17, 109, -105, -10, 46, -114, -38, -72, -67, 92, 68, 59, -111, 80, -15, -4, 37, 32, -42, -11, 50, 71, 31, -7, -41, 100, -32, -124, 26, -119, 25, 121, 11),
                byteArrayOf(-78, -89, 127, -100, 89, -66, -105, -97, 86, 84, 108, 48, -35, -109, -30, 57, -108, -29, -6, -98, 83, 110, -11, -50, 117, -65, -128, -67, 6, -67, -36, -47, 47, 57, 89, -30, 33, -58, -3, 66, -47, -47, 25, -110, -87, -91, 17, 121, 56, -79, -20, 85, 69, -106, 123, 110, 96, 100, -63, 40, 73, -84, -82, 10),
                byteArrayOf(121, 3, 105, 2, 116, -68, 122, -109, -26, -66, 120, 9, -93, -91, 65, 12, -107, 86, -16, -109, -32, 84, 36, -52, 127, 57, -124, 97, 124, -76, 30, 81, -18, 52, 96, 14, -67, -20, 47, 69, -37, -68, -80, -50, -84, -114, 41, -92, -71, 3, 74, -36, 120, -54, -43, -77, -39, -80, 102, 67, -82, 16, -123, 14),
                byteArrayOf(82, 24, 120, 106, 33, 48, -126, 125, 47, 67, -88, 63, -85, 62, -97, 70, -52, 70, 34, 112, -72, -76, 123, -124, -95, -55, 114, -126, -28, 31, 3, -99, -94, 53, 84, -93, -83, 37, 98, -58, 2, 37, 82, -93, -40, 117, 78, -114, -112, 73, 85, 25, 70, 107, -112, -107, -39, 110, 73, 86, -110, 88, 102, 6),
                byteArrayOf(-51, -120, 34, -80, 60, 88, -95, -5, 100, 38, 35, -6, 80, 110, 65, 44, -103, -101, 115, 65, -66, -115, -46, 43, 27, 117, 110, -42, 79, -124, 22, -20, 60, -19, 124, -124, 105, 122, -48, -55, 121, -96, 118, 27, -14, 78, 67, 48, 41, 105, 41, 30, 26, -17, -80, 9, -60, -14, -91, -8, -30, 111, 4, 4),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES),
            listOf(
                Uri.parse("bip32:/m/44'/501'/0'"),
                Uri.parse("bip32:/m/44'/501'/1'"),
                Uri.parse("bip32:/m/44'/501'/2'"),
                Uri.parse("bip32:/m/44'/501'/3'"),
                Uri.parse("bip32:/m/44'/501'/4'"),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES)
        ),
        SigningResponse(
            listOf(
                byteArrayOf(97, -25, 36, 66, 55, 113, -16, -9, 100, -101, -109, 31, -63, 29, -33, -15, 61, -120, 36, 103, -103, 85, 112, 88, -60, -48, -128, -11, -116, 46, 54, -46, 49, -59, -75, 108, -101, -105, 4, 49, -77, -97, -89, 47, -52, 56, -127, 81, 25, -24, 56, 112, -75, 81, -33, -75, 89, -44, -18, 122, -108, 108, 5, 4),
                byteArrayOf(-43, -23, -119, -16, -127, -119, -92, -119, -46, 86, -8, 70, 105, -44, -6, 0, -4, 119, 84, 8, 120, 81, 91, 124, -5, 43, 104, 107, -61, 6, 52, 10, -4, 0, -70, 123, -108, -68, 99, -125, 27, -105, -11, 93, -124, -31, 109, -73, -7, -119, 21, 111, 123, -81, 17, 51, -97, 69, 68, -97, 62, -57, 49, 6),
                byteArrayOf(98, -101, 117, -41, -37, -112, -125, 116, -124, 84, -100, -117, -92, -95, 35, -20, 26, -76, 95, 46, -24, 23, -120, 24, 39, -24, 102, 112, 21, 20, 40, 89, -60, 60, 87, -43, -122, -62, 14, 111, -41, -36, 49, 82, 84, -14, -102, -40, -46, -123, -101, 80, 40, -56, -53, 64, -27, 41, 95, -62, 127, 40, -128, 11),
                byteArrayOf(-104, -89, 71, -16, 0, -64, -44, -113, 112, 57, 30, -122, 40, 43, 85, -15, 116, -120, 23, 104, -83, 83, 75, -51, -24, 2, 119, 123, 8, 84, -18, -39, -7, 97, -25, 109, 109, 67, 103, 24, 9, 12, -12, -49, 92, -86, 116, -20, 29, -109, 22, 105, -42, 53, -86, 30, 96, -12, 68, -29, 85, -13, -48, 8),
                byteArrayOf(127, -57, -100, -79, 90, -57, 115, 63, -20, 89, -31, 3, -93, 98, 69, 70, -97, -74, 42, -85, 90, 98, -56, -65, -54, -59, 19, 100, 0, 115, 52, 122, 34, -96, 96, -66, 46, 77, -57, 42, -120, -65, 3, 7, -66, 70, 63, 52, -85, 31, -104, 20, -81, -111, -42, -70, 24, 84, 85, 35, -3, -44, 127, 6),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES),
            listOf(
                Uri.parse("bip32:/m/44'/501'/5'"),
                Uri.parse("bip32:/m/44'/501'/6'"),
                Uri.parse("bip32:/m/44'/501'/7'"),
                Uri.parse("bip32:/m/44'/501'/8'"),
                Uri.parse("bip32:/m/44'/501'/9'"),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES)
        ),
        SigningResponse(
            listOf(
                byteArrayOf(-2, -55, -48, -111, -19, -42, 83, 64, -33, 16, 71, -17, 59, -10, 111, 39, 49, 69, -107, -39, 87, 42, 2, -117, 32, -83, -52, 22, 113, -107, 31, 7, -104, 44, -89, 126, -44, -23, -103, -108, 26, -77, -19, -10, 95, -54, 52, 58, 126, 68, -39, 101, -12, -89, -29, -39, -9, 78, -128, 5, 116, 7, -96, 5),
                byteArrayOf(-114, -48, -4, 3, -11, 118, 117, 78, -86, 119, 72, 42, -61, -118, 99, 24, -6, 70, -110, 16, 41, -9, -17, 6, -43, 95, -21, 82, -40, -95, -16, -106, -23, 36, -75, 10, 97, -82, 115, 58, 68, 126, -116, 42, -30, -74, 85, 107, -63, -37, -88, 48, 98, 66, -88, 17, -14, 51, 11, -60, -78, -115, 41, 10),
                byteArrayOf(18, 2, 63, 120, 24, 38, -80, -72, -125, -110, 66, -30, -57, -115, -109, -31, 38, 23, 101, -48, -80, 43, -55, -68, -4, 26, -100, 4, 88, 103, 9, 0, -110, 59, 102, 43, 73, -83, -97, 43, -96, -39, 11, -41, -123, 43, -82, -122, 4, -67, 5, -89, 106, 125, 77, 43, -109, -80, 60, 87, -71, 38, 126, 1),
                byteArrayOf(-119, -93, 18, -11, 44, -108, -72, -25, -50, 99, -117, 124, -46, -28, -64, 46, 47, 45, -56, 99, 92, -19, -26, 100, -84, 5, -25, -124, -38, 82, -106, -46, -79, -36, -79, -4, 79, 122, -5, -63, 34, 19, -105, -68, 124, 23, -117, 52, -98, -106, 117, 96, -94, -117, -1, -49, 53, 87, 50, 84, -27, -125, 100, 10),
                byteArrayOf(118, 73, -74, -32, 33, -94, 126, -1, -47, -11, 115, 52, -49, 123, 12, 47, 113, -84, -9, -81, -9, 106, 54, 70, -95, 0, 74, -32, 33, 22, 103, -38, -56, -63, 108, 99, 122, -7, -49, 100, 103, 86, -13, 0, -45, -57, 119, 84, -19, -84, -64, 123, 111, -118, -112, -115, 81, 39, 63, 45, 14, -101, 71, 0),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES),
            listOf(
                Uri.parse("bip32:/m/44'/501'/10'"),
                Uri.parse("bip32:/m/44'/501'/11'"),
                Uri.parse("bip32:/m/44'/501'/12'"),
                Uri.parse("bip32:/m/44'/501'/13'"),
                Uri.parse("bip32:/m/44'/501'/14'"),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES)
        ),
        SigningResponse(
            listOf(
                byteArrayOf(-26, 47, -95, -6, -47, -41, 24, 103, 40, -63, 102, 62, 74, 10, 47, 23, -8, -126, 19, 50, -22, -10, 96, 101, -71, -120, -126, -94, 84, 36, -92, -42, -34, 78, -83, 74, 44, 65, -4, -41, -50, -67, 28, 80, 122, 13, -92, 72, 64, -31, 97, -110, -125, 12, -93, 10, 81, 103, 21, 48, 72, 109, -80, 5),
                byteArrayOf(76, -51, 22, -114, 33, 75, -123, 123, -115, 22, 10, -73, -77, -92, -87, -64, -30, -34, 106, 74, -19, 126, -19, 76, -88, -6, 58, 37, -57, 56, 3, -45, -47, 71, 126, -72, -75, -44, 75, -33, -104, -60, -128, 48, -36, 64, 10, -97, 81, 11, -123, -115, 63, -123, -99, -3, -115, -11, 65, 74, 2, -37, 113, 6),
                byteArrayOf(49, -92, -47, -56, 106, -126, -47, -61, -42, 94, -46, 15, -26, 72, 98, -18, 54, 9, -95, -62, 4, 85, 76, 106, -111, 58, 15, -27, 103, -46, 109, 120, -81, -93, 89, -24, 27, -125, -65, 53, -2, 102, 117, 74, 15, -43, -23, -39, -7, 123, 119, 104, 112, 103, -107, -120, -35, 48, 47, 116, -118, 10, -35, 9),
                byteArrayOf(-25, 74, -115, -84, -40, 57, -70, -16, -41, -70, -46, -50, 8, 67, -79, 26, 4, 114, 126, -85, -54, -70, -127, 76, 88, -14, 114, -95, 110, 65, 43, 48, -35, -90, -116, -21, -13, 78, 17, 0, 80, -44, -121, 31, 44, 44, -13, -36, -69, 82, -30, -30, 62, 46, -19, 40, 19, 40, -88, 9, -60, -128, -90, 13),
                byteArrayOf(-110, 61, 83, -113, 5, 105, -38, 111, 67, -114, 30, 39, 90, -110, -7, 22, 103, -1, 23, -114, 51, -84, -42, -107, -92, 105, 48, 35, 30, 81, 88, 10, -11, -5, -81, -29, -86, 36, 12, -90, 86, -81, -10, 22, 108, -102, -57, 50, -1, 111, 91, -100, -30, 33, -112, 67, -57, 118, 54, -96, 10, 25, 88, 4),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES),
            listOf(
                Uri.parse("bip32:/m/44'/501'/15'"),
                Uri.parse("bip32:/m/44'/501'/16'"),
                Uri.parse("bip32:/m/44'/501'/17'"),
                Uri.parse("bip32:/m/44'/501'/18'"),
                Uri.parse("bip32:/m/44'/501'/19'"),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES)
        ),
        SigningResponse(
            listOf(
                byteArrayOf(52, -12, 112, -27, -1, -7, 40, 94, -109, 65, 117, 3, -49, -17, -3, -83, 39, -40, 58, -17, 97, -104, 96, 56, 71, 16, 22, 75, 79, -125, 35, -120, 15, -10, 81, 93, 5, 78, -88, 18, 119, 2, -83, 73, 0, -74, -19, -103, 25, -97, 99, -6, -61, -120, 86, -114, 96, -115, -47, 78, -88, 108, -12, 14),
                byteArrayOf(-51, 31, -86, 87, 125, -3, -11, 104, -96, 26, -60, 2, -42, -62, -45, 31, 87, -33, -117, 45, 81, 69, -66, 56, -24, 127, -10, -19, -41, -127, 10, -32, 94, -30, -20, 126, -99, 99, -15, 35, 16, 116, -85, -113, 54, 3, -107, -66, 3, 77, -67, 127, 15, -21, 100, 21, 8, -103, 65, 36, 5, -96, -22, 5),
                byteArrayOf(65, -61, -41, 29, 98, 15, 112, -128, -11, -11, 113, -23, -36, -126, -44, -77, 55, -14, 110, 114, 28, 61, 94, -103, 116, 78, -108, -108, 113, -49, -13, -29, -93, -6, 38, 119, 71, -35, 65, 111, -86, -5, -125, -15, -102, -122, 61, 0, 74, -4, -32, -40, 96, 85, 30, -76, -125, -73, -2, -53, -75, -33, -73, 0),
                byteArrayOf(-104, 22, -30, 0, -68, -29, -40, -17, -113, 53, 56, 89, -72, 27, -13, 90, -34, 89, 87, -14, 9, -95, 1, 18, 90, 95, -71, -16, 21, 45, 99, -48, 49, -15, -115, 27, -96, -118, 101, 37, -58, 70, -110, 9, -16, -60, -108, 67, 11, -69, 97, -128, -52, 53, 111, -105, -34, -34, -103, -93, 19, -39, 20, 1),
                byteArrayOf(111, -99, -95, -33, -70, 82, -116, 68, -30, -52, -110, 76, -69, -21, 87, -128, -73, -33, 123, -4, -84, -1, -126, -62, 13, -53, -110, -6, -110, 27, 33, -128, -49, 98, 69, 117, -56, 48, 19, -111, 101, -76, 5, 107, 7, -30, 28, -48, -99, 111, 70, -11, 112, 74, -19, -121, 72, 126, -19, -27, 8, 9, 34, 11),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES),
            listOf(
                Uri.parse("bip32:/m/44'/501'/20'"),
                Uri.parse("bip32:/m/44'/501'/21'"),
                Uri.parse("bip32:/m/44'/501'/22'"),
                Uri.parse("bip32:/m/44'/501'/23'"),
                Uri.parse("bip32:/m/44'/501'/24'"),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES)
        ),
        SigningResponse(
            listOf(
                byteArrayOf(16, -14, -16, 6, 8, 29, 114, 14, 10, 30, 88, -34, 105, -87, -54, -117, 50, 39, 13, -103, 64, 95, -65, -78, 48, 58, 67, -11, 45, 41, 113, -27, 45, 99, -18, 99, -9, -41, 85, 123, 0, 75, 26, -76, 8, -31, 14, 28, 60, 27, 45, 82, -112, 99, 67, 116, 100, -23, -97, -4, 110, -10, 92, 14),
                byteArrayOf(-75, 37, 60, 81, -92, 110, 23, 93, 74, -127, 81, -21, -87, 21, -89, -82, 17, 90, -111, -63, 45, -65, -32, 103, -31, -8, -38, -40, 81, -98, 42, -47, -55, 76, -116, 102, -78, 69, -5, 77, -90, -34, 94, -109, 89, -41, 74, -75, 96, 52, -33, -25, 30, -106, 60, 105, 70, -93, 15, -39, 85, -28, -61, 2),
                byteArrayOf(-79, 78, 9, 4, -76, -20, -40, -8, -63, -15, -121, 90, 63, -72, -48, 94, 73, -55, 102, -76, 17, -107, 87, 9, -31, -3, 82, -21, -73, 51, 70, -72, -23, 24, 5, -36, -60, 12, 15, 48, 118, 100, 89, 66, -15, -36, -81, -10, -103, -115, -5, -55, -14, 17, 87, 121, 32, -71, -23, 118, 97, 47, 82, 3),
                byteArrayOf(4, 49, -52, 64, 110, -79, -11, -99, -79, 6, 19, -22, -107, -75, -19, -5, 52, -51, 98, -72, 27, -65, 78, 54, 30, -72, -119, -19, 53, 91, -122, -104, 4, -21, 32, -113, 0, -35, 32, -96, 4, -123, -105, 54, 103, 4, 16, -35, -11, -110, -85, -103, 120, -32, 28, -33, -25, -78, 16, 64, -1, 52, 34, 4),
                byteArrayOf(-106, 58, -37, 99, -28, -54, 81, -63, -64, -94, 37, 104, -15, -9, -105, -99, -41, -97, -45, 79, -113, 91, 93, 60, 80, -106, -92, -98, 25, -60, -125, 110, -104, -12, 98, -85, 120, -118, 108, 60, 76, -54, -70, 10, -32, 28, 45, 74, 124, 25, 115, -2, 105, -119, 17, 103, -73, 94, 97, 0, -11, 19, 6, 8),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES),
            listOf(
                Uri.parse("bip32:/m/44'/501'/25'"),
                Uri.parse("bip32:/m/44'/501'/26'"),
                Uri.parse("bip32:/m/44'/501'/27'"),
                Uri.parse("bip32:/m/44'/501'/28'"),
                Uri.parse("bip32:/m/44'/501'/29'"),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES)
        ),
        SigningResponse(
            listOf(
                byteArrayOf(123, -38, 112, 103, -27, 36, -89, 125, 17, 121, 20, -44, -71, -29, -5, 92, 55, 18, 30, 15, -69, 46, 109, 58, 45, -14, -128, -20, -53, 120, 30, 21, 21, 87, -68, -76, -9, -119, 37, 31, -127, -46, 24, 96, 96, -5, -12, 63, 92, 18, 45, 76, 12, -107, 45, -28, 62, 72, 37, 82, -112, -15, -62, 8),
                byteArrayOf(65, 64, -118, -93, 65, -126, 116, -54, 121, 104, -98, 16, 94, 75, -19, 35, -121, -89, 99, -71, -116, 15, -6, -44, -101, -25, -26, 29, 117, 17, -85, -52, -8, -74, -5, -70, -60, -116, 79, -68, -22, -87, -51, 94, 83, -34, -20, -55, 114, -118, 43, -67, -85, 106, -56, -68, -122, 73, -53, 121, -44, -101, 93, 7),
                byteArrayOf(0, -53, 64, 76, 21, -2, -120, -126, -52, -50, -76, 29, 84, -123, 127, -77, -18, -99, -83, -88, 91, -9, 95, -114, -3, 113, 16, 7, 51, -98, -89, -96, 44, -76, -62, -3, 83, -29, 22, 62, 89, 101, 85, -23, -120, 16, 121, 99, 48, 58, 127, -39, 19, 106, 47, 83, -42, 92, -60, -42, 17, -30, -127, 15),
                byteArrayOf(-66, 72, -82, -77, 61, -58, 81, 10, 74, -102, -108, 37, 114, -96, 51, -69, -100, -44, 49, -1, -108, 115, -73, -42, 48, 5, -21, -9, -101, -117, -14, -47, 56, 40, -62, -122, -53, 13, -72, -82, -82, -40, -25, 85, -96, -105, 5, -26, 71, -73, 0, 15, -67, -6, -47, 28, 99, 59, -63, 26, 29, 61, -53, 2),
                byteArrayOf(64, -48, 54, -64, -102, 62, 24, 3, -118, -80, -108, -88, 125, 20, -23, -116, 41, 21, 40, -78, -108, -2, 30, 19, 52, -105, -24, -119, -101, -72, 57, -87, 101, 47, 31, -19, 102, -113, -84, -110, -90, 118, -19, 47, 100, 68, -52, -80, 67, 4, 110, -77, -6, 25, 31, 125, -54, 43, 76, 25, 16, -87, -96, 9),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES),
            listOf(
                Uri.parse("bip32:/m/44'/501'/30'"),
                Uri.parse("bip32:/m/44'/501'/31'"),
                Uri.parse("bip32:/m/44'/501'/32'"),
                Uri.parse("bip32:/m/44'/501'/33'"),
                Uri.parse("bip32:/m/44'/501'/34'"),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES)
        ),
        SigningResponse(
            listOf(
                byteArrayOf(106, -90, 69, 30, 6, -126, -27, -111, 82, -91, 116, -45, 109, 71, -27, -10, 92, 127, 8, -92, -2, 110, -106, -26, 15, -95, -73, 82, -77, -17, 10, -19, -48, 8, 108, 126, -60, -1, 88, 47, -59, 91, 43, 108, -31, -110, 32, -114, -23, -21, 47, -9, 32, -41, 69, -53, -86, 108, -70, -93, -79, -10, -48, 8),
                byteArrayOf(71, 5, 63, 34, -39, -12, -101, 21, -44, -110, -30, 123, 43, -115, 48, -32, 80, 16, 66, -111, 45, -19, 16, 118, 82, -105, -14, -62, -39, -121, 42, -11, 47, 55, 59, 86, -120, -7, -82, 77, 117, 80, 6, -112, -30, 68, -40, 35, 4, 19, 104, -123, -9, 47, -83, -110, -97, -3, 93, -75, -99, 121, 63, 0),
                byteArrayOf(-110, -39, 49, 54, -70, -43, 103, 119, 100, -3, -33, -65, -74, -7, -45, 14, 124, -4, 68, -69, -36, 0, 69, 115, -11, -90, -94, -74, -88, -45, -94, -107, 29, -115, -81, 115, 94, -79, 66, -74, -64, 58, -41, -110, -43, 126, -17, 95, 9, -26, 83, 75, 50, 95, 86, -88, 94, -31, -20, -25, 68, 9, 115, 10),
                byteArrayOf(-116, 20, 78, -116, -55, -78, 34, 22, 17, -40, 60, -16, -11, 60, -105, 108, -1, 15, -94, 96, 66, 40, 113, 46, 23, 55, -105, 49, -29, 66, -110, 65, -48, 90, 109, -66, 47, -128, -44, 61, -93, 123, -112, 83, -93, -89, -49, 61, -119, 64, -111, -35, 3, 82, 19, 48, 101, 76, 77, 51, 104, 108, -46, 2),
                byteArrayOf(119, 50, -50, 87, 102, -39, -104, -9, 19, -56, 120, -69, -16, 34, -111, -10, 84, 111, 98, -55, -88, 85, 42, -82, -82, -80, 19, 98, -65, -119, -15, -101, 68, -28, 63, -24, 79, -99, 33, -68, -89, -106, -58, -111, 97, 121, -54, 44, 8, -123, 107, 25, 46, -75, -98, -8, 18, -102, 11, -48, 82, 44, -49, 6),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES),
            listOf(
                Uri.parse("bip32:/m/44'/501'/35'"),
                Uri.parse("bip32:/m/44'/501'/36'"),
                Uri.parse("bip32:/m/44'/501'/37'"),
                Uri.parse("bip32:/m/44'/501'/38'"),
                Uri.parse("bip32:/m/44'/501'/39'"),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES)
        ),
        SigningResponse(
            listOf(
                byteArrayOf(-72, -67, -47, 60, -74, 22, 3, 95, -16, -30, 22, -104, -92, 10, 31, 32, -42, -108, 41, 57, -67, 59, 82, -82, -18, -70, -55, -88, 111, 122, 104, -7, -7, -11, -10, 22, 29, 34, 63, 93, 52, 37, -48, 76, -15, 10, 0, 12, 94, 101, 35, -30, -128, 81, -35, 101, -4, 13, -9, -84, -91, -94, 11, 0),
                byteArrayOf(-9, -42, -30, 59, 62, 45, 47, -6, -38, -102, -4, 105, -99, -13, -60, 3, 55, 45, 24, -65, -55, 4, 17, -4, -59, 76, 45, 95, 72, 39, -66, -124, -75, 104, -103, -104, 103, -5, -122, 62, 93, -6, 114, -75, -126, -86, 37, -116, -18, -31, -43, -78, 25, 85, 63, 71, -92, 127, -13, 12, -41, -125, 41, 3),
                byteArrayOf(115, -45, 12, 69, 71, 113, -38, -62, 47, -127, 94, -29, -89, 80, -77, 38, 88, -24, -90, 59, -40, 41, 34, 34, -56, 15, 94, 119, -127, -52, 82, 40, -21, -90, -93, 10, -33, 20, -64, -107, 10, -33, -41, 8, -47, -116, 72, 1, -90, 95, -106, 99, -32, 73, -58, -57, -65, -5, -2, 0, 30, 66, 40, 9),
                byteArrayOf(93, -47, 103, -105, -104, -95, 28, 2, 90, 74, 30, -76, 78, 83, 73, -97, 45, 45, -126, 113, -73, 44, -15, 55, -83, 49, 45, -95, 125, 33, -78, -30, 47, 72, -27, -91, -93, -126, 69, 119, -47, -110, 81, -37, 119, -88, 124, -36, 12, -35, 117, 17, -92, 7, -120, 118, 79, -96, -107, -109, -28, -126, -40, 6),
                byteArrayOf(69, -111, -94, -98, 42, 62, 29, 58, -93, 57, -9, 26, 127, -103, -54, -29, -66, -104, 77, -57, 15, 12, -95, 105, 124, 16, -17, -10, -34, 64, 11, 125, -27, -110, -57, -83, -65, -59, -44, 14, 113, 57, -11, 89, 126, -26, -44, -113, 77, 1, 41, 83, -1, 15, 84, 105, 49, -53, -122, 47, 35, -59, -38, 9),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES),
            listOf(
                Uri.parse("bip32:/m/44'/501'/40'"),
                Uri.parse("bip32:/m/44'/501'/41'"),
                Uri.parse("bip32:/m/44'/501'/42'"),
                Uri.parse("bip32:/m/44'/501'/43'"),
                Uri.parse("bip32:/m/44'/501'/44'"),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES)
        ),
        SigningResponse(
            listOf(
                byteArrayOf(30, 21, -85, -72, -16, 94, 22, 96, 32, -81, -89, 88, 62, -123, -66, -38, -98, 97, 72, 67, 24, 54, -13, -105, -27, 40, 70, -75, -59, 62, 66, -28, -68, -58, -63, 37, 23, -38, 78, -105, -63, -45, 44, -35, -8, 79, -125, -109, -106, 105, 92, 7, -57, -111, -42, 48, -117, 73, -66, 81, -98, -68, 42, 13),
                byteArrayOf(-116, 123, -29, 27, 103, -75, 34, -48, 4, -35, 61, 29, 15, 57, -39, -24, -25, 64, -32, -45, 114, 117, 1, 15, -22, 84, 6, -84, -43, 72, -12, 63, -73, 10, -13, -38, 69, 24, 13, -34, -92, 85, -100, -46, 1, 43, 47, 47, 101, -35, 86, 42, 18, -56, 94, 8, 76, 115, -99, 10, -89, -36, -104, 15),
                byteArrayOf(-7, 119, 41, 0, 90, 81, 72, 60, 31, -27, -109, 83, -106, 68, 83, -25, -8, -76, 27, 3, 83, -32, -53, 113, 120, -78, -86, -108, -50, 109, 117, 114, -94, 75, -77, -78, 67, 114, 45, -65, -42, 87, -15, -107, 82, 69, -81, 33, -72, 21, 60, -111, -71, 67, 1, -103, 38, 16, 72, -66, -35, -70, -122, 8),
                byteArrayOf(61, -17, -92, -116, 7, -4, -73, -88, 111, -72, -18, 0, -127, 9, -103, -110, -68, 69, -44, -66, -23, 18, 63, 70, -74, -93, 82, -99, -6, -120, -98, -110, 121, 33, 42, -21, -89, -12, 21, -45, -3, 104, -72, -55, 24, 118, 63, 51, 115, -90, -123, -55, 30, -22, -88, 113, -76, -2, 41, 28, -89, -69, -20, 2),
                byteArrayOf(-47, 74, 63, -58, 95, -76, 77, 18, -126, -41, 104, -52, -1, -38, -76, 88, -56, -92, 63, 75, 100, 120, -61, 84, 4, -31, 94, 109, -53, 106, 71, 48, 73, -55, -95, -84, 29, -51, 53, 51, 80, -60, -77, -12, 75, 67, -58, -62, 122, 29, 93, -34, 50, 23, 63, 82, -15, 61, -39, -26, 117, 124, 16, 11),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES),
            listOf(
                Uri.parse("bip32:/m/44'/501'/45'"),
                Uri.parse("bip32:/m/44'/501'/46'"),
                Uri.parse("bip32:/m/44'/501'/47'"),
                Uri.parse("bip32:/m/44'/501'/48'"),
                Uri.parse("bip32:/m/44'/501'/49'"),
            ).take(implementationDetails.MAX_REQUESTED_SIGNATURES)
        )
    ).take(implementationDetails.MAX_SIGNING_REQUESTS)
), ActivityLauncherTestCase {
    override val id: String = "smaxtmaxsb44"
    override val description: String
        get() {
            val limits = getLimits(context)
            return "Sign ${limits.first} transaction with ${limits.second} signature using BIP-44 derivation paths"
        }
    override val instructions: String = "Approve transaction when prompted."

    init {
        check(implementationDetails.MAX_SIGNING_REQUESTS <= 10) { "Test case implementation currently only handles up to 10 transactions per signing request" }
        check(implementationDetails.MAX_REQUESTED_SIGNATURES <= 5) { "Test case implementation currently only handles up to 5 signatures per transaction" }
    }
}

internal class SignTransactionRequestsExceedLimitTestCase @Inject constructor(
    @ApplicationContext private val context: Context,
    logger: TestSessionLogger,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
) : SignNTransactionsMSignaturesTestCase(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker),
    authorizedSeedsChecker = knownSeed12AuthorizedChecker,
    logger = logger,
    signingRequests = {
        val limits = getLimits(context)
        signMTransactionsWithNSignatures(context, limits.first + 1, 1, false)
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
            return "Sign ${limits.first + 1} transactions"
        }
    override val instructions: String = ""
}

internal class SignTransactionSignaturesExceedLimitTestCase @Inject constructor(
    @ApplicationContext private val context: Context,
    logger: TestSessionLogger,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
) : SignNTransactionsMSignaturesTestCase(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker),
    authorizedSeedsChecker = knownSeed12AuthorizedChecker,
    logger = logger,
    signingRequests = {
        val limits = getLimits(context)
        signMTransactionsWithNSignatures(context, 1, limits.second + 1, false)
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
