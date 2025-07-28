/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.tests

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import com.solanamobile.seedvault.Bip32DerivationPath
import com.solanamobile.seedvault.BipLevel
import com.solanamobile.seedvault.PermissionedAccount
import com.solanamobile.seedvault.PublicKeyResponse
import com.solanamobile.seedvault.Wallet
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvault.WalletContractV1.AuthToken
import com.solanamobile.seedvault.cts.PrivilegedSeedVaultChecker
import com.solanamobile.seedvault.cts.data.ActivityLauncherTestCase
import com.solanamobile.seedvault.cts.data.TestCaseImpl
import com.solanamobile.seedvault.cts.data.TestResult
import com.solanamobile.seedvault.cts.data.TestSessionLogger
import com.solanamobile.seedvault.cts.data.conditioncheckers.HasSeedVaultPermissionChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.KnownSeed12AuthorizedChecker
import com.solanamobile.seedvault.cts.data.testdata.ImplementationDetails
import com.solanamobile.seedvault.cts.data.testdata.KnownSeed
import com.solanamobile.seedvault.cts.data.testdata.KnownSeed12
import com.solanamobile.seedvault.cts.data.tests.helper.ActionFailedException
import com.solanamobile.seedvault.cts.data.tests.helper.EmptyResponseException
import com.solanamobile.seedvault.cts.data.tests.helper.NoResultException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred

