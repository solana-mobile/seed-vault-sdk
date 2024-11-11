/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault;

import androidx.annotation.RequiresApi;

/**
 * Helper functions for derivation paths of the form
 * <pre>m/44'/[coin]'/10000’/0/X</pre>, where <pre>X</pre> is a standard BIP-44
 * account index. These derivation paths have a special meaning when an app holds the
 * {@link WalletContractV1#PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED} permission.
 */
@RequiresApi(api = SeedVault.MIN_API_FOR_SEED_VAULT_PRIVILEGED)
public final class PermissionedAccount {
    private PermissionedAccount() {}

    /**
     * Returns {@link Bip44DerivationPath} for the permissioned account at <code>addressIndex</code>
     *
     * @param addressIndex Address index defined in <a href="https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki">BIP44</a>
     * @return {@link Bip44DerivationPath} for the permissioned account at <code>addressIndex</code>
     */
    public static Bip44DerivationPath getPermissionedAccountDerivationPath(int addressIndex) {
        return Bip44DerivationPath.newBuilder()
                .setAccount(new BipLevel(WalletContractV1.PERMISSIONED_BIP44_ACCOUNT, true))
                .setChange(new BipLevel(WalletContractV1.PERMISSIONED_BIP44_CHANGE, false))
                .setAddressIndex(new BipLevel(addressIndex, false))
                .build();
    }
}
