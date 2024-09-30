/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.usecase

import com.solanamobile.seedvault.Bip32DerivationPath
import com.solanamobile.seedvault.Bip44DerivationPath
import com.solanamobile.seedvault.BipLevel
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvaultimpl.model.Authorization

object PermissionedAccountUseCase {
    private val BIP44_PERMISSIONED_ACCOUNT_ANCESTOR = Bip44DerivationPath.newBuilder()
        .setAccount(BipLevel(WalletContractV1.PERMISSIONED_BIP44_ACCOUNT, true))
        .setChange(BipLevel(WalletContractV1.PERMISSIONED_BIP44_CHANGE, false))
        .build()

    fun getPermissionedAccountAncestorForPurpose(purpose: Authorization.Purpose): Bip32DerivationPath {
        return BIP44_PERMISSIONED_ACCOUNT_ANCESTOR.normalize(purpose).toBip32DerivationPath(purpose)
    }
}