/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvault;

import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/** Base class for BIP derivation {@link Uri}s */
@RequiresApi(api = Build.VERSION_CODES.M) // library minSdk is 17
public abstract class BipDerivationPath {
    protected BipDerivationPath() {}

    /**
     * Convert this {@link BipDerivationPath} into a {@link Uri}
     * @return a {@link Uri} encoding this BIP derivation path
     */
    @NonNull
    public abstract Uri toUri();

    /**
     * Factory method to convert {@link WalletContractV1#BIP32_URI_SCHEME} and
     * {@link WalletContractV1#BIP44_URI_SCHEME} {@link Uri}s into {@link BipDerivationPath}s
     * @param bipUri a {@link Uri} encoding either a {@link WalletContractV1#BIP32_URI_SCHEME} or a
     *      {@link WalletContractV1#BIP44_URI_SCHEME} {@link Uri}
     * @return a {@link BipDerivationPath}
     */
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
