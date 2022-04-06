/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvault;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Bip44DerivationPath extends BipDerivationPath {
    public static class Builder {
        private Bip32Level mAccount;
        private Bip32Level mChange;
        private Bip32Level mAddressIndex;

        private Builder() {}

        @NonNull
        public Builder setAccount(@NonNull Bip32Level account) {
            mAccount = account;
            return this;
        }

        @NonNull
        public Builder setChange(@Nullable Bip32Level change) {
            mChange = change;
            return this;
        }

        @NonNull
        public Builder setAddressIndex(@Nullable Bip32Level addressIndex) {
            mAddressIndex = addressIndex;
            return this;
        }

        @NonNull
        public Bip44DerivationPath build() {
            return new Bip44DerivationPath(mAccount, mChange, mAddressIndex);
        }
    }

    private final List<Bip32Level> mLevels = new ArrayList<>();

    public Bip44DerivationPath(@NonNull Bip32Level account,
                               @Nullable Bip32Level change,
                               @Nullable Bip32Level addressIndex) {
        if (!account.hardened) {
            throw new UnsupportedOperationException("account must be hardened");
        } else if (change == null && addressIndex != null) {
            throw new UnsupportedOperationException("addressIndex must be null when change is null");
        }

        mLevels.add(account);
        if (change != null) {
            mLevels.add(change);
            if (addressIndex != null) {
                mLevels.add(addressIndex);
            }
        }
    }

    @NonNull
    public Bip32Level getAccount() {
        return mLevels.get(0);
    }

    public boolean hasChange() {
        return mLevels.size() >= 2;
    }

    @Nullable
    public Bip32Level getChange() {
        if (!hasChange()) {
            return null;
        }
        return mLevels.get(1);
    }

    public boolean hasAddressIndex() {
        return mLevels.size() >= 3;
    }

    @Nullable
    public Bip32Level getAddressIndex() {
        if (!hasAddressIndex()) {
            return null;
        }
        return mLevels.get(2);
    }

    @NonNull
    public List<Bip32Level> getLevels() {
        return Collections.unmodifiableList(mLevels);
    }

    @NonNull
    public Uri toUri() {
        final Uri.Builder builder = new Uri.Builder();

        builder.scheme(WalletContractV1.BIP44_URI_SCHEME);

        for (Bip32Level level : mLevels) {
            final String pathElement;
            if (level.hardened) {
                pathElement = level.index + WalletContractV1.BIP32_URI_HARDENED_INDEX_IDENTIFIER;
            } else {
                pathElement = String.valueOf(level.index);
            }

            builder.appendPath(pathElement);
        }

        return builder.build();
    }

    @NonNull
    public static Bip44DerivationPath fromUri(@NonNull Uri bip44Uri) {
        if (!bip44Uri.isHierarchical()) {
            throw new UnsupportedOperationException("BIP44 URI must be hierarchical");
        }

        if (!bip44Uri.isAbsolute() || !bip44Uri.getScheme().equals(WalletContractV1.BIP44_URI_SCHEME)) {
            throw new UnsupportedOperationException("BIP44 URI must be absolute with scheme " + WalletContractV1.BIP44_URI_SCHEME);
        }

        if (bip44Uri.getAuthority() != null) {
            throw new UnsupportedOperationException("BIP44 URI authority must be null");
        }

        if (bip44Uri.getQuery() != null) {
            throw new UnsupportedOperationException("BIP44 URI query must be null");
        }

        if (bip44Uri.getFragment() != null) {
            throw new UnsupportedOperationException("BIP44 URI fragment must be null");
        }

        final List<String> path = bip44Uri.getPathSegments();
        if (path.size() < 1 || path.size() > 3) {
            throw new UnsupportedOperationException("BIP44 URI path must contain between 1 and 3 elements");
        }

        final Builder builder = newBuilder();

        for (int i = 0; i < path.size(); i++) {
            final String pathElement = path.get(i);
            final boolean hardened = pathElement.endsWith(WalletContractV1.BIP32_URI_HARDENED_INDEX_IDENTIFIER);
            final int index;
            try {
                index = Integer.parseInt(pathElement.substring(0, pathElement.length() -
                        WalletContractV1.BIP32_URI_HARDENED_INDEX_IDENTIFIER.length()));
            } catch (NumberFormatException e) {
                throw new UnsupportedOperationException("Path element " + i + " could not be parsed as a BIP32 level");
            }

            final Bip32Level level = new Bip32Level(index, hardened);

            switch (i) {
                case 0: builder.setAccount(level); break;
                case 1: builder.setChange(level); break;
                case 2: builder.setAddressIndex(level); break;
                default: throw new AssertionError("Impossible case!");
            }
        }

        return builder.build();
    }

    @NonNull
    @Contract(value = " -> new", pure = true)
    public static Builder newBuilder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bip44DerivationPath that = (Bip44DerivationPath) o;
        return Objects.equals(mLevels, that.mLevels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mLevels);
    }

    @Override
    @NonNull
    public String toString() {
        return "Bip44DerivationPath{" +
                "mLevels=" + mLevels +
                '}';
    }
}
