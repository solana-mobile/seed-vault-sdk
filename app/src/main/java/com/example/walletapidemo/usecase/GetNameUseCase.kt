/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.example.walletapidemo.usecase

import com.example.walletapidemo.model.Account
import com.example.walletapidemo.model.Seed
import java.nio.charset.StandardCharsets

object GetNameUseCase {
    fun getName(s: Seed): String {
        return if (s.details.name.isNullOrBlank()) "Seed ${s.id}" else s.details.name
    }

    fun getName(a: Account): String {
        return if (a.name.isNullOrBlank()) Base58EncodeUseCase(a.publicKey) else a.name
    }
}