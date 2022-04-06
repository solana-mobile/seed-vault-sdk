/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.model

data class Seed(
    val id: Int,
    val details: SeedDetails,
    val authorizations: List<Authorization> = listOf(),
    val accounts: List<Account> = listOf()
)