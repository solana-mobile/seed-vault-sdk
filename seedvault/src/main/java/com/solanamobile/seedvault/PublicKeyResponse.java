/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvault;

import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.Arrays;
import java.util.Objects;

/**
 * The account public key generated in response to an {@link WalletContractV1#ACTION_GET_PUBLIC_KEY}
 * request.
 *
 * @version 0.2.6
 */
@RequiresApi(api = Build.VERSION_CODES.M) // library minSdk is 17
public class PublicKeyResponse implements Parcelable {
    /**
     * This exception indicates that an account does not exist for this
     * {@code WalletContractV1.PURPOSE_*} and BIP derivation path. In some key derivation schemes
     * (e.g. Ed25519-BIP32), not every key is guaranteed to exist. In others (e.g. Ed25519-SLIP10),
     * every key will always exist.
     */
    public static final class KeyNotValidException extends Exception {}

    @Nullable
    private final byte[] mPublicKey;

    @Nullable
    private final String mPublicKeyEncoded;

    /**
     * The resolved derivation path corresponding to this account public key. This is fully resolved
     * to the canonical representation for the {@code WalletContractV1.PURPOSE_*} and key derivation
     * scheme.
     */
    @NonNull
    public final Uri resolvedDerivationPath;

    /**
     * Construct a new {@link PublicKeyResponse}
     * @param publicKey an account public key. If publicKey is null, this indicates that this
     *      response is for an account which does not exist in the account derivation scheme.
     * @param publicKeyEncoded the encoded account public key. The encoding scheme depends on the
     *      authorized purpose of the seed from which this account is derived (for e.g. Base58 for
     *      {@link WalletContractV1#PURPOSE_SIGN_SOLANA_TRANSACTION}). If publicKey is null, this
     *      should also be null. If publicKey is non-null, this should be the encoded
     *      representation of publicKey (or an empty string, if no encoding is specified for this
     *      {@code WalletContractV1.PURPOSE_*}.
     * @param resolvedDerivationPath the fully resolved derivation path for this account public key
     */
    public PublicKeyResponse(@Nullable byte[] publicKey,
                             @Nullable String publicKeyEncoded,
                             @NonNull Uri resolvedDerivationPath) {
        if ((publicKey == null) != (publicKeyEncoded == null)) {
            throw new IllegalArgumentException("Raw and encoded public key null-ness are inconsistent");
        }
        mPublicKey = (publicKey != null ? publicKey.clone() : null);
        mPublicKeyEncoded = publicKeyEncoded;
        this.resolvedDerivationPath = resolvedDerivationPath;
    }

    protected PublicKeyResponse(Parcel in) {
        mPublicKey = in.createByteArray();
        mPublicKeyEncoded = in.readString();
        resolvedDerivationPath = in.readTypedObject(Uri.CREATOR);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByteArray(mPublicKey);
        dest.writeString(mPublicKeyEncoded);
        dest.writeTypedObject(resolvedDerivationPath, 0);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<PublicKeyResponse> CREATOR = new Creator<PublicKeyResponse>() {
        @Override
        public PublicKeyResponse createFromParcel(Parcel in) {
            return new PublicKeyResponse(in);
        }

        @Override
        public PublicKeyResponse[] newArray(int size) {
            return new PublicKeyResponse[size];
        }
    };

    /**
     * Get the account public key for this {@link PublicKeyResponse}
     * @return the account public key
     * @throws KeyNotValidException if the account does not exist for this
     *      {@code WalletContractV1.PURPOSE_*} and BIP derivation path.
     */
    public byte[] getPublicKey() throws KeyNotValidException {
        if (mPublicKey == null) {
            throw new KeyNotValidException();
        }
        return mPublicKey;
    }

    /**
     * Get the encoded account public key for this {@link PublicKeyResponse}
     * @return the encoded account public key
     * @throws KeyNotValidException if the account does not exist for this
     *      {@code WalletContractV1.PURPOSE_*} and BIP derivation path.
     */
    public String getPublicKeyEncoded() throws KeyNotValidException {
        if (mPublicKeyEncoded == null) {
            throw new KeyNotValidException();
        }
        return mPublicKeyEncoded;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PublicKeyResponse that = (PublicKeyResponse) o;
        return Arrays.equals(mPublicKey, that.mPublicKey) && Objects.equals(mPublicKeyEncoded, that.mPublicKeyEncoded) && resolvedDerivationPath.equals(that.resolvedDerivationPath);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mPublicKeyEncoded, resolvedDerivationPath);
        result = 31 * result + Arrays.hashCode(mPublicKey);
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        return "PublicKeyResponse{" +
                "mPublicKey=" + Arrays.toString(mPublicKey) +
                ", mPublicKeyEncoded=" + mPublicKeyEncoded +
                ", resolvedDerivationPath=" + resolvedDerivationPath +
                '}';
    }
}
