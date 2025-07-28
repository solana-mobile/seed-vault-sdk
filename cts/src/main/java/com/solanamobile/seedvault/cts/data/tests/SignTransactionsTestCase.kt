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
            Wallet.signTransactions(input.authToken, ArrayList(input.requests))

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
            useBip44DerivationPaths: Boolean
        ): ArrayList<SigningRequest> {
            val implementationLimits = Wallet.getImplementationLimitsForPurpose(
                context,
                WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION
            )
            val maxRequestedSignatures =
                implementationLimits[WalletContractV1.IMPLEMENTATION_LIMITS_MAX_REQUESTED_SIGNATURES]!!.toInt()

            val signingRequests = (0 until transactions).map { i ->
                val derivationPaths = (0 until signaturesPerTransaction).map { j ->
                    if (useBip44DerivationPaths) {
                        Bip44DerivationPath.newBuilder()
                            .setAccount(BipLevel(i * maxRequestedSignatures + j, true)).build().toUri()
                    } else {
                        Bip32DerivationPath.newBuilder()
                            .appendLevel(BipLevel(44, true))
                            .appendLevel(BipLevel(501, true))
                            .appendLevel(BipLevel(i * maxRequestedSignatures + j, true))
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
                byteArrayOf(121, 3, 105, 2, 116, -68, 122, -109, -26, -66, 120, 9, -93, -91, 65, 12, -107, 86, -16, -109, -32, 84, 36, -52, 127, 57, -124, 97, 124, -76, 30, 81, -18, 52, 96, 14, -67, -20, 47, 69, -37, -68, -80, -50, -84, -114, 41, -92, -71, 3, 74, -36, 120, -54, -43, -77, -39, -80, 102, 67, -82, 16, -123, 14)
            ),
            listOf(
                Uri.parse("bip32:/m/44'/501'/0'"),
                Uri.parse("bip32:/m/44'/501'/1'"),
                Uri.parse("bip32:/m/44'/501'/2'"),
            )
        ),
        SigningResponse(
            listOf(
                byteArrayOf(0, -62, 74, 7, -114, -82, 95, -81, -107, 92, 21, -82, 84, 80, 74, 64, 35, 31, -83, 103, -127, -44, -27, 92, 83, -20, 122, -31, -127, 12, 2, 31, 73, -68, 75, 37, -62, 50, -46, 15, 12, 44, 38, -27, -127, 92, 31, -39, 95, -127, 94, -6, -69, 20, -44, 114, 121, 32, -99, -80, 6, -117, 105, 10),
                byteArrayOf(48, -71, 8, -85, -67, 99, 68, 38, 68, 6, 126, 35, 7, 28, -72, 122, 119, 19, 13, -38, 0, 120, -54, -109, -81, -77, -46, 7, 75, -2, 58, 82, -119, -71, 46, -109, 92, 61, 106, -46, 101, -42, -66, -73, -13, -34, -10, 37, 114, -27, -62, 85, 43, 92, -107, -18, -112, -97, -44, 24, 82, 48, 7, 9),
                byteArrayOf(97, -25, 36, 66, 55, 113, -16, -9, 100, -101, -109, 31, -63, 29, -33, -15, 61, -120, 36, 103, -103, 85, 112, 88, -60, -48, -128, -11, -116, 46, 54, -46, 49, -59, -75, 108, -101, -105, 4, 49, -77, -97, -89, 47, -52, 56, -127, 81, 25, -24, 56, 112, -75, 81, -33, -75, 89, -44, -18, 122, -108, 108, 5, 4)
            ),
            listOf(
                Uri.parse("bip32:/m/44'/501'/3'"),
                Uri.parse("bip32:/m/44'/501'/4'"),
                Uri.parse("bip32:/m/44'/501'/5'"),
            )
        ),
        SigningResponse(
            listOf(
                byteArrayOf(-80, 90, 86, -57, -60, 46, 6, -28, 75, 11, -35, -62, 56, 103, 106, -5, -117, -13, 60, 2, 61, 17, -103, 52, -45, 41, -101, -44, -5, 26, 70, 6, 41, 102, 68, 24, -121, 52, 18, 16, -17, -72, 26, 8, -102, -66, 16, 46, -79, -18, -75, 2, 85, 113, 19, 81, 43, -89, 84, 67, 30, -40, -128, 11),
                byteArrayOf(-101, 80, -35, 58, -57, 77, -96, -46, -38, 35, -97, -98, 33, 106, 31, 78, 43, 4, 53, 50, -120, -109, -95, 9, -61, 34, -52, 120, -94, -3, -79, 100, -118, 70, -42, 13, 27, 80, 47, 38, 48, 108, 21, -116, -100, -42, 64, -48, -118, 56, 17, -1, 92, 7, -116, 84, 121, -50, 122, -5, -91, -55, -35, 6),
                byteArrayOf(-14, 23, 100, -98, 109, 58, 10, 80, -37, 107, -111, 87, 86, -128, -94, 16, 59, 98, 115, -31, 124, -113, 34, -126, -7, 79, -76, 80, 55, 58, 124, -106, 110, 13, -71, -55, -115, 65, -16, -128, -116, -120, -6, -121, -49, -50, 49, -49, 61, 84, 83, -109, -39, -86, 15, 85, -78, -2, -66, 78, 78, -20, -67, 9)
            ),
            listOf(
                Uri.parse("bip32:/m/44'/501'/6'"),
                Uri.parse("bip32:/m/44'/501'/7'"),
                Uri.parse("bip32:/m/44'/501'/8'"),
            )
        ),
        SigningResponse(
            listOf(
                byteArrayOf(66, -27, -126, 65, -83, -29, -74, 76, -109, -128, -122, -95, 127, 111, 102, -124, -52, 59, 100, 59, 66, 58, 74, 65, -96, -22, 10, 51, 12, 93, -120, -98, -8, 113, -70, -29, -26, 49, -100, -117, 84, 32, -101, 96, 6, -30, 80, 43, -10, -14, -77, -113, -19, 53, -116, -92, 120, 75, 20, -70, -83, 123, 127, 12),
                byteArrayOf(55, -65, 49, 28, -33, 87, -72, -68, -57, 80, 38, -27, -113, 125, -20, 2, 13, 20, 76, 50, 103, -96, 104, -124, 18, -101, -108, 15, 1, 120, 83, -37, -40, -61, -82, -123, 24, -40, 122, -116, 38, 102, 98, -2, 37, 70, -52, -118, -74, 115, 113, -100, 17, 30, -118, -118, 47, -10, -27, 31, 8, -65, -64, 6),
                byteArrayOf(63, -90, -17, -87, 88, -70, 41, 44, 91, 4, 39, -94, 107, 118, 1, 12, 44, -70, -35, 15, 121, 79, -128, 19, -88, 115, 89, 8, 98, -77, 81, 97, -14, -36, 65, -56, 114, 42, 47, 25, -30, -115, -77, 3, -66, 68, -12, -104, -30, 53, 78, -98, -68, -85, 119, 94, -9, 4, -55, 70, -16, 32, 92, 14)
            ),
            listOf(
                Uri.parse("bip32:/m/44'/501'/9'"),
                Uri.parse("bip32:/m/44'/501'/10'"),
                Uri.parse("bip32:/m/44'/501'/11'"),
            )
        ),
        SigningResponse(
            listOf(
                byteArrayOf(-61, -59, 125, -71, 110, 106, 12, -47, -44, 27, -27, -94, 37, -88, 39, -53, -74, -27, -2, 88, -117, 5, -68, 42, 9, -23, 120, -23, -127, -128, 78, -38, 97, 43, -52, 45, 127, -30, 16, 67, -114, -109, -8, 104, 53, 120, 39, -115, -101, -99, -33, 12, -41, -63, -109, -69, -82, 22, -106, 39, -3, 22, -17, 10),
                byteArrayOf(-56, -12, -2, 124, 56, 76, 84, 33, 47, -75, 119, -48, -90, 54, 103, 24, 119, -24, 55, -44, 65, 49, -46, 57, -82, -95, -112, -51, 32, -26, 80, 86, -23, 16, 70, 125, 80, -27, 71, 31, 41, 72, -124, -45, -100, 49, -4, -85, -30, 78, -67, -21, 103, -70, -49, -87, -78, -81, 107, 121, -84, -45, 82, 4),
                byteArrayOf(103, -1, -111, -118, -119, -65, -108, 120, -56, -4, -83, -11, 120, 10, -79, 100, 9, -36, -107, -50, 29, 54, 65, 30, -47, -67, -21, -112, -8, -70, 24, -26, -81, 23, 64, 57, 90, 34, -95, 15, -88, -107, 123, -43, -3, 116, 76, -46, -78, -12, -15, 84, -7, 6, 55, 2, -125, -10, -74, -7, -95, 11, 78, 2)
            ),
            listOf(
                Uri.parse("bip32:/m/44'/501'/12'"),
                Uri.parse("bip32:/m/44'/501'/13'"),
                Uri.parse("bip32:/m/44'/501'/14'"),
            )
        ),
        SigningResponse(
            listOf(
                byteArrayOf( -118, 74, -26, 3, 48, -74, 125, 61, -128, -9, 49, 25, 17, -6, 93, 53, 48, -89, -93, 48, 53, 70, 85, -88, -81, 8, -100, 67, 49, 38, 17, 78, -96, 108, -62, -25, -109, -67, 72, -128, 12, 63, -100, -110, -57, 30, -104, 55, 20, 122, -37, 81, -44, 114, -54, 15, -26, 124, -90, 109, -51, 67, -37, 8),
                byteArrayOf(31, -97, -32, 4, 101, 17, 31, 107, -86, 48, 34, -16, 1, 30, -72, -4, -91, 87, -117, 39, -36, -60, 62, 18, 62, -45, -59, 19, 111, 123, -117, 87, -71, 12, -51, -15, -56, 7, 15, 123, 88, -118, 11, 24, -74, 53, 3, -93, 84, -88, 29, -24, 4, -60, -31, -44, 13, -118, -40, -2, 124, 77, 64, 13),
                byteArrayOf(-22, -31, -124, -60, -97, 28, 19, 112, 12, -77, 35, -44, -24, 80, 56, 97, 44, 80, 56, 21, -20, 4, 7, -63, -107, 100, -46, -10, 83, 43, -84, 64, 50, -107, 13, 2, -2, 67, 111, -40, -42, 105, -7, -38, 24, -101, -113, 84, 105, -52, 122, 82, 61, 82, -43, -32, -51, -94, 107, -37, 36, -108, -47, 0)
            ),
            listOf(
                Uri.parse("bip32:/m/44'/501'/15'"),
                Uri.parse("bip32:/m/44'/501'/16'"),
                Uri.parse("bip32:/m/44'/501'/17'"),
            )
        ),
        SigningResponse(
            listOf(
                byteArrayOf(44, 26, -117, -111, 23, 78, 46, 77, -128, -120, -44, -57, -120, 98, 92, 50, -64, 80, 114, -124, 29, -38, 25, -32, -10, -63, 1, -3, 3, 102, 74, -99, -4, 34, -61, 51, 75, 94, 95, -53, 14, -18, 33, -57, -124, 62, 4, 46, -83, 31, 69, -53, 44, 16, -39, -110, -92, 25, 3, -15, -60, 90, -34, 12),
                byteArrayOf(45, 30, 114, 54, 95, 92, 67, 12, -90, 69, -96, 30, -36, -61, -82, 120, -27, 57, -80, -71, 45, 65, -35, 29, 63, 16, -63, -62, -44, 1, -15, -9, -65, 109, -69, -4, 126, 27, 3, -118, 46, -75, -38, -97, 99, 12, 30, 60, 23, 48, -43, -92, -24, -100, 24, 81, -126, -67, 118, -67, -67, 22, -110, 15),
                byteArrayOf(86, -20, 85, 68, -33, 25, -1, -79, -111, 110, -37, 55, -9, 17, 20, 7, 49, -68, 90, 20, -10, -92, -21, -101, -8, -120, -44, -47, -111, 25, -4, -32, 14, 84, 109, 127, 93, -64, 17, 26, -8, -64, -112, 42, 120, -125, -80, 24, 62, -56, 84, 52, 96, 52, 5, -112, 1, 95, 8, 4, -88, 110, 111, 0)
            ),
            listOf(
                Uri.parse("bip32:/m/44'/501'/18'"),
                Uri.parse("bip32:/m/44'/501'/19'"),
                Uri.parse("bip32:/m/44'/501'/20'"),
            )
        ),
        SigningResponse(
            listOf(
                byteArrayOf(-74, -118, -25, -50, -8, 3, 105, 99, 115, -13, -4, -122, -121, 26, -116, 55, -58, 99, 16, 124, -23, 26, -90, 104, -109, 1, 10, 57, 42, 120, 15, -58, 72, 57, -105, 37, -17, -75, -17, -23, -34, -2, 55, 102, 118, -28, -103, -17, 17, -10, 97, -50, -45, 34, -114, 46, 41, -60, -107, -121, -52, 104, -97, 8),
                byteArrayOf(58, -26, 54, 126, 85, -77, 50, -112, -16, -118, 37, -111, -39, 101, -78, -68, 98, 23, 104, 104, -111, -106, 56, 95, -35, 27, -13, 30, -48, 71, 78, -56, -55, -86, 95, 2, -48, -68, 41, -16, 72, -110, 62, 87, 6, 50, -33, -5, -96, -42, -96, 70, 10, -65, -67, 115, 66, -90, 90, 88, 105, 20, 99, 13),
                byteArrayOf(-43, -6, 116, -63, 67, -117, -20, 67, -51, 7, -50, 83, 99, 117, 110, -90, 88, 119, 18, -8, 79, -8, -89, -77, -25, 65, 5, -53, -109, 42, 1, -57, 28, 46, 26, -115, 0, -76, 99, -75, 86, -31, -115, -91, 71, 97, 29, 123, -78, 46, -60, -32, -96, -115, -26, 39, 15, -4, 89, 45, 67, 52, -37, 14)
            ),
            listOf(
                Uri.parse("bip32:/m/44'/501'/21'"),
                Uri.parse("bip32:/m/44'/501'/22'"),
                Uri.parse("bip32:/m/44'/501'/23'"),
            )
        ),
        SigningResponse(
            listOf(
                byteArrayOf(24, 72, -69, -6, 6, 113, -68, -26, -15, 11, -70, -20, 103, -80, -96, -11, -122, -68, 21, 11, -102, 41, 108, 6, -98, -78, -44, -2, 97, 23, 75, -89, -116, 84, -101, 10, 15, 8, -32, 36, -56, 50, -21, -39, -128, -80, -100, -72, -118, 88, -14, -66, -71, 45, 124, 115, -34, 13, -89, 57, -49, -12, 105, 15),
                byteArrayOf(-5, -35, 60, 36, -113, -67, 101, -1, -1, -54, 103, -96, 70, 74, -83, -81, -80, 87, -82, -51, 5, 87, 42, 38, 51, 3, 78, 16, -64, -101, 97, -40, 54, 116, 58, 118, 117, 22, -107, -67, -96, -20, 62, -85, -122, -82, -120, -32, -104, -24, -36, 99, 104, -73, -12, -105, -6, -106, -72, 41, -11, 57, -89, 13),
                byteArrayOf(31, 32, -61, -94, 43, -92, 126, 64, -103, 25, 101, 77, -108, 113, -4, -36, -76, 59, -17, 65, -26, -114, 96, 26, 4, 3, 33, -104, -25, -98, 92, -103, -99, 31, 61, -104, -84, 117, 59, -116, -13, 65, -14, -128, 51, 90, -44, -54, -107, -71, 66, -14, -39, 61, -38, 124, 45, 70, 38, 50, -84, 99, -10, 1)
            ),
            listOf(
                Uri.parse("bip32:/m/44'/501'/24'"),
                Uri.parse("bip32:/m/44'/501'/25'"),
                Uri.parse("bip32:/m/44'/501'/26'"),
            )
        ),
        SigningResponse(
            listOf(
                byteArrayOf(-96, -68, -75, 26, -9, -7, 32, 55, -97, -87, -104, -29, -125, 35, 125, 81, -102, -106, -109, -21, -104, 84, 19, -34, -3, -55, 114, 34, 113, 69, -15, 125, 63, 10, -4, -69, -94, -18, 82, 99, 14, -51, 74, 33, -10, -11, -49, -82, -73, 8, -67, 1, 7, 59, -22, 79, 78, 28, 89, 13, -48, 33, 59, 5),
                byteArrayOf(-78, -85, 3, -66, 10, 125, 67, -114, 39, 42, -83, -5, -107, -18, -90, 60, -67, 66, 89, 19, 127, 106, 109, -79, 73, -32, -107, -101, -96, 51, 72, 126, -117, 106, 109, -110, 54, 10, 46, -68, -110, -90, -79, -11, 1, -4, -1, -33, -87, 21, -28, -103, -31, -28, -28, -100, -42, -89, 21, 41, -23, -61, 77, 2),
                byteArrayOf(-73, -68, -101, -10, -111, -70, 70, 33, 51, 89, -12, -35, -46, -90, -93, -76, -27, 69, -89, 8, 121, 110, -6, -32, -105, 41, -13, -104, 57, -1, 90, 80, -68, 81, -37, -22, 34, -71, -99, -16, -75, 76, -114, -80, -125, 119, 23, 65, 10, -43, -36, 110, 80, -77, -109, -70, 51, 9, 92, -18, -111, -28, 127, 11)
            ),
            listOf(
                Uri.parse("bip32:/m/44'/501'/27'"),
                Uri.parse("bip32:/m/44'/501'/28'"),
                Uri.parse("bip32:/m/44'/501'/29'"),
            )
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
        check(implementationDetails.MAX_SIGNING_REQUESTS <= 10) { "Test case implementation currently only handles up to 3 transactions per signing request" }
        check(implementationDetails.MAX_REQUESTED_SIGNATURES <= 3) { "Test case implementation currently only handles up to 3 signatures per transaction" }
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
                byteArrayOf(121, 3, 105, 2, 116, -68, 122, -109, -26, -66, 120, 9, -93, -91, 65, 12, -107, 86, -16, -109, -32, 84, 36, -52, 127, 57, -124, 97, 124, -76, 30, 81, -18, 52, 96, 14, -67, -20, 47, 69, -37, -68, -80, -50, -84, -114, 41, -92, -71, 3, 74, -36, 120, -54, -43, -77, -39, -80, 102, 67, -82, 16, -123, 14)
            ),
            listOf(
                Uri.parse("bip32:/m/44'/501'/0'"),
                Uri.parse("bip32:/m/44'/501'/1'"),
                Uri.parse("bip32:/m/44'/501'/2'"),
            )
        ),
        SigningResponse(
            listOf(
                byteArrayOf(0, -62, 74, 7, -114, -82, 95, -81, -107, 92, 21, -82, 84, 80, 74, 64, 35, 31, -83, 103, -127, -44, -27, 92, 83, -20, 122, -31, -127, 12, 2, 31, 73, -68, 75, 37, -62, 50, -46, 15, 12, 44, 38, -27, -127, 92, 31, -39, 95, -127, 94, -6, -69, 20, -44, 114, 121, 32, -99, -80, 6, -117, 105, 10),
                byteArrayOf(48, -71, 8, -85, -67, 99, 68, 38, 68, 6, 126, 35, 7, 28, -72, 122, 119, 19, 13, -38, 0, 120, -54, -109, -81, -77, -46, 7, 75, -2, 58, 82, -119, -71, 46, -109, 92, 61, 106, -46, 101, -42, -66, -73, -13, -34, -10, 37, 114, -27, -62, 85, 43, 92, -107, -18, -112, -97, -44, 24, 82, 48, 7, 9),
                byteArrayOf(97, -25, 36, 66, 55, 113, -16, -9, 100, -101, -109, 31, -63, 29, -33, -15, 61, -120, 36, 103, -103, 85, 112, 88, -60, -48, -128, -11, -116, 46, 54, -46, 49, -59, -75, 108, -101, -105, 4, 49, -77, -97, -89, 47, -52, 56, -127, 81, 25, -24, 56, 112, -75, 81, -33, -75, 89, -44, -18, 122, -108, 108, 5, 4)
            ),
            listOf(
                Uri.parse("bip32:/m/44'/501'/3'"),
                Uri.parse("bip32:/m/44'/501'/4'"),
                Uri.parse("bip32:/m/44'/501'/5'"),
            )
        ),
        SigningResponse(
            listOf(
                byteArrayOf(-80, 90, 86, -57, -60, 46, 6, -28, 75, 11, -35, -62, 56, 103, 106, -5, -117, -13, 60, 2, 61, 17, -103, 52, -45, 41, -101, -44, -5, 26, 70, 6, 41, 102, 68, 24, -121, 52, 18, 16, -17, -72, 26, 8, -102, -66, 16, 46, -79, -18, -75, 2, 85, 113, 19, 81, 43, -89, 84, 67, 30, -40, -128, 11),
                byteArrayOf(-101, 80, -35, 58, -57, 77, -96, -46, -38, 35, -97, -98, 33, 106, 31, 78, 43, 4, 53, 50, -120, -109, -95, 9, -61, 34, -52, 120, -94, -3, -79, 100, -118, 70, -42, 13, 27, 80, 47, 38, 48, 108, 21, -116, -100, -42, 64, -48, -118, 56, 17, -1, 92, 7, -116, 84, 121, -50, 122, -5, -91, -55, -35, 6),
                byteArrayOf(-14, 23, 100, -98, 109, 58, 10, 80, -37, 107, -111, 87, 86, -128, -94, 16, 59, 98, 115, -31, 124, -113, 34, -126, -7, 79, -76, 80, 55, 58, 124, -106, 110, 13, -71, -55, -115, 65, -16, -128, -116, -120, -6, -121, -49, -50, 49, -49, 61, 84, 83, -109, -39, -86, 15, 85, -78, -2, -66, 78, 78, -20, -67, 9)
            ),
            listOf(
                Uri.parse("bip32:/m/44'/501'/6'"),
                Uri.parse("bip32:/m/44'/501'/7'"),
                Uri.parse("bip32:/m/44'/501'/8'"),
            )
        ),
        SigningResponse(
            listOf(
                byteArrayOf(66, -27, -126, 65, -83, -29, -74, 76, -109, -128, -122, -95, 127, 111, 102, -124, -52, 59, 100, 59, 66, 58, 74, 65, -96, -22, 10, 51, 12, 93, -120, -98, -8, 113, -70, -29, -26, 49, -100, -117, 84, 32, -101, 96, 6, -30, 80, 43, -10, -14, -77, -113, -19, 53, -116, -92, 120, 75, 20, -70, -83, 123, 127, 12),
                byteArrayOf(55, -65, 49, 28, -33, 87, -72, -68, -57, 80, 38, -27, -113, 125, -20, 2, 13, 20, 76, 50, 103, -96, 104, -124, 18, -101, -108, 15, 1, 120, 83, -37, -40, -61, -82, -123, 24, -40, 122, -116, 38, 102, 98, -2, 37, 70, -52, -118, -74, 115, 113, -100, 17, 30, -118, -118, 47, -10, -27, 31, 8, -65, -64, 6),
                byteArrayOf(63, -90, -17, -87, 88, -70, 41, 44, 91, 4, 39, -94, 107, 118, 1, 12, 44, -70, -35, 15, 121, 79, -128, 19, -88, 115, 89, 8, 98, -77, 81, 97, -14, -36, 65, -56, 114, 42, 47, 25, -30, -115, -77, 3, -66, 68, -12, -104, -30, 53, 78, -98, -68, -85, 119, 94, -9, 4, -55, 70, -16, 32, 92, 14)
            ),
            listOf(
                Uri.parse("bip32:/m/44'/501'/9'"),
                Uri.parse("bip32:/m/44'/501'/10'"),
                Uri.parse("bip32:/m/44'/501'/11'"),
            )
        ),
        SigningResponse(
            listOf(
                byteArrayOf(-61, -59, 125, -71, 110, 106, 12, -47, -44, 27, -27, -94, 37, -88, 39, -53, -74, -27, -2, 88, -117, 5, -68, 42, 9, -23, 120, -23, -127, -128, 78, -38, 97, 43, -52, 45, 127, -30, 16, 67, -114, -109, -8, 104, 53, 120, 39, -115, -101, -99, -33, 12, -41, -63, -109, -69, -82, 22, -106, 39, -3, 22, -17, 10),
                byteArrayOf(-56, -12, -2, 124, 56, 76, 84, 33, 47, -75, 119, -48, -90, 54, 103, 24, 119, -24, 55, -44, 65, 49, -46, 57, -82, -95, -112, -51, 32, -26, 80, 86, -23, 16, 70, 125, 80, -27, 71, 31, 41, 72, -124, -45, -100, 49, -4, -85, -30, 78, -67, -21, 103, -70, -49, -87, -78, -81, 107, 121, -84, -45, 82, 4),
                byteArrayOf(103, -1, -111, -118, -119, -65, -108, 120, -56, -4, -83, -11, 120, 10, -79, 100, 9, -36, -107, -50, 29, 54, 65, 30, -47, -67, -21, -112, -8, -70, 24, -26, -81, 23, 64, 57, 90, 34, -95, 15, -88, -107, 123, -43, -3, 116, 76, -46, -78, -12, -15, 84, -7, 6, 55, 2, -125, -10, -74, -7, -95, 11, 78, 2)
            ),
            listOf(
                Uri.parse("bip32:/m/44'/501'/12'"),
                Uri.parse("bip32:/m/44'/501'/13'"),
                Uri.parse("bip32:/m/44'/501'/14'"),
            )
        ),
        SigningResponse(
            listOf(
                byteArrayOf( -118, 74, -26, 3, 48, -74, 125, 61, -128, -9, 49, 25, 17, -6, 93, 53, 48, -89, -93, 48, 53, 70, 85, -88, -81, 8, -100, 67, 49, 38, 17, 78, -96, 108, -62, -25, -109, -67, 72, -128, 12, 63, -100, -110, -57, 30, -104, 55, 20, 122, -37, 81, -44, 114, -54, 15, -26, 124, -90, 109, -51, 67, -37, 8),
                byteArrayOf(31, -97, -32, 4, 101, 17, 31, 107, -86, 48, 34, -16, 1, 30, -72, -4, -91, 87, -117, 39, -36, -60, 62, 18, 62, -45, -59, 19, 111, 123, -117, 87, -71, 12, -51, -15, -56, 7, 15, 123, 88, -118, 11, 24, -74, 53, 3, -93, 84, -88, 29, -24, 4, -60, -31, -44, 13, -118, -40, -2, 124, 77, 64, 13),
                byteArrayOf(-22, -31, -124, -60, -97, 28, 19, 112, 12, -77, 35, -44, -24, 80, 56, 97, 44, 80, 56, 21, -20, 4, 7, -63, -107, 100, -46, -10, 83, 43, -84, 64, 50, -107, 13, 2, -2, 67, 111, -40, -42, 105, -7, -38, 24, -101, -113, 84, 105, -52, 122, 82, 61, 82, -43, -32, -51, -94, 107, -37, 36, -108, -47, 0)
            ),
            listOf(
                Uri.parse("bip32:/m/44'/501'/15'"),
                Uri.parse("bip32:/m/44'/501'/16'"),
                Uri.parse("bip32:/m/44'/501'/17'"),
            )
        ),
        SigningResponse(
            listOf(
                byteArrayOf(44, 26, -117, -111, 23, 78, 46, 77, -128, -120, -44, -57, -120, 98, 92, 50, -64, 80, 114, -124, 29, -38, 25, -32, -10, -63, 1, -3, 3, 102, 74, -99, -4, 34, -61, 51, 75, 94, 95, -53, 14, -18, 33, -57, -124, 62, 4, 46, -83, 31, 69, -53, 44, 16, -39, -110, -92, 25, 3, -15, -60, 90, -34, 12),
                byteArrayOf(45, 30, 114, 54, 95, 92, 67, 12, -90, 69, -96, 30, -36, -61, -82, 120, -27, 57, -80, -71, 45, 65, -35, 29, 63, 16, -63, -62, -44, 1, -15, -9, -65, 109, -69, -4, 126, 27, 3, -118, 46, -75, -38, -97, 99, 12, 30, 60, 23, 48, -43, -92, -24, -100, 24, 81, -126, -67, 118, -67, -67, 22, -110, 15),
                byteArrayOf(86, -20, 85, 68, -33, 25, -1, -79, -111, 110, -37, 55, -9, 17, 20, 7, 49, -68, 90, 20, -10, -92, -21, -101, -8, -120, -44, -47, -111, 25, -4, -32, 14, 84, 109, 127, 93, -64, 17, 26, -8, -64, -112, 42, 120, -125, -80, 24, 62, -56, 84, 52, 96, 52, 5, -112, 1, 95, 8, 4, -88, 110, 111, 0)
            ),
            listOf(
                Uri.parse("bip32:/m/44'/501'/18'"),
                Uri.parse("bip32:/m/44'/501'/19'"),
                Uri.parse("bip32:/m/44'/501'/20'"),
            )
        ),
        SigningResponse(
            listOf(
                byteArrayOf(-74, -118, -25, -50, -8, 3, 105, 99, 115, -13, -4, -122, -121, 26, -116, 55, -58, 99, 16, 124, -23, 26, -90, 104, -109, 1, 10, 57, 42, 120, 15, -58, 72, 57, -105, 37, -17, -75, -17, -23, -34, -2, 55, 102, 118, -28, -103, -17, 17, -10, 97, -50, -45, 34, -114, 46, 41, -60, -107, -121, -52, 104, -97, 8),
                byteArrayOf(58, -26, 54, 126, 85, -77, 50, -112, -16, -118, 37, -111, -39, 101, -78, -68, 98, 23, 104, 104, -111, -106, 56, 95, -35, 27, -13, 30, -48, 71, 78, -56, -55, -86, 95, 2, -48, -68, 41, -16, 72, -110, 62, 87, 6, 50, -33, -5, -96, -42, -96, 70, 10, -65, -67, 115, 66, -90, 90, 88, 105, 20, 99, 13),
                byteArrayOf(-43, -6, 116, -63, 67, -117, -20, 67, -51, 7, -50, 83, 99, 117, 110, -90, 88, 119, 18, -8, 79, -8, -89, -77, -25, 65, 5, -53, -109, 42, 1, -57, 28, 46, 26, -115, 0, -76, 99, -75, 86, -31, -115, -91, 71, 97, 29, 123, -78, 46, -60, -32, -96, -115, -26, 39, 15, -4, 89, 45, 67, 52, -37, 14)
            ),
            listOf(
                Uri.parse("bip32:/m/44'/501'/21'"),
                Uri.parse("bip32:/m/44'/501'/22'"),
                Uri.parse("bip32:/m/44'/501'/23'"),
            )
        ),
        SigningResponse(
            listOf(
                byteArrayOf(24, 72, -69, -6, 6, 113, -68, -26, -15, 11, -70, -20, 103, -80, -96, -11, -122, -68, 21, 11, -102, 41, 108, 6, -98, -78, -44, -2, 97, 23, 75, -89, -116, 84, -101, 10, 15, 8, -32, 36, -56, 50, -21, -39, -128, -80, -100, -72, -118, 88, -14, -66, -71, 45, 124, 115, -34, 13, -89, 57, -49, -12, 105, 15),
                byteArrayOf(-5, -35, 60, 36, -113, -67, 101, -1, -1, -54, 103, -96, 70, 74, -83, -81, -80, 87, -82, -51, 5, 87, 42, 38, 51, 3, 78, 16, -64, -101, 97, -40, 54, 116, 58, 118, 117, 22, -107, -67, -96, -20, 62, -85, -122, -82, -120, -32, -104, -24, -36, 99, 104, -73, -12, -105, -6, -106, -72, 41, -11, 57, -89, 13),
                byteArrayOf(31, 32, -61, -94, 43, -92, 126, 64, -103, 25, 101, 77, -108, 113, -4, -36, -76, 59, -17, 65, -26, -114, 96, 26, 4, 3, 33, -104, -25, -98, 92, -103, -99, 31, 61, -104, -84, 117, 59, -116, -13, 65, -14, -128, 51, 90, -44, -54, -107, -71, 66, -14, -39, 61, -38, 124, 45, 70, 38, 50, -84, 99, -10, 1)
            ),
            listOf(
                Uri.parse("bip32:/m/44'/501'/24'"),
                Uri.parse("bip32:/m/44'/501'/25'"),
                Uri.parse("bip32:/m/44'/501'/26'"),
            )
        ),
        SigningResponse(
            listOf(
                byteArrayOf(-96, -68, -75, 26, -9, -7, 32, 55, -97, -87, -104, -29, -125, 35, 125, 81, -102, -106, -109, -21, -104, 84, 19, -34, -3, -55, 114, 34, 113, 69, -15, 125, 63, 10, -4, -69, -94, -18, 82, 99, 14, -51, 74, 33, -10, -11, -49, -82, -73, 8, -67, 1, 7, 59, -22, 79, 78, 28, 89, 13, -48, 33, 59, 5),
                byteArrayOf(-78, -85, 3, -66, 10, 125, 67, -114, 39, 42, -83, -5, -107, -18, -90, 60, -67, 66, 89, 19, 127, 106, 109, -79, 73, -32, -107, -101, -96, 51, 72, 126, -117, 106, 109, -110, 54, 10, 46, -68, -110, -90, -79, -11, 1, -4, -1, -33, -87, 21, -28, -103, -31, -28, -28, -100, -42, -89, 21, 41, -23, -61, 77, 2),
                byteArrayOf(-73, -68, -101, -10, -111, -70, 70, 33, 51, 89, -12, -35, -46, -90, -93, -76, -27, 69, -89, 8, 121, 110, -6, -32, -105, 41, -13, -104, 57, -1, 90, 80, -68, 81, -37, -22, 34, -71, -99, -16, -75, 76, -114, -80, -125, 119, 23, 65, 10, -43, -36, 110, 80, -77, -109, -70, 51, 9, 92, -18, -111, -28, 127, 11)
            ),
            listOf(
                Uri.parse("bip32:/m/44'/501'/27'"),
                Uri.parse("bip32:/m/44'/501'/28'"),
                Uri.parse("bip32:/m/44'/501'/29'"),
            )
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
        check(implementationDetails.MAX_SIGNING_REQUESTS <= 10) { "Test case implementation currently only handles up to 3 transactions per signing request" }
        check(implementationDetails.MAX_REQUESTED_SIGNATURES <= 3) { "Test case implementation currently only handles up to 3 signatures per transaction" }
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
