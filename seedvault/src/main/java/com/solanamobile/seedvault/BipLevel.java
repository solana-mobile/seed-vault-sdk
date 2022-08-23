/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvault;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.Objects;

/** An immutable data class encoding a level in a {@link BipDerivationPath} */
@RequiresApi(api = Build.VERSION_CODES.M) // library minSdk is 17
public class BipLevel {
    /** Index of this {@link BipLevel} */
    @WalletContractV1.BipIndex
    public final int index;

    /** Whether this {@link BipLevel} is hardened */
    public final boolean hardened;

    /**
     * Construct a new {@link BipLevel}
     * @param index level index
     * @param hardened whether or not this level should be hardened
     */
    public BipLevel(@WalletContractV1.BipIndex int index, boolean hardened) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be in the range [0, 2^31)");
        }
        this.index = index;
        this.hardened = hardened;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BipLevel that = (BipLevel) o;
        return index == that.index && hardened == that.hardened;
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, hardened);
    }

    @Override
    @NonNull
    public String toString() {
        return "Bip32Level{" +
                "index=" + index +
                ", hardened=" + hardened +
                '}';
    }
}
