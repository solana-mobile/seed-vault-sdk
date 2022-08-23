/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvault;

import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An immutable BIP44 derivation path (see
 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki">BIP-0044</a>)
 */
@RequiresApi(api = Build.VERSION_CODES.M) // library minSdk is 17
public class Bip44DerivationPath extends BipDerivationPath {
    /** Builder pattern for {@link Bip44DerivationPath} */
    public static class Builder {
        private BipLevel mAccount;
        private BipLevel mChange;
        private BipLevel mAddressIndex;

        private Builder() {}

        /**
         * Set the account {@link BipLevel} for this {@link Builder}
         * @param account the account {@link BipLevel}
         * @return this builder
         */
        @NonNull
        public Builder setAccount(@NonNull BipLevel account) {
            mAccount = account;
            return this;
        }

        /**
         * Set the change {@link BipLevel} for this {@link Builder}
         * @param change the change {@link BipLevel}
         * @return this builder
         */
        @NonNull
        public Builder setChange(@Nullable BipLevel change) {
            mChange = change;
            return this;
        }

        /**
         * Set the address index {@link BipLevel} for this {@link Builder}
         * @param addressIndex the address index {@link BipLevel}
         * @return this builder
         */
        @NonNull
        public Builder setAddressIndex(@Nullable BipLevel addressIndex) {
            mAddressIndex = addressIndex;
            return this;
        }

        /**
         * Construct a new {@link Bip44DerivationPath} from this builder
         * @return a new {@link Bip44DerivationPath}
         */
        @NonNull
        public Bip44DerivationPath build() {
            return new Bip44DerivationPath(mAccount, mChange, mAddressIndex);
        }
    }

    private final List<BipLevel> mLevels = new ArrayList<>();

    /**
     * Construct a new {@link Bip44DerivationPath}
     * @param account the account {@link BipLevel}
     * @param change the change {@link BipLevel}
     * @param addressIndex the address index {@link BipLevel}
     * @throws UnsupportedOperationException if account is not hardened, or if change is null and
     *      addressIndex is not null
     */
    public Bip44DerivationPath(@NonNull BipLevel account,
                               @Nullable BipLevel change,
                               @Nullable BipLevel addressIndex) {
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

    /**
     * Get the account {@link BipLevel} for this {@link Bip44DerivationPath}
     * @return the account {@link BipLevel}
     */
    @NonNull
    public BipLevel getAccount() {
        return mLevels.get(0);
    }

    /**
     * Test whether this {@link Bip44DerivationPath} has the change level
     * @return true if this {@link Bip44DerivationPath} has the change level, else false
     */
    public boolean hasChange() {
        return mLevels.size() >= 2;
    }

    /**
     * Get the change {@link BipLevel} for this {@link Bip44DerivationPath}
     * @return the change {@link BipLevel}
     */
    @Nullable
    public BipLevel getChange() {
        if (!hasChange()) {
            return null;
        }
        return mLevels.get(1);
    }

    /**
     * Test whether this {@link Bip44DerivationPath} has the address index level
     * @return true if this {@link Bip44DerivationPath} has the address level, else false
     */
    public boolean hasAddressIndex() {
        return mLevels.size() >= 3;
    }

    /**
     * Get the address index {@link BipLevel} for this {@link Bip44DerivationPath}
     * @return the address index {@link BipLevel}
     */
    @Nullable
    public BipLevel getAddressIndex() {
        if (!hasAddressIndex()) {
            return null;
        }
        return mLevels.get(2);
    }

    /**
     * Get an immutable list of the {@link BipLevel}s in this {@link Bip44DerivationPath}
     * @return an immutable list of {@link BipLevel}s
     */
    @NonNull
    public List<BipLevel> getLevels() {
        return Collections.unmodifiableList(mLevels);
    }

    @Override
    @NonNull
    public Uri toUri() {
        final Uri.Builder builder = new Uri.Builder();

        builder.scheme(WalletContractV1.BIP44_URI_SCHEME);

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
     * Create a {@link Bip44DerivationPath} from the specified {@link Uri}. It must follow the
     * format defined in {@link WalletContractV1#BIP44_URI_SCHEME}.
     * @param bip44Uri a {@link WalletContractV1#BIP44_URI_SCHEME} {@link Uri}
     * @return a new {@link Bip44DerivationPath}
     * @throws UnsupportedOperationException if bip44Uri is not a valid
     *      {@link WalletContractV1#BIP44_URI_SCHEME} {@link Uri}
     */
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
            final boolean hardened = pathElement.endsWith(WalletContractV1.BIP_URI_HARDENED_INDEX_IDENTIFIER);
            final int index;
            try {
                index = Integer.parseInt(pathElement.substring(0, pathElement.length() -
                        WalletContractV1.BIP_URI_HARDENED_INDEX_IDENTIFIER.length()));
            } catch (NumberFormatException e) {
                throw new UnsupportedOperationException("Path element " + i + " could not be parsed as a BIP32 level");
            }

            final BipLevel level = new BipLevel(index, hardened);

            switch (i) {
                case 0: builder.setAccount(level); break;
                case 1: builder.setChange(level); break;
                case 2: builder.setAddressIndex(level); break;
                default: throw new AssertionError("Impossible case!");
            }
        }

        return builder.build();
    }

    /**
     * Create a new {@link Bip32DerivationPath.Builder}
     * @return a new {@link Bip32DerivationPath.Builder}
     */
    @NonNull
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
