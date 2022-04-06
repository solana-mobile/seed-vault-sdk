/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.example.walletapidemo.model

data class Seed(
    val id: Int,
    val details: SeedDetails,
    val authorizations: List<Authorization> = listOf(),
    val accounts: List<Account> = listOf()
)