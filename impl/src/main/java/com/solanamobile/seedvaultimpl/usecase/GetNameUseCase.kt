/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.usecase

import com.solanamobile.seedvaultimpl.model.Account
import com.solanamobile.seedvaultimpl.model.Seed

object GetNameUseCase {
    fun getName(s: Seed): String {
        return if (s.details.name.isNullOrBlank()) "Seed ${s.id}" else s.details.name
    }

    fun getName(a: Account): String {
        return if (a.name.isNullOrBlank()) Base58EncodeUseCase(a.publicKey) else a.name
    }
}