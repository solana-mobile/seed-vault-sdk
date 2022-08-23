/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvault;

import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.Size;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An immutable BIP32 derivation path (see
 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki">BIP-0032</a>)
 */
@RequiresApi(api = Build.VERSION_CODES.M) // library minSdk is 17
public class Bip32DerivationPath extends BipDerivationPath {
    /** Builder pattern for {@link Bip32DerivationPath} */
    public static class Builder {
        private final List<BipLevel> mLevels = new ArrayList<>();

        private Builder() {}

        /**
         * Append a {@link BipLevel} to this builder
         * @param level a {@link BipLevel}
         * @return this builder
         */
        @NonNull
        public Builder appendLevel(@NonNull BipLevel level) {
            mLevels.add(level);
            return this;
        }

        /**
         * Append {@link BipLevel}s to this builder
         * @param levels a {@link Collection} of {@link BipLevel}s
         * @return this builder
         */
        @NonNull
        public Builder appendLevels(@NonNull Collection<BipLevel> levels) {
            mLevels.addAll(levels);
            return this;
        }

        /**
         * Construct a new {@link Bip32DerivationPath} from this builder
         * @return a new {@link Bip32DerivationPath}
         */
        @NonNull
        public Bip32DerivationPath build() {
            return new Bip32DerivationPath(mLevels);
        }
    }

    private final List<BipLevel> mLevels;

    /**
     * Construct a {@link Bip32DerivationPath}
     * @param levels a {@link Collection} of {@link BipLevel}s
     * @throws IndexOutOfBoundsException if levels contains more than
     *      {@link WalletContractV1#BIP32_URI_MAX_DEPTH} entries
     */
    public Bip32DerivationPath(@NonNull @Size(max=WalletContractV1.BIP32_URI_MAX_DEPTH) Collection<BipLevel> levels) {
        if (levels.size() > WalletContractV1.BIP32_URI_MAX_DEPTH) {
            throw new IndexOutOfBoundsException("BIP32 max supported depth (" + WalletContractV1.BIP32_URI_MAX_DEPTH + ") exceeded");
        }

        mLevels = new ArrayList<>(levels);
    }

    /**
     * Get an immutable list of the {@link BipLevel}s in this {@link Bip32DerivationPath}
     * @return an immutable list of {@link BipLevel}s
     */
    @NonNull
    @Size(max=WalletContractV1.BIP32_URI_MAX_DEPTH)
    public List<BipLevel> getLevels() {
        return Collections.unmodifiableList(mLevels);
    }

    @Override
    @NonNull
    public Uri toUri() {
        final Uri.Builder builder = new Uri.Builder();

        builder.scheme(WalletContractV1.BIP32_URI_SCHEME);
        builder.appendPath(WalletContractV1.BIP32_URI_MASTER_KEY_INDICATOR);

        for (BipLevel level : mLevels) {
            final String pathElement;
            if (level.hardened) {
                pathElement = level.index + WalletContractV1.BIP_URI_HARDENED_INDEX_IDENTIFIER;
            } else {
                pathElement = String.valueOf(level.index);
            }

            builder.appendPath(pathElement);
        }

        return builder.build();
    }

    /**
     * Create a {@link Bip32DerivationPath} from the specified {@link Uri}. It must follow the
     * format defined in {@link WalletContractV1#BIP32_URI_SCHEME}.
     * @param bip32Uri a {@link WalletContractV1#BIP32_URI_SCHEME} {@link Uri}
     * @return a new {@link Bip32DerivationPath}
     * @throws UnsupportedOperationException if bip32Uri is not a valid
     *      {@link WalletContractV1#BIP32_URI_SCHEME} {@link Uri}
     */
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
            final boolean hardened = pathElement.endsWith(WalletContractV1.BIP_URI_HARDENED_INDEX_IDENTIFIER);
            final int index;
            try {
                index = Integer.parseInt(pathElement.substring(0, pathElement.length() - (hardened ?
                        WalletContractV1.BIP_URI_HARDENED_INDEX_IDENTIFIER.length() : 0)));
            } catch (NumberFormatException e) {
                throw new UnsupportedOperationException("Path element [" + i + "](" + pathElement + ") could not be parsed as a BIP32 level");
            }

            builder.appendLevel(new BipLevel(index, hardened));
        }

        return builder.build();
    }

    /**
     * Create a new {@link Builder}
     * @return a new {@link Builder}
     */
    @NonNull
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
