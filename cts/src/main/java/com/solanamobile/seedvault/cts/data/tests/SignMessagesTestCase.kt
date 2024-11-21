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
import com.solanamobile.seedvault.cts.data.tests.helper.ActionFailedException
import com.solanamobile.seedvault.cts.data.tests.helper.EmptyResponseException
import com.solanamobile.seedvault.cts.data.tests.helper.NoResultException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred

internal abstract class SignNMessagesMSignaturesTestCase(
    preConditions: List<ConditionChecker>,
    private val authorizedSeedsChecker: AuthorizedSeedsChecker,
    private val logger: TestSessionLogger,
    private val expectedException: Exception? = null,
    private val signingRequests: () -> ArrayList<SigningRequest>,
    private val expectedSignatures: ArrayList<SigningResponse>? = null
) : TestCaseImpl(
    preConditions = preConditions
), ActivityLauncherTestCase {
    private lateinit var launcher: ActivityResultLauncher<SignMessagesInput>
    private var completionSignal: CompletableDeferred<ArrayList<SigningResponse>>? = null

    data class SignMessagesInput(
        @AuthToken val authToken: Long,
        val requests: ArrayList<SigningRequest>
    )

    class SignMessageIntentContract :
        ActivityResultContract<SignMessagesInput, Result<ArrayList<SigningResponse>>>() {

        override fun createIntent(context: Context, input: SignMessagesInput): Intent =
            Wallet.signMessages(input.authToken, input.requests)

        override fun parseResult(
            resultCode: Int,
            intent: Intent?
        ): Result<ArrayList<SigningResponse>> {
            return try {
                val result = onSignMessagesResult(resultCode, intent)
                Log.d(TAG, "Message signed: signatures=$result")
                Result.success(result)
            } catch (e: ActionFailedException) {
                Log.e(TAG, "Message signing failed", e)
                Result.failure(e)
            } catch (e: EmptyResponseException) {
                Log.e(TAG, "Message signing failed", e)
                Result.failure(e)
            } catch (e: NoResultException) {
                Log.e(TAG, "Message signing failed", e)
                Result.failure(e)
            }
        }

        @Throws(ActionFailedException::class)
        fun onSignMessagesResult(
            resultCode: Int,
            result: Intent?
        ): ArrayList<SigningResponse> {
            if (resultCode != Activity.RESULT_OK) {
                throw ActionFailedException("Sign Message", resultCode)
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
            const val TAG = "SignMessageIntentContract"
        }
    }

    override fun registerActivityLauncher(arc: ActivityResultCaller) {
        launcher =
            arc.registerForActivityResult(SignMessageIntentContract()) { signingResponse ->
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
            logger.warn("$id: Failed locating seed for `signMessages`")
            return TestResult.FAIL
        }

        val signal = CompletableDeferred<ArrayList<SigningResponse>>()
        assert(completionSignal == null) { "Completion signal non-null" }
        completionSignal = signal

        val requests = signingRequests()
        launcher.launch(SignMessagesInput(authToken, requests))

        try {
            signal.await()
        } catch (e: Exception) {
            Log.e("SignMessageTestCase", "Message failed", e)
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
        protected fun signMMessagesWithNSignatures(
            context: Context,
            messages: Int,
            signaturesPerMessage: Int,
            useBip44DerivationPaths: Boolean
        ): ArrayList<SigningRequest> {
            val implementationLimits = Wallet.getImplementationLimitsForPurpose(
                context,
                WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION
            )
            val maxRequestedSignatures =
                implementationLimits[WalletContractV1.IMPLEMENTATION_LIMITS_MAX_REQUESTED_SIGNATURES]!!.toInt()

            val signingRequests = (0 until messages).map { i ->
                val derivationPaths = (0 until signaturesPerMessage).map { j ->
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
                SigningRequest(createFakeMessage(i), derivationPaths)
            }

            return ArrayList(signingRequests)
        }

        @JvmStatic
        protected fun createFakeMessage(i: Int): ByteArray {
            return ByteArray(MESSAGE_SIZE) { i.toByte() }
        }

        protected const val MESSAGE_SIZE = 512
    }
}

internal class Sign1MessageWith1SignatureTestCase @Inject constructor(
    @ApplicationContext context: Context,
    logger: TestSessionLogger,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
) : SignNMessagesMSignaturesTestCase(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker),
    authorizedSeedsChecker = knownSeed12AuthorizedChecker,
    logger = logger,
    signingRequests = {
        signMMessagesWithNSignatures(context, 1, 1, false)
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
    override val id: String = "s1m1s"
    override val description: String = "Sign 1 message with 1 signature"
    override val instructions: String = "Approve message when prompted."
}

internal class SignMaxMessageWithMaxSignatureTestCase @Inject constructor(
    @ApplicationContext private val context: Context,
    logger: TestSessionLogger,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
) : SignNMessagesMSignaturesTestCase(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker),
    authorizedSeedsChecker = knownSeed12AuthorizedChecker,
    logger = logger,
    signingRequests = {
        val limits = getLimits(context)
        signMMessagesWithNSignatures(context, limits.first, limits.second, false)
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
        )
    )
), ActivityLauncherTestCase {
    override val id: String = "smaxmmaxs"
    override val description: String
        get() {
            val limits = getLimits(context)
            return "Sign ${limits.first} message with ${limits.second} signature"
        }
    override val instructions: String = "Approve message when prompted."
}

internal class SignMaxMessageWithMaxSignatureBip44TestCase @Inject constructor(
    @ApplicationContext private val context: Context,
    logger: TestSessionLogger,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
) : SignNMessagesMSignaturesTestCase(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker),
    authorizedSeedsChecker = knownSeed12AuthorizedChecker,
    logger = logger,
    signingRequests = {
        val limits = getLimits(context)
        signMMessagesWithNSignatures(context, limits.first, limits.second, true)
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
        )
    )
), ActivityLauncherTestCase {
    override val id: String = "smaxmmaxsb44"
    override val description: String
        get() {
            val limits = getLimits(context)
            return "Sign ${limits.first} message with ${limits.second} signature using BIP-44 derivation paths"
        }
    override val instructions: String = "Approve message when prompted."
}

