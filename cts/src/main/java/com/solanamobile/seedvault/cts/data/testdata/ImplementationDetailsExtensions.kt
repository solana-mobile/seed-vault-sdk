/*
 * Copyright (c) 2026 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.testdata

/** The largest payload accepted by the implementation to fit [requestedSignatures]. */
internal fun ImplementationDetails.maxPayloadSize(requestedSignatures: Int): Int =
    MAX_TRANSACTION_SIZE - requestedSignatures * SigningDatasets.ED25519_SIGNATURE_SIZE

/** Whether this implementation supports Solana Transaction V1. */
internal val ImplementationDetails.supportsTransactionV1: Boolean
    get() = MAX_TRANSACTION_SIZE >= SigningDatasets.MAX_TRANSACTION_V1_SIZE
