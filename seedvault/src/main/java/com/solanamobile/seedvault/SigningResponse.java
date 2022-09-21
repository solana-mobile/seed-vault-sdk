/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvault;

import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The signatures generated in response to a {@link SigningRequest}
 *
 * @version 0.2.6
 */
@RequiresApi(api = Build.VERSION_CODES.M) // library minSdk is 17
public class SigningResponse implements Parcelable {
    @NonNull
    private final ArrayList<byte[]> mSignatures;

    @NonNull
    private final ArrayList<Uri> mResolvedDerivationPaths;

    /**
     * Construct a new {@link SigningResponse}
     * @param signatures the set of signatures for this {@link SigningResponse}
     */
    public SigningResponse(@NonNull List<byte[]> signatures,
                           @NonNull List<Uri> resolvedDerivationPaths) {
        mSignatures = new ArrayList<>(signatures);
        mResolvedDerivationPaths = new ArrayList<>(resolvedDerivationPaths);
    }

    protected SigningResponse(Parcel in) {
        final int size = in.readInt();
        mSignatures = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            mSignatures.add(in.createByteArray());
        }
        mResolvedDerivationPaths = in.createTypedArrayList(Uri.CREATOR);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mSignatures.size());
        for (byte[] signature : mSignatures) {
            dest.writeByteArray(signature);
        }
        dest.writeTypedList(mResolvedDerivationPaths);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<SigningResponse> CREATOR = new Creator<SigningResponse>() {
        @Override
        public SigningResponse createFromParcel(Parcel in) {
            return new SigningResponse(in);
        }

        @Override
        public SigningResponse[] newArray(int size) {
            return new SigningResponse[size];
        }
    };

    /**
     * Get the set of signatures for this {@link SigningResponse}
     * @return a {@link List} of signatures, corresponding to the BIP derivation paths specified in
     *      the {@link SigningRequest}
     */
    public List<byte[]> getSignatures() {
        return Collections.unmodifiableList(mSignatures);
    }

    /**
     * Get the set of resolved derivation paths corresponding to each signature. These are fully
     * resolved to the canonical representation for the {@code WalletContractV1.PURPOSE_*} and key
     * derivation scheme.
     * @return a {@link List} of resolved derivation paths, corresponding to each signature
     */
    public List<Uri> getResolvedDerivationPaths() {
        return Collections.unmodifiableList(mResolvedDerivationPaths);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SigningResponse that = (SigningResponse) o;
        return mSignatures.equals(that.mSignatures)
                && mResolvedDerivationPaths.equals(that.mResolvedDerivationPaths);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSignatures, mResolvedDerivationPaths);
    }

    @NonNull
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mSignatures.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(Arrays.toString(mSignatures.get(i)));
        }
        return "SigningResponse{" +
                "mSignatures=[" + sb +
                "], mResolvedDerivationPaths=" + mResolvedDerivationPaths +
                '}';
    }
}
