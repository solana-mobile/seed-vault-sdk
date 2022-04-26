/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.usecase

import com.solanamobile.seedvault.WalletContractV1

object RequestLimitsUseCase {
    const val MAX_SIGNING_REQUESTS = WalletContractV1.MIN_SUPPORTED_SIGNING_REQUESTS
    const val MAX_REQUESTED_SIGNATURES = WalletContractV1.MIN_SUPPORTED_REQUESTED_SIGNATURES
    const val MAX_REQUESTED_PUBLIC_KEYS = WalletContractV1.MIN_SUPPORTED_REQUESTED_PUBLIC_KEYS
}