/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.example.seedvault;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Size;

import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Bip32DerivationPath extends BipDerivationPath {
    public static class Builder {
        private final List<Bip32Level> mLevels = new ArrayList<>();

        private Builder() {}

        @NonNull
        public Builder appendLevel(@NonNull Bip32Level level) {
            mLevels.add(level);
            return this;
        }

        @NonNull
        public Builder appendLevels(@NonNull List<Bip32Level> levels) {
            mLevels.addAll(levels);
            return this;
        }

        @NonNull
        public Bip32DerivationPath build() {
            return new Bip32DerivationPath(mLevels);
        }
    }

    private final List<Bip32Level> mLevels;

    public Bip32DerivationPath(@NonNull @Size(max=WalletContractV1.BIP32_URI_MAX_DEPTH) List<Bip32Level> levels) {
        if (levels.size() >= WalletContractV1.BIP32_URI_MAX_DEPTH) {
            throw new IndexOutOfBoundsException("BIP32 max supported depth (" + WalletContractV1.BIP32_URI_MAX_DEPTH + ") exceeded");
        }

        mLevels = new ArrayList<>(levels);
    }

    @NonNull
    @Size(max=WalletContractV1.BIP32_URI_MAX_DEPTH)
    public List<Bip32Level> getLevels() {
        return Collections.unmodifiableList(mLevels);
    }

    @NonNull
    public Uri toUri() {
        final Uri.Builder builder = new Uri.Builder();

        builder.scheme(WalletContractV1.BIP32_URI_SCHEME);
        builder.appendPath(WalletContractV1.BIP32_URI_MASTER_KEY_INDICATOR);

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
    public static Bip32DerivationPath fromUri(@NonNull Uri bip32Uri) {
        if (!bip32Uri.isHierarchical()) {
            throw new UnsupportedOperationException("BIP32 URI must be hierarchical");
        }

        if (bip32Uri.isAbsolute() && !bip32Uri.getScheme().equals(WalletContractV1.BIP32_URI_SCHEME)) {
            throw new UnsupportedOperationException("BIP32 URI absolute scheme must be " + WalletContractV1.BIP32_URI_SCHEME);
        }

        if (bip32Uri.getAuthority() != null) {
            throw new UnsupportedOperationException("BIP32 URI authority must be null");
        }

        if (bip32Uri.getQuery() != null) {
            throw new UnsupportedOperationException("BIP32 URI query must be null");
        }

        if (bip32Uri.getFragment() != null) {
            throw new UnsupportedOperationException("BIP32 URI fragment must be null");
        }

        final List<String> path = bip32Uri.getPathSegments();
        if (path.isEmpty() || !path.get(0).equals(WalletContractV1.BIP32_URI_MASTER_KEY_INDICATOR)) {
            throw new UnsupportedOperationException("BIP32 URI path must start with a master key indicator");
        }

        final Builder builder = newBuilder();

        for (int i = 1; i < path.size(); i++) {
            final String pathElement = path.get(i);
            final boolean hardened = pathElement.endsWith(WalletContractV1.BIP32_URI_HARDENED_INDEX_IDENTIFIER);
            final int index;
            try {
                index = Integer.parseInt(pathElement.substring(0, pathElement.length() - (hardened ?
                        WalletContractV1.BIP32_URI_HARDENED_INDEX_IDENTIFIER.length() : 0)));
            } catch (NumberFormatException e) {
                throw new UnsupportedOperationException("Path element [" + i + "](" + pathElement + ") could not be parsed as a BIP32 level");
            }

            builder.appendLevel(new Bip32Level(index, hardened));
        }

        return builder.build();
    }

    @NonNull
    @Contract(" -> new")
    public static Builder newBuilder() {
        return new Builder();
    }

    @Override
    @NonNull
    public String toString() {
        return "Bip32DerivationPath{" +
                "mLevels=" + mLevels +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bip32DerivationPath that = (Bip32DerivationPath) o;
        return Objects.equals(mLevels, that.mLevels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mLevels);
    }
}
