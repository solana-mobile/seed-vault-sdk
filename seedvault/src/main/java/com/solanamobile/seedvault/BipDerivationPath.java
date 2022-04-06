/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvault;

import android.net.Uri;

import androidx.annotation.NonNull;

public abstract class BipDerivationPath {
    protected BipDerivationPath() {}

    @NonNull
    public abstract Uri toUri();

    @NonNull
    public static BipDerivationPath fromUri(@NonNull Uri bipUri) {
        final String scheme = bipUri.getScheme();
        if (scheme.equals(WalletContractV1.BIP32_URI_SCHEME)) {
            return Bip32DerivationPath.fromUri(bipUri);
        } else if (scheme.equals(WalletContractV1.BIP44_URI_SCHEME)) {
            return Bip44DerivationPath.fromUri(bipUri);
        } else {
            throw new UnsupportedOperationException("Unknown BIP derivation URI scheme '" + scheme + "'");
        }
    }
}