internal abstract class PubKeyDerivationTestCase(
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    @ApplicationContext private val context: Context,
    private val knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    private val knownSeed12: KnownSeed,
    private val keysToFetch: Int = 1,
    private val startIndex: Int = 1000,
    private val logger: TestSessionLogger,
    private val fetchPermissionedPubkey: Boolean = false,
    private val expectedException: Exception? = null,
    private val expectedPubKeys: List<PublicKeyResponse>? = null
) : TestCaseImpl(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker)
), ActivityLauncherTestCase {

    data class FetchPubKeyInput(
        @AuthToken val authToken: Long,
        val derivationPaths: ArrayList<Uri>
    )

    class FetchPubKeyIntentContract :
        ActivityResultContract<FetchPubKeyInput, Result<ArrayList<PublicKeyResponse>>>() {

        override fun createIntent(context: Context, input: FetchPubKeyInput): Intent =
            Wallet.requestPublicKeys(input.authToken, input.derivationPaths)

        override fun parseResult(
            resultCode: Int,
            intent: Intent?
        ): Result<ArrayList<PublicKeyResponse>> {
            return try {
                val result = onRequestPublicKeysResult(resultCode, intent)
                Log.d(TAG, "PubKey retrieved: $result")
                Result.success(result)
            } catch (e: ActionFailedException) {
                Log.e(TAG, "PubKey retrieved failed", e)
                Result.failure(e)
            }
        }

        @Throws(ActionFailedException::class)
        fun onRequestPublicKeysResult(
            resultCode: Int,
            result: Intent?
        ): ArrayList<PublicKeyResponse> {
            if (resultCode != Activity.RESULT_OK) {
                throw ActionFailedException("PubKey fetch", resultCode)
            } else if (result == null) {
                throw NoResultException()
            }

            @Suppress("DEPRECATION")
            val publicKeys = result.getParcelableArrayListExtra<PublicKeyResponse>(
                WalletContractV1.EXTRA_PUBLIC_KEY
            )
            if (publicKeys.isNullOrEmpty()) {
                throw EmptyResponseException()
            }

            return publicKeys
        }

        companion object {
            const val TAG = "FetchPubKeyIntentContract"
        }
    }

    private lateinit var launcher: ActivityResultLauncher<FetchPubKeyInput>
    private var completionSignal: CompletableDeferred<ArrayList<PublicKeyResponse>>? = null

    override suspend fun doExecute(): TestResult {
        @AuthToken val authToken = knownSeed12AuthorizedChecker.findMatchingSeed()
        if (authToken == null) {
            logger.warn("$id: Failed locating seed `${knownSeed12.SEED_NAME}` to `signMessage`")
            return TestResult.FAIL
        }

        if (expectedPubKeys != null) {
            context.contentResolver.query(
                WalletContractV1.ACCOUNTS_CONTENT_URI,
                WalletContractV1.ACCOUNTS_ALL_COLUMNS,
                Bundle().apply { putLong(WalletContractV1.EXTRA_AUTH_TOKEN, authToken) },
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val derivationPath = Uri.parse(c.getString(1))
                    val pubKeyEncoded = c.getString(3)
                    expectedPubKeys.forEach {
                        if (it.publicKeyEncoded == pubKeyEncoded || it.resolvedDerivationPath == derivationPath) {
                            logger.warn("Pubkey $pubKeyEncoded, derivation path $derivationPath already exists in accounts table")
                            return TestResult.FAIL
                        }
                    }
                }
            }
        }

        val signal = CompletableDeferred<ArrayList<PublicKeyResponse>>()
        assert(completionSignal == null) { "Completion signal non-null" }
        completionSignal = signal

        val derivationPaths = nPublicKeysDerivationPaths(keysToFetch)
        launcher.launch(FetchPubKeyInput(authToken, derivationPaths))

        try {
            val response = signal.await()
            return if (expectedPubKeys != null) {
                if (expectedPubKeys != response) {
                    logger.warn("Pubkey mismatch\nExpected ${expectedPubKeys}\nReceived $response")
                    TestResult.FAIL
                } else {
                    TestResult.PASS
                }
            } else if (expectedException != null) {
                TestResult.FAIL
            } else {
                TestResult.PASS
            }
        } catch (e: Exception) {
            if (expectedException == null) {
                logger.warn("Fetching pubkey failed", e)
                return TestResult.FAIL
            }
            if (e is ActionFailedException && expectedException is ActionFailedException) {
                return if (expectedException.errorCode == e.errorCode) {
                    TestResult.PASS
                } else {
                    logger.warn("Fetching pubkey failed", e)
                    TestResult.FAIL
                }
            }
            if (e is EmptyResponseException && expectedException is EmptyResponseException) {
                return TestResult.PASS
            }
            if (e is NoResultException && expectedException is NoResultException) {
                return TestResult.PASS
            }

            logger.warn("Fetching pubkey failed", e)
            return TestResult.FAIL
        }
    }

    override fun registerActivityLauncher(arc: ActivityResultCaller) {
        launcher =
            arc.registerForActivityResult(FetchPubKeyIntentContract()) { pubKeyResponse ->
                completionSignal?.run {
                    completionSignal = null
                    pubKeyResponse.fold(
                        onSuccess = { complete(it) },
                        onFailure = { completeExceptionally(it) }
                    )
                }
            }
    }

    private fun nPublicKeysDerivationPaths(n: Int): ArrayList<Uri> {
        return if (fetchPermissionedPubkey) {
            ArrayList(
                (0 until n).map { i ->
                    PermissionedAccount.getPermissionedAccountDerivationPath(startIndex + i).toUri()
                }
            )
        } else {
            ArrayList(
                (0 until n).map { i ->
                    Bip32DerivationPath.newBuilder()
                        .appendLevel(BipLevel(startIndex + i, true))
                        .build().toUri()
                }
            )
        }
    }
}

