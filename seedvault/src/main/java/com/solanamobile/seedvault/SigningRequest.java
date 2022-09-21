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
 * A request to sign a payload with the specified BIP derivation paths
 *
 * @version 0.2.6
 */
@RequiresApi(api = Build.VERSION_CODES.M) // library minSdk is 17
public class SigningRequest implements Parcelable {
    /** The payload to sign */
    @NonNull
    public final byte[] payload;

    @NonNull
    private final ArrayList<Uri> mRequestedSignatures;
    private List<Uri> requestedSignatures; // unmodifiable view of mRequestedSignatures

    /**
     * Construct a new {@link SigningRequest}
     * @param payload the transaction payload to sign
     * @param requestedSignatures the derivation paths which which to sign payload
     */
    public SigningRequest(@NonNull byte[] payload, @NonNull List<Uri> requestedSignatures) {
        this.payload = payload.clone();
        mRequestedSignatures = new ArrayList<>(requestedSignatures);
    }

    protected SigningRequest(Parcel in) {
        payload = in.createByteArray();
        mRequestedSignatures = in.createTypedArrayList(Uri.CREATOR);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByteArray(payload);
        dest.writeTypedList(mRequestedSignatures);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<SigningRequest> CREATOR = new Creator<SigningRequest>() {
        @Override
        public SigningRequest createFromParcel(Parcel in) {
            return new SigningRequest(in);
        }

        @Override
        public SigningRequest[] newArray(int size) {
            return new SigningRequest[size];
        }
    };

    /**
     * Get the list of BIP derivation paths for which this payload should be signed
     * @return an unmodifiable {@link List} of BIP derivation paths
     */
    @NonNull
    public List<Uri> getRequestedSignatures() {
        if (requestedSignatures == null) {
            requestedSignatures = Collections.unmodifiableList(mRequestedSignatures);
        }
        return requestedSignatures;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SigningRequest that = (SigningRequest) o;
        return Arrays.equals(payload, that.payload) && mRequestedSignatures.equals(that.mRequestedSignatures);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mRequestedSignatures);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        return "SigningRequest{" +
                "payload=" + Arrays.toString(payload) +
                ", mRequestedSignatures=" + mRequestedSignatures +
                '}';
    }
}