internal class SignMessageRequestsExceedLimitTestCase @Inject constructor(
    @ApplicationContext private val context: Context,
    logger: TestSessionLogger,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
) : SignNMessagesMSignaturesTestCase(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker),
    authorizedSeedsChecker = knownSeed12AuthorizedChecker,
    logger = logger,
    signingRequests = {
        val limits = getLimits(context)
        signMMessagesWithNSignatures(context, limits.first + 1, 1, false)
    },
    expectedException = ActionFailedException(
        "Sign Message",
        WalletContractV1.RESULT_IMPLEMENTATION_LIMIT_EXCEEDED
    ),
), ActivityLauncherTestCase {
    override val id: String = "smrel"
    override val description: String
        get() {
            val limits = getLimits(context)
            return "Sign ${limits.first + 1} messages"
        }
    override val instructions: String = ""
}

internal class SignMessageSignaturesExceedLimitTestCase @Inject constructor(
    @ApplicationContext private val context: Context,
    logger: TestSessionLogger,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
) : SignNMessagesMSignaturesTestCase(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker),
    authorizedSeedsChecker = knownSeed12AuthorizedChecker,
    logger = logger,
    signingRequests = {
        val limits = getLimits(context)
        signMMessagesWithNSignatures(context, 1, limits.second + 1, false)
    },
    expectedException = ActionFailedException(
        "Sign Message",
        WalletContractV1.RESULT_IMPLEMENTATION_LIMIT_EXCEEDED
    ),
), ActivityLauncherTestCase {
    override val id: String = "smsel"
    override val description: String
        get() {
            val limits = getLimits(context)
            return "Sign request with ${limits.second + 1} signatures"
        }
    override val instructions: String = ""
}

internal class DenySignMessageTestCase @Inject constructor(
    @ApplicationContext context: Context,
    logger: TestSessionLogger,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
) : SignNMessagesMSignaturesTestCase(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker),
    authorizedSeedsChecker = knownSeed12AuthorizedChecker,
    logger = logger,
    signingRequests = {
        signMMessagesWithNSignatures(context, 1, 1, false)
    },
    expectedException = ActionFailedException("Sign Message", Activity.RESULT_CANCELED),
), ActivityLauncherTestCase {
    override val id: String = "dsm"
    override val description: String = "Signature denial flow"
    override val instructions: String = "Deny message when prompted."
}

internal class IncorrectPinSignMessageFailureTestCase @Inject constructor(
    @ApplicationContext context: Context,
    logger: TestSessionLogger,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
) : SignNMessagesMSignaturesTestCase(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker),
    authorizedSeedsChecker = knownSeed12AuthorizedChecker,
    logger = logger,
    signingRequests = {
        signMMessagesWithNSignatures(context, 1, 1, false)
    },
    expectedException = ActionFailedException(
        "Sign Message",
        WalletContractV1.RESULT_AUTHENTICATION_FAILED
    ),
), ActivityLauncherTestCase {
    override val id: String = "ipsmf"
    override val description: String = "Incorrect pin on message approval flow"
    override val instructions: String = "Enter incorrect pin 5 times."
}