internal class Fetch1PubKeyTestCase @Inject constructor(
    @ApplicationContext context: Context,
    logger: TestSessionLogger,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    @KnownSeed12 knownSeed12: KnownSeed,
) : PubKeyDerivationTestCase(
    context = context,
    hasSeedVaultPermissionChecker = hasSeedVaultPermissionChecker,
    knownSeed12AuthorizedChecker = knownSeed12AuthorizedChecker,
    knownSeed12 = knownSeed12,
    keysToFetch = 1,
    expectedPubKeys = arrayListOf(
        PublicKeyResponse(
            byteArrayOf(89, 2, -27, 112, -42, -35, -16, -23, 24, -88, 11, -67, -37, -91, -77, -17, -28, -96, 74, -14, 36, -109, 106, 6, -43, -125, -48, -4, -98, -122, 121, 0),
            "6zTr6qLDtLj1N2p52KsxJVsAJtM6ZcMPht2qxi5znj6X",
            Uri.parse("bip32:/m/1000'")
        )
    ),
    logger = logger
) {
    override val id: String = "f1pk"
    override val description: String = "Fetch 1 public key."
    override val instructions: String = ""
}

internal class FetchMaxPubKeyTestCase @Inject constructor(
    @ApplicationContext context: Context,
    logger: TestSessionLogger,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    @KnownSeed12 knownSeed12: KnownSeed,
    implementationDetails: ImplementationDetails,
) : PubKeyDerivationTestCase(
    context = context,
    hasSeedVaultPermissionChecker = hasSeedVaultPermissionChecker,
    knownSeed12AuthorizedChecker = knownSeed12AuthorizedChecker,
    knownSeed12 = knownSeed12,
    startIndex = 1001,
    keysToFetch = implementationDetails.MAX_REQUESTED_PUBLIC_KEYS,
    expectedPubKeys = arrayListOf(
        PublicKeyResponse(
            byteArrayOf(119, 77, -30, -98, 86, -100, 84, -84, -78, 56, -72, 28, -15, -38, -62, 123, 33, 126, 98, -50, 56, 15, -53, 110, -26, 102, -94, -112, 58, -17, -119, 30),
            "92iQ6HssBuvdxnhycHiWR7qLudCRMWYbHmcFkF2TRDZo",
            Uri.parse("bip32:/m/1001'")
        ),
        PublicKeyResponse(
            byteArrayOf(92, 24, -37, -75, -60, -7, -25, 13, -120, -90, -53, -116, -8, -31, -113, -117, 80, 3, 29, 126, -67, 75, -125, 97, -56, -119, 45, -40, -37, 125, -100, -86),
            "7CWVbHNphfcoMxpJR6QbZWrkmiBZjSdakok25buhxMpV",
            Uri.parse("bip32:/m/1002'")
        ),
        PublicKeyResponse(
            byteArrayOf(23, -25, 81, -48, 16, 96, 114, -65, -125, 1, 38, 99, -106, -28, -112, 22, -106, 122, -103, -23, 39, -36, -96, 66, 90, 125, -61, -95, -76, -63, -90, 23),
            "2cJxXDea3H4nvtyYZLi1P9x5ar3zcSqnQCVtvHLuJCZ4",
            Uri.parse("bip32:/m/1003'")
        ),
        PublicKeyResponse(
            byteArrayOf(79, 73, 8, 80, -25, 87, 70, -116, 117, -114, 95, -15, 78, 98, -93, -120, -50, 104, 27, -64, -49, -102, 59, 127, -25, -23, 29, -111, 37, 37, -48, -124),
            "6LVoJtqkvQJ5eZ9mF1x36emgMQ3zCKfYCZffCLb5eFgo",
            Uri.parse("bip32:/m/1004'")
        ),
        PublicKeyResponse(
            byteArrayOf(75, -2, 70, -82, -108, 68, -31, 118, 66, -35, -88, 115, -55, 88, 118, -3, -47, -23, -8, -110, 120, 70, 41, 114, 27, -73, 74, 9, -54, 65, -33, 124),
            "67eThoaCoHx8VSK3yrPBvZ5gQF93RRRWiS7QsXda1uEj",
            Uri.parse("bip32:/m/1005'")
        ),
        PublicKeyResponse(
            byteArrayOf(-53, 124, -32, 11, 33, 7, -35, -81, 102, -31, -93, -5, -113, 124, 7, -102, 124, -125, -111, 44, -31, 44, -69, 58, 71, -65, 44, -28, -119, -15, -105, 27),
            "EhLCmV5eoihRyqiCFr84aQKRTSvn9ZZdP9tAjqCdbc8i",
            Uri.parse("bip32:/m/1006'")
        ),
        PublicKeyResponse(
            byteArrayOf(-101, 107, -2, 5, -92, -126, -76, 82, -39, -26, -31, -57, 1, -88, 46, 10, 21, -70, -2, -120, 127, -49, -94, 109, -60, 83, 89, 75, -87, 104, 60, 110),
            "BThhikjsdTy1jFgszN4r7qtQVeCtJEQPGs9VTQPL6xUV",
            Uri.parse("bip32:/m/1007'")
        ),
        PublicKeyResponse(
            byteArrayOf(-12, 0, -2, 9, -10, -50, -79, 111, -70, 66, 91, 50, -96, 43, 84, 120, -45, 20, -21, 84, 92, 50, 6, 85, 64, 54, 116, -104, -99, 65, 76, -120),
            "HRVMeCDKw1mBeR3bpXceqxHaUmqVfbX1xEJPyCdqWyuR",
            Uri.parse("bip32:/m/1008'")
        ),
        PublicKeyResponse(
            byteArrayOf(9, 65, 108, -17, -70, -63, -82, -42, 25, 61, 1, -124, -5, -16, 41, -19, 46, 40, -23, -83, 109, 40, 30, -65, 58, -118, 97, 36, -27, 84, 53, -34),
            "d8XrVPiz2dwtQcFqks6PQfqVf819XEZnQkrDSF1tgG5",
            Uri.parse("bip32:/m/1009'")
        ),
        PublicKeyResponse(
            byteArrayOf(-98, -11, 120, 105, 102, -22, -89, -46, 31, -60, -59, 42, -30, -95, 92, -65, 110, 109, 37, 4, 95, 41, -86, 123, -26, -26, 60, -49, -9, -39, 24, -54),
            "BhWWfc1yf5EgdZM6PBddC1BE1kxxtcdijHsMKongAiGh",
            Uri.parse("bip32:/m/1010'")
        ),
        PublicKeyResponse(
            byteArrayOf(-60, 16, 63, -83, 70, 121, -114, -64, 97, -105, -124, -37, 52, -56, 14, -126, -71, 44, -44, -123, 121, 3, -123, 6, 71, -21, -34, 85, -59, 88, 63, -104),
            "ECMHCKagCLGbiqeZV4n8nM96aK3pqPH1SZDSpMyajWns",
            Uri.parse("bip32:/m/1011'")
        ),
        PublicKeyResponse(
            byteArrayOf(-18, -47, -30, -12, -18, -44, 125, -81, -105, -83, 68, -31, 31, 36, 77, 97, -47, -97, -69, -128, 28, -95, -40, 100, 124, -17, 54, -119, -126, 120, 65, -71),
            "H5Ff6Tm1fDsAJ4tvAayhgvKG2TdQjResbfRpDCdPoTQC",
            Uri.parse("bip32:/m/1012'")
        ),
        PublicKeyResponse(
            byteArrayOf(20, -92, 28, 95, 28, -76, 103, 33, -105, 90, -40, 39, 85, 91, 97, -97, -83, 55, -7, 37, 8, -106, -98, -107, 23, -21, -102, 88, 107, -68, -27, 117),
            "2PaJ5NMPbuQn2gD3vkGyUEgpv54gzZASwCvkKXCPGiGt",
            Uri.parse("bip32:/m/1013'")
        ),
        PublicKeyResponse(
            byteArrayOf(50, -18, 69, 95, -26, 34, 92, 98, 18, 115, 117, -110, -87, -36, -28, -81, -88, -81, -82, -95, -38, -42, 97, 84, 10, 31, -33, 69, -101, 8, 24, 97),
            "4Rp7Z1YLiv58GpXzdazSz3mtHP2ay54Ki58F6PpBowon",
            Uri.parse("bip32:/m/1014'")
        ),
        PublicKeyResponse(
            byteArrayOf(-36, 55, 67, -96, -58, -105, 92, -5, -43, 54, -39, 102, -127, 90, 105, 96, 17, 20, 72, -74, -45, 102, -89, -78, -15, 34, 52, -67, -25, 11, -8, -91),
            "Fpda2XwKg5GDebRccLJz9J4tdwc2cnDFUrxXuW6kYwEt",
            Uri.parse("bip32:/m/1015'")
        ),
        PublicKeyResponse(
            byteArrayOf(-84, 3, -120, 66, -114, -30, 119, -125, 53, 101, -18, -93, 76, -108, 38, -91, 127, -106, -107, -60, 111, 10, -104, 106, 69, 103, 47, 81, -89, 106, -64, 117),
            "CaUFPAdw4GisnQWbkcvYMmVFjwaCaCw3vN9yH9G6TN4L",
            Uri.parse("bip32:/m/1016'")
        ),
        PublicKeyResponse(
            byteArrayOf(-79, 68, -6, -36, -4, -37, -2, 102, 49, -63, 37, -112, -11, 37, -76, 127, -58, -5, 126, 43, -96, 56, 119, -13, -70, -49, 74, 25, -68, 98, -44, -19),
            "CvzAnXrnHYHQEzYoFfFVvKwk5pzFAfWHDrVvtBpvvcqi",
            Uri.parse("bip32:/m/1017'")
        ),
        PublicKeyResponse(
            byteArrayOf(-119, -40, 59, 125, -37, -34, 19, 62, 70, -105, -38, -73, -42, 8, 107, 36, -119, -125, 114, 49, -96, -128, 74, 24, -119, -126, -60, -79, -103, -52, -108, -10),
            "AH66KC5kRDmZK59LaNyvYfWw3iebhyHPXWjz2hNwEccM",
            Uri.parse("bip32:/m/1018'")
        ),
        PublicKeyResponse(
            byteArrayOf(-106, -109, -78, -90, -40, -54, -49, -96, 0, 27, -42, -101, 104, 44, 37, -13, 115, -49, -103, -9, 56, -96, 101, 93, -84, -4, 8, -5, 33, -102, 21, 47),
            "B8nnCu5G5wJtZGMvnk7mV6Fz7aNrfzPCafF3o767g5Ga",
            Uri.parse("bip32:/m/1019'")
        ),
        PublicKeyResponse(
            byteArrayOf(60, 104, 22, -11, -80, 79, 117, -92, 31, -91, -22, 117, -108, -81, -110, -86, -86, 21, -6, 60, -38, -64, 101, 36, -100, -90, -72, -33, -1, 74, 83, -54),
            "54oX7y1GBTJaNf1W6nASEX6N2hRkwWb27PcZLKSuKCob",
            Uri.parse("bip32:/m/1020'")
        ),
        PublicKeyResponse(
            byteArrayOf(-22, 68, -124, -111, 55, 88, 20, 6, -11, 42, 80, 22, -119, -66, -4, -7, -79, 10, -51, -6, -79, 125, -44, -54, 8, -29, -11, 54, 32, 61, -22, 98),
            "GmUzvyEhLyGaQUesvTCxMeaTuRuDxSJVrfeEcJPWt2c9",
            Uri.parse("bip32:/m/1021'")
        ),
        PublicKeyResponse(
            byteArrayOf(54, 39, -77, 72, -40, 127, 97, 122, 24, -116, 104, 21, 47, 42, -85, 17, -35, 91, 118, 51, -12, -52, -117, 38, -88, 116, -92, -47, 108, -77, -61, 75),
            "4eQ8MjSTo4j3PpMi9LMiGuV6NJBsUuHNTrvzt2WVyBiz",
            Uri.parse("bip32:/m/1022'")
        ),
        PublicKeyResponse(
            byteArrayOf(-71, 10, -116, 83, -12, 31, -90, 104, 105, -85, 109, 64, -106, -20, 126, 118, -72, 37, -89, -54, 57, 101, -81, -105, -66, -19, 106, -48, 10, -85, -84, -56),
            "DTKkfrPKhqHogWM6wjHeJCPSRzUd1z4hapXFom8CBwVy",
            Uri.parse("bip32:/m/1023'")
        ),
        PublicKeyResponse(
            byteArrayOf(-77, -37, -103, 13, -74, 123, -65, -92, 117, 53, 8, -48, -12, -82, -74, 117, 71, 47, 38, 25, 111, -87, 106, -121, -12, 55, 58, 48, -108, -55, -27, -37),
            "D76C6kZBtZb1qJPAZ38y8mbziWC6ijTEbGcuxN5N6HQE",
            Uri.parse("bip32:/m/1024'")
        ),
        PublicKeyResponse(
            byteArrayOf(106, 36, 23, 111, 55, 37, -109, -59, -108, 116, 113, -24, -33, -81, -111, 63, -29, 68, -84, -104, -120, 96, 29, -32, 77, -16, 124, -61, 46, 83, -8, 86),
            "89L8rqWvLa97inrwbcsqjxaEnofTJDbWYGJjt81ZrJmK",
            Uri.parse("bip32:/m/1025'")
        ),
        PublicKeyResponse(
            byteArrayOf(41, 102, -119, -7, -88, 33, -9, 33, -46, -3, -26, 34, 3, -28, -96, 1, 89, -15, -54, -17, 45, 80, -35, 21, 15, 42, -85, -27, 49, 97, 84, 2),
            "3ncQHwte6maiLf1KDYY8SqQMPB25jQAXsaKAhLBhvVrq",
            Uri.parse("bip32:/m/1026'")
        ),
        PublicKeyResponse(
            byteArrayOf(-38, -127, 51, -27, 68, 61, -123, 61, 61, 73, -20, -58, 19, -58, 41, -31, 86, -91, -117, -93, -125, -91, -93, 19, 67, 83, -67, 66, -77, -115, -72, -34),
            "Fhx9Sy3gkxFT5aa2aYLL7ScaggazgmC47h5ftxXuUGKw",
            Uri.parse("bip32:/m/1027'")
        ),
        PublicKeyResponse(
            byteArrayOf(-64, 17, 2, -81, 82, -91, 112, 68, 7, 96, 23, -36, -18, -27, -39, -80, 60, -28, 20, 16, 85, -53, 59, 66, -22, -98, -59, -62, 43, -71, 80, 98),
            "DvkKgemQ1q7dm12ZeMTs22a6UWCznckssWN4NmmraYkH",
            Uri.parse("bip32:/m/1028'")
        ),
        PublicKeyResponse(
            byteArrayOf(82, -123, -108, 53, 49, 62, -24, 55, 40, 29, -29, 20, -116, -61, 73, 61, 91, -104, 71, 11, -3, 73, 44, 104, 65, 19, 34, -47, -84, -104, 50, -62),
            "6Z8a1UFFnXBEh13aqt9rZt7r1zn5pmvA8TWf4mHRhmwP",
            Uri.parse("bip32:/m/1029'")
        ),
        PublicKeyResponse(
            byteArrayOf(-64, -40, 91, -113, 121, 19, 74, -34, 103, -68, 41, 26, -55, -107, -26, 82, 106, 0, 73, -95, 106, 40, -116, 72, 121, -64, -35, 30, 8, -115, 71, -78),
            "DyndJ8894KAwJDZTfoSCnajqPiSfBU2fzJr896nsiYQy",
            Uri.parse("bip32:/m/1030'")
        ),
    ).take(implementationDetails.MAX_REQUESTED_PUBLIC_KEYS),
    logger = logger
) {
    override val id: String = "fmaxpk"
    override val description: String = "Fetch ${implementationDetails.MAX_REQUESTED_PUBLIC_KEYS} public keys."
    override val instructions: String = ""

    init {
        check(implementationDetails.MAX_REQUESTED_PUBLIC_KEYS <= 30) { "Test case implementation currently only handles up to 30 requested pubkeys" }
    }
}

