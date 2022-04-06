/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.example.seedvault;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.Objects;

public class Bip32Level {
    @IntRange(from=0, to=2147483647)
    public final int index;
    public final boolean hardened;

    public Bip32Level(@IntRange(from=0, to=2147483647) int index, boolean hardened) {
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
        Bip32Level that = (Bip32Level) o;
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
