/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.example.seedvault;

import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class Wallet {
    public static final class NotModifiedException extends Exception {
        public NotModifiedException(String message) {
            super(message);
        }
    }

    public static final class ActionFailedException extends Exception {
        public ActionFailedException(String message) {
            super(message);
        }
    }

    private Wallet() {}

    @NonNull
    public static Intent authorizeSeed(
            @WalletContractV1.Purpose int purpose) {
        return new Intent()
                .setPackage("com.example.walletapidemo")
                .setAction(WalletContractV1.ACTION_AUTHORIZE_SEED_ACCESS)
                .putExtra(WalletContractV1.EXTRA_PURPOSE, purpose);
    }

    @IntRange(from=0)
    public static int onAuthorizeSeedResult(
            int resultCode,
            @Nullable Intent result) throws ActionFailedException {
        if (resultCode != Activity.RESULT_OK) {
            throw new ActionFailedException("authorizeSeed failed with result=" + resultCode);
        } else if (result == null) {
            throw new ActionFailedException("authorizeSeed failed to return a result");
        }

        final int authToken = result.getIntExtra(WalletContractV1.EXTRA_AUTH_TOKEN,
                WalletContractV1.AUTH_TOKEN_INVALID);
        if (authToken == WalletContractV1.AUTH_TOKEN_INVALID) {
            throw new ActionFailedException("authorizeSeed returned an invalid AuthToken");
        }

        return authToken;
    }

    @NonNull
    public static Intent signTransaction(
            @IntRange(from=0) int authToken,
            @NonNull Uri derivationPath,
            @NonNull byte[] transaction) {
        return new Intent()
                .setPackage("com.example.walletapidemo")
                .setAction(WalletContractV1.ACTION_SIGN_TRANSACTION)
                .setData(derivationPath)
                .putExtra(WalletContractV1.EXTRA_AUTH_TOKEN, authToken)
                .putExtra(WalletContractV1.EXTRA_TRANSACTION, transaction);
    }

    public static final class SignTransactionResult {
        @NonNull public final byte[] signature;
        @NonNull public final Uri resolvedDerivationPath;

        private SignTransactionResult(@NonNull byte[] signature, @NonNull Uri resolvedDerivationPath) {
            this.signature = signature;
            this.resolvedDerivationPath = resolvedDerivationPath;
        }
    }

    @NonNull
    public static SignTransactionResult onSignTransactionResult(
            int resultCode,
            @Nullable Intent result) throws ActionFailedException {
        if (resultCode != Activity.RESULT_OK) {
            throw new ActionFailedException("signTransaction failed with result=" + resultCode);
        } else if (result == null) {
            throw new ActionFailedException("signTransaction failed to return a result");
        }

        final byte[] sig = result.getByteArrayExtra(WalletContractV1.EXTRA_SIGNATURE);
        if (sig == null) {
            throw new ActionFailedException("signTransaction returned a null signature");
        }

        final Uri resolvedDerivationPath = result.getData();
        if (resolvedDerivationPath == null) {
            throw new ActionFailedException("signTransaction returned a null derivation path");
        }

        return new SignTransactionResult(sig, resolvedDerivationPath);
    }

    @NonNull
    public static Intent requestPublicKey(
            @IntRange(from=0) int authToken,
            @NonNull Uri derivationPath) {
        return new Intent()
                .setPackage("com.example.walletapidemo")
                .setAction(WalletContractV1.ACTION_GET_PUBLIC_KEY)
                .setData(derivationPath)
                .putExtra(WalletContractV1.EXTRA_AUTH_TOKEN, authToken);
    }

    public static final class RequestPublicKeyResult {
        @NonNull public final byte[] publicKey;
        @NonNull public final Uri resolvedDerivationPath;

        private RequestPublicKeyResult(@NonNull byte[] publicKey, @NonNull Uri resolvedDerivationPath) {
            this.publicKey = publicKey;
            this.resolvedDerivationPath = resolvedDerivationPath;
        }
    }

    @NonNull
    public static RequestPublicKeyResult onRequestPublicKeyResult(
            int resultCode,
            @Nullable Intent result) throws ActionFailedException {
        if (resultCode != Activity.RESULT_OK) {
            throw new ActionFailedException("requestPublicKey failed with result=" + resultCode);
        } else if (result == null) {
            throw new ActionFailedException("requestPublicKey failed to return a result");
        }

        final byte[] publicKey = result.getByteArrayExtra(WalletContractV1.EXTRA_PUBLIC_KEY);
        if (publicKey == null) {
            throw new ActionFailedException("requestPublicKey returned a null public key");
        }

        final Uri resolvedDerivationPath = result.getData();
        if (resolvedDerivationPath == null) {
            throw new ActionFailedException("requestPublicKey returned a null derivation path");
        }

        return new RequestPublicKeyResult(publicKey, resolvedDerivationPath);
    }

    @Nullable
    public static Cursor getAllAuthorizedSeeds(
            @NonNull Context context,
            @NonNull String[] projection) {
        return context.getContentResolver().query(
                WalletContractV1.WALLET_AUTHORIZED_SEEDS_CONTENT_URI,
                projection,
                null,
                null);
    }

    // TODO: getAllAuthorizedSeeds API with filters

    @Nullable
    public static Cursor getAuthorizedSeed(
            @NonNull Context context,
            @IntRange(from=0) int authToken,
            @NonNull String[] projection) {
        return context.getContentResolver().query(
                ContentUris.withAppendedId(WalletContractV1.WALLET_AUTHORIZED_SEEDS_CONTENT_URI, authToken),
                projection,
                null,
                null);
    }

    public static void deauthorizeSeed(
            @NonNull Context context,
            @IntRange(from=0) int authToken) throws NotModifiedException {
        if (context.getContentResolver().delete(
                ContentUris.withAppendedId(WalletContractV1.WALLET_AUTHORIZED_SEEDS_CONTENT_URI, authToken),
                null) == 0) {
            throw new NotModifiedException("deauthorizeSeed for AuthToken=" + authToken);
        }
    }

    public static boolean hasUnauthorizedSeeds(
            @NonNull Context context,
            @WalletContractV1.Purpose int purpose) {
        final Cursor c = context.getContentResolver().query(
                WalletContractV1.WALLET_UNAUTHORIZED_SEEDS_CONTENT_URI,
                WalletContractV1.WALLET_UNAUTHORIZED_SEEDS_ALL_COLUMNS,
                null,
                null);
        if (!c.moveToFirst()) {
            throw new IllegalStateException("Cursor does not contain expected data");
        }
        boolean hasUnauthorizedSeeds = (c.getInt(0) != 0);
        c.close();
        return hasUnauthorizedSeeds;
    }

    @Nullable
    public static Cursor getAllAccounts(
            @NonNull Context context,
            @IntRange(from=0) int authToken,
            @NonNull String[] projection) {
        Bundle queryArgs = new Bundle();
        queryArgs.putInt(WalletContractV1.EXTRA_AUTH_TOKEN, authToken);
        return context.getContentResolver().query(
                WalletContractV1.WALLET_ACCOUNTS_CONTENT_URI,
                projection,
                queryArgs,
                null);
    }

    // TODO: getAllAccounts API with filters

    @Nullable
    public static Cursor getAccount(
            @NonNull Context context,
            @IntRange(from=0) int authToken,
            @IntRange(from=0) int id,
            @NonNull String[] projection) {
        Bundle queryArgs = new Bundle();
        queryArgs.putInt(WalletContractV1.EXTRA_AUTH_TOKEN, authToken);
        return context.getContentResolver().query(
                ContentUris.withAppendedId(WalletContractV1.WALLET_ACCOUNTS_CONTENT_URI, id),
                projection,
                queryArgs,
                null);
    }

    public static void updateAccountName(
            @NonNull Context context,
            @IntRange(from=0) int authToken,
            @IntRange(from=0) int id,
            @Nullable String name) throws NotModifiedException {
        Bundle updateArgs = new Bundle();
        updateArgs.putInt(WalletContractV1.EXTRA_AUTH_TOKEN, authToken);
        ContentValues updateValues = new ContentValues(1);
        updateValues.put(WalletContractV1.ACCOUNT_NAME, name);
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(WalletContractV1.WALLET_ACCOUNTS_CONTENT_URI, id),
                updateValues,
                updateArgs) == 0) {
            throw new NotModifiedException("updateAccountName for AuthToken=" + authToken + "/id=" + id);
        }
    }

    public static void updateAccountIsUserWallet(
            @NonNull Context context,
            @IntRange(from=0) int authToken,
            @IntRange(from=0) int id,
            boolean isUserWallet) throws NotModifiedException {
        Bundle updateArgs = new Bundle();
        updateArgs.putInt(WalletContractV1.EXTRA_AUTH_TOKEN, authToken);
        ContentValues updateValues = new ContentValues(1);
        updateValues.put(WalletContractV1.ACCOUNT_IS_USER_WALLET, isUserWallet ? 1 : 0);
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(WalletContractV1.WALLET_ACCOUNTS_CONTENT_URI, id),
                updateValues,
                updateArgs) == 0) {
            throw new NotModifiedException("updateAccountIsUserWallet for AuthToken=" + authToken + "/id=" + id);
        }
    }

    public static void updateAccountIsValid(
            @NonNull Context context,
            @IntRange(from=0) int authToken,
            @IntRange(from=0) int id,
            boolean isValid) throws NotModifiedException {
        Bundle updateArgs = new Bundle();
        updateArgs.putInt(WalletContractV1.EXTRA_AUTH_TOKEN, authToken);
        ContentValues updateValues = new ContentValues(1);
        updateValues.put(WalletContractV1.ACCOUNT_IS_VALID, isValid ? 1 : 0);
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(WalletContractV1.WALLET_ACCOUNTS_CONTENT_URI, id),
                updateValues,
                updateArgs) == 0) {
            throw new NotModifiedException("updateAccountIsValid for AuthToken=" + authToken + "/id=" + id);
        }
    }
}