internal class FetchTooManyPubKeyTestCase @Inject constructor(
    @ApplicationContext context: Context,
    logger: TestSessionLogger,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    @KnownSeed12 knownSeed12: KnownSeed,
    implementationDetails: ImplementationDetails,
) : PubKeyDerivationTestCase(
    context = context,
    hasSeedVaultPermissionChecker = hasSeedVaultPermissionChecker,
    knownSeed12AuthorizedChecker = knownSeed12AuthorizedChecker,
    knownSeed12 = knownSeed12,
    keysToFetch = implementationDetails.MAX_REQUESTED_PUBLIC_KEYS + 1,
    expectedException = ActionFailedException("PubKey fetch", WalletContractV1.RESULT_IMPLEMENTATION_LIMIT_EXCEEDED),
    logger = logger
) {
    override val id: String = "ftmpk"
    override val description: String = "Fetch too many (${implementationDetails.MAX_REQUESTED_PUBLIC_KEYS + 1}) public keys."
    override val instructions: String = ""
}

internal class PermissionedAccountFetchPubKeysTestCase @Inject constructor(
    @ApplicationContext context: Context,
    logger: TestSessionLogger,
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    @KnownSeed12 knownSeed12: KnownSeed,
    private val privilegedSeedVaultChecker: PrivilegedSeedVaultChecker,
) : PubKeyDerivationTestCase(
    context = context,
    knownSeed12AuthorizedChecker = knownSeed12AuthorizedChecker,
    knownSeed12 = knownSeed12,
    hasSeedVaultPermissionChecker = hasSeedVaultPermissionChecker,
    logger = logger,
    keysToFetch = 10,
    startIndex = 1000,
    fetchPermissionedPubkey = true,
    expectedPubKeys = arrayListOf(
        PublicKeyResponse(
            byteArrayOf(-26, 76, 83, 45, -65, 46, 67, -36, 126, 35, 43, -81, -76, -16, 97, -127, 39, -62, 98, -1, 1, -82, -64, 126, 30, -46, -69, -91, 19, 81, 78, 76),
            "GVzGp85PYduswFWJ3RLzqsUuzcDG5H2aQYPnipMjsYMy",
            Uri.parse("bip32:/m/44'/501'/10000'/0'/1000'"),
        ),
        PublicKeyResponse(
            byteArrayOf(-119, -9, -23, 57, 112, -64, 89, -39, -58, 117, 35, -106, -97, -57, -18, -127, 37, -73, 87, 84, -31, 45, -96, -83, -48, -123, -13, 47, -4, -52, -101, -89),
            "AHa7HYn5dg1rebpwQ546zmf14H29gDwVSCPRR6qcEK3t",
            Uri.parse("bip32:/m/44'/501'/10000'/0'/1001'"),
        ),
        PublicKeyResponse(
            byteArrayOf(9, 52, -13, -117, -34, 30, -105, -41, 51, 123, 95, -79, 32, -123, 81, 26, -102, 78, -70, 52, 26, 2, 32, 122, -48, 11, -90, -72, -100, -83, -85, -95),
            "cwVz5KACRbasBK2sCz7TsXCtAarB2mk5JCmbLiVfZYC",
            Uri.parse("bip32:/m/44'/501'/10000'/0'/1002'"),
        ),
        PublicKeyResponse(
            byteArrayOf(106, 34, -50, 19, -68, 35, -80, -9, -3, -72, 71, -119, 32, -54, 100, -92, -81, -49, 18, 9, 18, 6, 62, 93, -97, 122, -10, 111, 30, 119, -6, -41),
            "89JzsAbko7dU2W1FzVXvZKq1pKTVgjPbZTjM9fBgFEox",
            Uri.parse("bip32:/m/44'/501'/10000'/0'/1003'"),
        ),
        PublicKeyResponse(
            byteArrayOf(-70, 117, -103, 119, 108, -80, 1, 11, -89, -40, 23, -102, 48, 41, 23, -128, 49, 24, -68, 37, -87, 45, -42, 82, -58, -111, -104, 74, -99, 111, -61, 11),
            "DYrqZshk54Aa2x41UVx9tWrMzK1bqWJYS4T9bNG1E2QA",
            Uri.parse("bip32:/m/44'/501'/10000'/0'/1004'"),
        ),
        PublicKeyResponse(
            byteArrayOf(-56, 31, -33, -90, -92, 85, -84, 72, -61, 127, 6, -10, 59, 65, -35, 88, -45, -53, -25, 5, -117, -47, 64, 52, -120, -54, 71, 111, -55, 26, 0, 19),
            "EUCjGYRTySafCJH7mtXr2kKUX7Uo6GxHiLAZQgne4HtW",
            Uri.parse("bip32:/m/44'/501'/10000'/0'/1005'"),
        ),
        PublicKeyResponse(
            byteArrayOf(-6, -67, 48, -104, -39, 117, 35, 41, 18, 18, 111, 105, 65, 40, -32, -50, -9, -118, 120, 102, -77, -81, -61, 47, 75, 58, 125, 64, 1, 19, 77, -96),
            "HsnFCoQZTp6N7CLXy39SdKT2LPRtzSn6twwt7pqnr8Bh",
            Uri.parse("bip32:/m/44'/501'/10000'/0'/1006'"),
        ),
        PublicKeyResponse(
            byteArrayOf(-89, -103, -115, -43, 3, 83, 77, -75, 96, -47, 98, 66, -75, 49, 49, 21, 23, 28, 46, -5, -48, -24, 22, 61, -40, -106, -47, -15, 64, 23, -66, 111),
            "CHEtbFBrSEmB7dBMgxWcZ1pJ3XyNCsR38C5sF9FcCm82",
            Uri.parse("bip32:/m/44'/501'/10000'/0'/1007'"),
        ),
        PublicKeyResponse(
            byteArrayOf(-79, 16, -110, -73, 91, -128, 89, 66, -85, -61, -84, -37, -31, 34, 58, -61, 118, -117, 60, 24, 70, -106, 100, 89, 119, -88, 42, -55, -12, 66, 41, -87),
            "CvBpYxtpr76CVaWaVGrrJUwPGEQ3myVExvzfqmbN5Veg",
            Uri.parse("bip32:/m/44'/501'/10000'/0'/1008'"),
        ),
        PublicKeyResponse(
            byteArrayOf(-115, -61, -11, 109, 19, -22, -56, -85, 61, -95, 108, 42, 119, 3, -14, -80, 101, 31, 31, 110, 119, -105, -37, -102, 58, 24, -123, -127, -128, 86, -108, -88),
            "AYPnx884aqNHQ9p6gAuYzRhuQM6WYUBnW6w76hRGPh1h",
            Uri.parse("bip32:/m/44'/501'/10000'/0'/1009'"),
        ),
    )
) {
    override val id: String = "pafpktc"
    override val description: String = "Fetch 10 permissioned pubkeys."
    override val instructions: String get() {
        return if (privilegedSeedVaultChecker.isPrivileged()) {
            "This should be done without any user interaction. If an auth dialog is shown, the test fails."
        } else {
            "This should should required a user authorization. If keys are fetched without user authorization, then the test fails."
        }
    }
}