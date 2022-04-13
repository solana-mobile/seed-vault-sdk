/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvault;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Programming interfaces for {@link WalletContractV1}
 *
 * @version 0.1
 * @todo bump version to 1.0 prior to shipping
 */
public final class Wallet {
    /**
     * Thrown by methods that modify the state of the Seed Vault Wallet if the target of the
     * modification (such as an account) does not exist.
     */
    public static final class NotModifiedException extends Exception {
        public NotModifiedException(String message) {
            super(message);
        }
    }

    /**
     * Thrown by methods to indicate a failure of a Seed Vault Wallet action
     */
    public static final class ActionFailedException extends Exception {
        public ActionFailedException(String message) {
            super(message);
        }
    }

    private Wallet() {}

    /**
     * Request authorization of a new seed for the specified purpose. The returned {@link Intent}
     * should be used with {@link Activity#startActivityForResult(Intent, int)}, and the result (as
     * returned to {@link Activity#onActivityResult(int, int, Intent)}) should be used as parameters
     * to {@link #onAuthorizeSeedResult(int, Intent)}.
     * @param purpose the purpose for which the seed will be used. One of the
     *      {@code WalletContractV1.PURPOSE_*} constants.
     * @return an {@link Intent} suitable for usage with
     *      {@link Activity#startActivityForResult(Intent, int)}
     */
    @NonNull
    public static Intent authorizeSeed(
            @WalletContractV1.Purpose int purpose) {
        return new Intent()
                .setPackage(WalletContractV1.PACKAGE_SEED_VAULT)
                .setAction(WalletContractV1.ACTION_AUTHORIZE_SEED_ACCESS)
                .putExtra(WalletContractV1.EXTRA_PURPOSE, purpose);
    }

    /**
     * Process the results of {@link Activity#onActivityResult(int, int, Intent)} (in response to an
     * invocation of {@link #authorizeSeed(int)})
     * @param resultCode resultCode from {@code onActivityResult}
     * @param result intent from {@code onActivityResult}
     * @return the auth token for the newly authorized seed
     * @throws ActionFailedException if the authorization failed
     */
    @WalletContractV1.AuthToken
    public static long onAuthorizeSeedResult(
            int resultCode,
            @Nullable Intent result) throws ActionFailedException {
        if (resultCode != Activity.RESULT_OK) {
            throw new ActionFailedException("authorizeSeed failed with result=" + resultCode);
        } else if (result == null) {
            throw new ActionFailedException("authorizeSeed failed to return a result");
        }

        final long authToken = result.getLongExtra(WalletContractV1.EXTRA_AUTH_TOKEN, -1);
        if (authToken == -1) {
            throw new ActionFailedException("authorizeSeed returned an invalid AuthToken");
        }

        return authToken;
    }

    /**
     * Request that the provided transaction be signed (with whatever method is appropriate for the
     * purpose originally specified for this auth token). The returned {@link Intent} should be used
     * with {@link Activity#startActivityForResult(Intent, int)}, and the result (as returned to
     * {@link Activity#onActivityResult(int, int, Intent)}) should be used as parameters to
     * {@link #onSignTransactionResult(int, Intent)}.
     * @param authToken the auth token for the seed with which to perform transaction signing
     * @param derivationPath a {@link BipDerivationPath} representing the account with which to
     *      sign this transaction
     * @param transaction a {@code byte[]} containing the transaction to be signed
     * @return an {@link Intent} suitable for usage with
     *      {@link Activity#startActivityForResult(Intent, int)}
     */
    @NonNull
    public static Intent signTransaction(
            @WalletContractV1.AuthToken long authToken,
            @NonNull Uri derivationPath,
            @NonNull byte[] transaction) {
        return new Intent()
                .setPackage(WalletContractV1.PACKAGE_SEED_VAULT)
                .setAction(WalletContractV1.ACTION_SIGN_TRANSACTION)
                .setData(derivationPath)
                .putExtra(WalletContractV1.EXTRA_AUTH_TOKEN, authToken)
                .putExtra(WalletContractV1.EXTRA_TRANSACTION, transaction);
    }

    /**
     * An immutable data class containing the results of {@link #signTransaction(int, Uri, byte[])}
     */
    public static final class SignTransactionResult {
        /** The transaction signature */
        @NonNull public final byte[] signature;

        /**
         * The fully resolved derivation path representing the account with which signing was
         * performed. This will include, e.g. mandatory hardening or translation from BIP44 to
         * BIP32.
         */
        @NonNull public final Uri resolvedDerivationPath;

        private SignTransactionResult(@NonNull byte[] signature, @NonNull Uri resolvedDerivationPath) {
            this.signature = signature;
            this.resolvedDerivationPath = resolvedDerivationPath;
        }
    }

    /**
     * Process the results of {@link Activity#onActivityResult(int, int, Intent)} (in response to an
     * invocation of {@link #signTransaction(int, Uri, byte[])})
     * @param resultCode resultCode from {@code onActivityResult}
     * @param result intent from {@code onActivityResult}
     * @return a {@link SignTransactionResult} with the transaction signature
     * @throws ActionFailedException if the transaction signing failed
     */
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

    /**
     * Request the public key for a given {@link BipDerivationPath} of a seed. The returned
     * {@link Intent} should be used with {@link Activity#startActivityForResult(Intent, int)}, and
     * the result (as returned to {@link Activity#onActivityResult(int, int, Intent)}) should be
     * used as parameters to {@link #onRequestPublicKeyResult(int, Intent)}. If the public key is
     * not present in the results of {@link #getAccounts(Context, int, String[])}, the user will be
     * asked to authorize access to this public key.
     * @param authToken the auth token for the seed with which to request a public key
     * @param derivationPath a {@link BipDerivationPath} representing the account from which to
     *      request the public key
     * @return an {@link Intent} suitable for usage with
     *      {@link Activity#startActivityForResult(Intent, int)}
     */
    @NonNull
    public static Intent requestPublicKey(
            @WalletContractV1.AuthToken long authToken,
            @NonNull Uri derivationPath) {
        return new Intent()
                .setPackage(WalletContractV1.PACKAGE_SEED_VAULT)
                .setAction(WalletContractV1.ACTION_GET_PUBLIC_KEY)
                .setData(derivationPath)
                .putExtra(WalletContractV1.EXTRA_AUTH_TOKEN, authToken);
    }

    /**
     * An immutable data class containing the results of {@link #requestPublicKey(int, Uri)}
     */
    public static final class RequestPublicKeyResult {
        /** The public key */
        @NonNull public final byte[] publicKey;

        /**
         * The fully resolved derivation path representing the account for which the public key was
         * retrieved. This will include, e.g. mandatory hardening or translation from BIP44 to
         * BIP32.
         */
        @NonNull public final Uri resolvedDerivationPath;

        private RequestPublicKeyResult(@NonNull byte[] publicKey, @NonNull Uri resolvedDerivationPath) {
            this.publicKey = publicKey;
            this.resolvedDerivationPath = resolvedDerivationPath;
        }
    }

    /**
     * Process the results of {@link Activity#onActivityResult(int, int, Intent)} (in response to an
     * invocation of {@link #requestPublicKey(int, Uri)})
     * @param resultCode resultCode from {@code onActivityResult}
     * @param result intent from {@code onActivityResult}
     * @return a {@link RequestPublicKeyResult} with the public key
     * @throws ActionFailedException if the public key request failed
     */
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

    /**
     * Request a {@link Cursor} containing the authorized seeds for the current app. The projection
     * should be a subset of the columns in {@link WalletContractV1#AUTHORIZED_SEEDS_ALL_COLUMNS}.
     * @param context the {@link Context} in which to perform this request
     * @param projection the set of columns to be present in the returned {@link Cursor}
     * @return a {@link Cursor}
     */
    @Nullable
    public static Cursor getAuthorizedSeeds(
            @NonNull Context context,
            @NonNull String[] projection) {
        return getAuthorizedSeeds(context, projection, null, null);
    }

    /**
     * Request a {@link Cursor} containing the authorized seeds for the current app which match the
     * provided query. The projection should be a subset of the columns in
     * {@link WalletContractV1#AUTHORIZED_SEEDS_ALL_COLUMNS}.
     * @param context the {@link Context} in which to perform this request
     * @param projection the set of columns to be present in the returned {@link Cursor}
     * @param filterOnColumn the column from {@link WalletContractV1#AUTHORIZED_SEEDS_ALL_COLUMNS}
     *      on which to filter
     * @param value the value of filterOnColumn which all returned rows must match
     * @return a {@link Cursor}
     * @throws IllegalArgumentException if filterOnColumn is not a column in
     *      {@link WalletContractV1#AUTHORIZED_SEEDS_ALL_COLUMNS}, or if value cannot be interpreted
     *      as an appropriate type to match against filterOnColumn values.
     */
    @Nullable
    public static Cursor getAuthorizedSeeds(
            @NonNull Context context,
            @NonNull String[] projection,
            @Nullable String filterOnColumn,
            @Nullable Object value) {
        final Bundle queryArgs;
        if (filterOnColumn != null) {
            if (value == null) {
                throw new IllegalArgumentException("value cannot be null when filterOnColumn is specified");
            } else if (!stringArrayContains(
                    WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS, filterOnColumn)) {
                throw new IllegalArgumentException("Column '" + filterOnColumn + "' is not a valid column");
            }
            queryArgs = new Bundle();
            queryArgs.putString(
                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                    filterOnColumn + "=?");
            queryArgs.putStringArray(
                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    new String[] { value.toString() });
        } else {
            queryArgs = null;
        }
        return context.getContentResolver().query(
                WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI,
                projection,
                queryArgs,
                null);
    }

    /**
     * Request a {@link Cursor} containing the specified authorized seed for the current app. The
     * projection should be a subset of the columns in
     * {@link WalletContractV1#AUTHORIZED_SEEDS_ALL_COLUMNS}. If the specified auth token is not
     * found, the returned {@link Cursor} will be empty.
     * @param context the {@link Context} in which to perform this request
     * @param authToken the auth token of the authorized seed to return in the {@link Cursor}
     * @param projection the set of columns to be present in the returned {@link Cursor}
     * @return a {@link Cursor}
     */
    @Nullable
    public static Cursor getAuthorizedSeed(
            @NonNull Context context,
            @WalletContractV1.AuthToken long authToken,
            @NonNull String[] projection) {
        return context.getContentResolver().query(
                ContentUris.withAppendedId(WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI, authToken),
                projection,
                null,
                null);
    }

    /**
     * Deauthorize the specified seed for the current app
     * @param context the {@link Context} in which to perform this request
     * @param authToken the auth token of the seed to deauthorize
     * @throws NotModifiedException if the seed was not authorized for this app
     */
    public static void deauthorizeSeed(
            @NonNull Context context,
            @WalletContractV1.AuthToken long authToken) throws NotModifiedException {
        if (context.getContentResolver().delete(
                ContentUris.withAppendedId(WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI, authToken),
                null) == 0) {
            throw new NotModifiedException("deauthorizeSeed for AuthToken=" + authToken);
        }
    }

    /**
     * Request a {@link Cursor} containing whether or not there are seeds remaining to be authorized
     * for each purpose for the current app. The projection should be a subset of the columns in
     * {@link WalletContractV1#UNAUTHORIZED_SEEDS_ALL_COLUMNS}.
     * @param context the {@link Context} in which to perform this request
     * @param projection the set of columns to be present in the returned {@link Cursor}
     * @return a {@link Cursor}
     */
    @Nullable
    public static Cursor getUnauthorizedSeeds(
            @NonNull Context context,
            @NonNull String[] projection) {
        return getUnauthorizedSeeds(context, projection, null, null);
    }

    /**
     * Request a {@link Cursor} containing whether or not there are seeds remainining to be
     * authorized for each purpose for the current app which match the provided query. The
     * projection should be a subset of the columns in
     * {@link WalletContractV1#UNAUTHORIZED_SEEDS_ALL_COLUMNS}.
     * @param context the {@link Context} in which to perform this request
     * @param projection the set of columns to be present in the returned {@link Cursor}
     * @param filterOnColumn the column from {@link WalletContractV1#UNAUTHORIZED_SEEDS_ALL_COLUMNS}
     *      on which to filter
     * @param value the value of filterOnColumn which all returned rows must match
     * @return a {@link Cursor}
     * @throws IllegalArgumentException if filterOnColumn is not a column in
     *      {@link WalletContractV1#UNAUTHORIZED_SEEDS_ALL_COLUMNS}, or if value cannot be
     *      interpreted as an appropriate type to match against filterOnColumn values.
     */
    @Nullable
    public static Cursor getUnauthorizedSeeds(
            @NonNull Context context,
            @NonNull String[] projection,
            @Nullable String filterOnColumn,
            @Nullable Object value) {
        final Bundle queryArgs;
        if (filterOnColumn != null) {
            if (value == null) {
                throw new IllegalArgumentException("value cannot be null when filterOnColumn is specified");
            } else if (!stringArrayContains(
                    WalletContractV1.UNAUTHORIZED_SEEDS_ALL_COLUMNS, filterOnColumn)) {
                throw new IllegalArgumentException("Column '" + filterOnColumn + "' is not a valid column");
            }
            queryArgs = new Bundle();
            queryArgs.putString(
                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                    filterOnColumn + "=?");
            queryArgs.putStringArray(
                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    new String[] { value.toString() });
        } else {
            queryArgs = null;
        }
        return context.getContentResolver().query(
                WalletContractV1.UNAUTHORIZED_SEEDS_CONTENT_URI,
                projection,
                queryArgs,
                null);
    }

    /**
     * Test whether there are any unauthorized seeds with the specified purpose available for the
     * current app
     * @param context the {@link Context} in which to perform this request
     * @param purpose the {@code WalletContractV1.PURPOSE_*} purpose
     * @return true if there are unauthorized seeds for purpose, else false
     * @throws IllegalArgumentException if purpose is not a known {@code WalletContractV1.PURPOSE_*}
     *      value
     */
    public static boolean hasUnauthorizedSeedsForPurpose(
            @NonNull Context context,
            @WalletContractV1.Purpose int purpose) {
        final Cursor c = context.getContentResolver().query(
                ContentUris.withAppendedId(
                        WalletContractV1.UNAUTHORIZED_SEEDS_CONTENT_URI, purpose),
                WalletContractV1.UNAUTHORIZED_SEEDS_ALL_COLUMNS,
                null,
                null);
        if (!c.moveToFirst()) {
            throw new IllegalStateException("Cursor does not contain expected data");
        }
        boolean hasUnauthorizedSeeds = (c.getShort(1) != 0);
        c.close();
        return hasUnauthorizedSeeds;
    }

    /**
     * Request a {@link Cursor} containing account metadata for known accounts for the specified
     * auth token. The projection should be a subset of the columns in
     * {@link WalletContractV1#ACCOUNTS_ALL_COLUMNS}.
     * @param context the {@link Context} in which to perform this request
     * @param authToken the auth token for which to retrieve account metadata
     * @param projection the set of columns to be present in the returned {@link Cursor}
     * @return a {@link Cursor}
     * @throws IllegalArgumentException if auth token is not valid for this app
     */
    @Nullable
    public static Cursor getAccounts(
            @NonNull Context context,
            @WalletContractV1.AuthToken long authToken,
            @NonNull String[] projection) {
        return getAccounts(context, authToken, projection, null, null);
    }

    /**
     * Request a {@link Cursor} containing account metadata for known accounts for the specified
     * auth token which match the provided query. The projection should be a subset of the columns
     * in {@link WalletContractV1#ACCOUNTS_ALL_COLUMNS}.
     * @param context the {@link Context} in which to perform this request
     * @param authToken the auth token for which to retrieve account metadata
     * @param projection the set of columns to be present in the returned {@link Cursor}
     * @param filterOnColumn the column from {@link WalletContractV1#ACCOUNTS_ALL_COLUMNS} on which
     *      to filter
     * @param value the value of filterOnColumn which all returned rows must match
     * @return a {@link Cursor}
     * @throws IllegalArgumentException if auth token is not valid for this app, if filterOnColumn
     *      is not a column in {@link WalletContractV1#ACCOUNTS_ALL_COLUMNS}, or if value cannot be
     *      interpreted as an appropriate type to match against filterOnColumn values.
     */
    @Nullable
    public static Cursor getAccounts(
            @NonNull Context context,
            @WalletContractV1.AuthToken long authToken,
            @NonNull String[] projection,
            @Nullable String filterOnColumn,
            @Nullable Object value) {
        final Bundle queryArgs = new Bundle();
        queryArgs.putLong(WalletContractV1.EXTRA_AUTH_TOKEN, authToken);
        if (filterOnColumn != null) {
            if (value == null) {
                throw new IllegalArgumentException("value cannot be null when filterOnColumn is specified");
            } else if (!stringArrayContains(
                    WalletContractV1.ACCOUNTS_ALL_COLUMNS, filterOnColumn)) {
                throw new IllegalArgumentException("Column '" + filterOnColumn + "' is not a valid column");
            }
            queryArgs.putString(
                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                    filterOnColumn + "=?");
            queryArgs.putStringArray(
                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    new String[] { value.toString() });
        }
        return context.getContentResolver().query(
                WalletContractV1.ACCOUNTS_CONTENT_URI,
                projection,
                queryArgs,
                null);
    }

    /**
     * Request a {@link Cursor} containing account metadata for the specified known account for the
     * given auth token. The projection should be a subset of the columns in
     * {@link WalletContractV1#ACCOUNTS_ALL_COLUMNS}.
     * @param context the {@link Context} in which to perform this request
     * @param authToken the auth token for which to retrieve account metadata
     * @param id the account ID for which to retrieve account metadata
     * @param projection the set of columns to be present in the returned {@link Cursor}
     * @return a {@link Cursor}
     * @throws IllegalArgumentException if auth token is not valid for this app
     */
    @Nullable
    public static Cursor getAccount(
            @NonNull Context context,
            @WalletContractV1.AuthToken long authToken,
            @WalletContractV1.AccountId long id,
            @NonNull String[] projection) {
        Bundle queryArgs = new Bundle();
        queryArgs.putLong(WalletContractV1.EXTRA_AUTH_TOKEN, authToken);
        return context.getContentResolver().query(
                ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, id),
                projection,
                queryArgs,
                null);
    }

    /**
     * Update the account name for the specified known account for the given auth token
     * @param context the {@link Context} in which to perform this request
     * @param authToken the auth token for which to update account metadata
     * @param id the account ID to update
     * @param name the new name for the account. If null or blank, the account name will be cleared.
     * @throws IllegalArgumentException if auth token is not valid for this app
     * @throws NotModifiedException if ID does not represent a known account
     */
    public static void updateAccountName(
            @NonNull Context context,
            @WalletContractV1.AuthToken long authToken,
            @WalletContractV1.AccountId long id,
            @Nullable String name) throws NotModifiedException {
        Bundle updateArgs = new Bundle();
        updateArgs.putLong(WalletContractV1.EXTRA_AUTH_TOKEN, authToken);
        ContentValues updateValues = new ContentValues(1);
        updateValues.put(WalletContractV1.ACCOUNTS_ACCOUNT_NAME, name);
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, id),
                updateValues,
                updateArgs) == 0) {
            throw new NotModifiedException("updateAccountName for AuthToken=" + authToken + "/id=" + id);
        }
    }

    /**
     * Update the user wallet flag for the specified known account for the given auth token. This
     * flag is intended to be set to true by any wallet that discovers this account is used (or was
     * previously used) by the user to receive tokens or send transactions. Wallet apps can use this
     * flag as a hint for whether or not a wallet should be displayed to a user.
     * @param context the {@link Context} in which to perform this request
     * @param authToken the auth token for which to update account metadata
     * @param id the account ID to update
     * @param isUserWallet the new value for the user wallet flag
     * @throws IllegalArgumentException if auth token is not valid for this app
     * @throws NotModifiedException if ID does not represent a known account
     */
    public static void updateAccountIsUserWallet(
            @NonNull Context context,
            @WalletContractV1.AuthToken long authToken,
            @WalletContractV1.AccountId long id,
            boolean isUserWallet) throws NotModifiedException {
        Bundle updateArgs = new Bundle();
        updateArgs.putLong(WalletContractV1.EXTRA_AUTH_TOKEN, authToken);
        ContentValues updateValues = new ContentValues(1);
        updateValues.put(WalletContractV1.ACCOUNTS_ACCOUNT_IS_USER_WALLET, isUserWallet ? (short)1 : (short)0);
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, id),
                updateValues,
                updateArgs) == 0) {
            throw new NotModifiedException("updateAccountIsUserWallet for AuthToken=" + authToken + "/id=" + id);
        }
    }

    /**
     * Update the valid flag for the specified known account for the given auth token. This flag is
     * intended to be set to true by any wallet that wants to force this account to be displayed to
     * a user, regardless of the value of {@link WalletContractV1#ACCOUNTS_ACCOUNT_IS_USER_WALLET}.
     * Wallet apps can use this flag as a hint for whether or not a wallet should be displayed to a
     * user.
     * @param context the {@link Context} in which to perform this request
     * @param authToken the auth token for which to update account metadata
     * @param id the account ID to update
     * @param isValid the new value for the valid flag
     * @throws IllegalArgumentException if auth token is not valid for this app
     * @throws NotModifiedException if ID does not represent a known account
     */
    public static void updateAccountIsValid(
            @NonNull Context context,
            @WalletContractV1.AuthToken long authToken,
            @WalletContractV1.AccountId long id,
            boolean isValid) throws NotModifiedException {
        Bundle updateArgs = new Bundle();
        updateArgs.putLong(WalletContractV1.EXTRA_AUTH_TOKEN, authToken);
        ContentValues updateValues = new ContentValues(1);
        updateValues.put(WalletContractV1.ACCOUNTS_ACCOUNT_IS_VALID, isValid ? (short)1 : (short)0);
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, id),
                updateValues,
                updateArgs) == 0) {
            throw new NotModifiedException("updateAccountIsValid for AuthToken=" + authToken + "/id=" + id);
        }
    }

    /**
     * Resolve the provided BIP derivation path {@link Uri} with scheme
     * {@link WalletContractV1#BIP32_URI_SCHEME} or {@link WalletContractV1#BIP44_URI_SCHEME} and
     * the provided {@code WalletContractV1.PURPOSE_*} purpose to a BIP32 derivation path.
     * This is used to apply purpose-specific properties (such as mandatory hardening) and to
     * translate from BIP44 to BIP32 derivation paths. Entries in the
     * {@link WalletContractV1#ACCOUNTS_TABLE} table will always be resolved BIP32 derivation paths.
     * @param context the {@link Context} in which to perform this request
     * @param derivationPath a BIP32 or BIP44 {@link Uri} to resolve to a BIP32 derivation path for
     *      the specified purpose
     * @param purpose the {@code WalletContractV1.PURPOSE_*} purpose for which to resolve
     *      derivationPath
     * @return a BIP32 derivation path {@link Uri}
     * @throws UnsupportedOperationException on failure to resolve the provided derivationPath
     */
    @NonNull
    public static Uri resolveDerivationPath(
            @NonNull Context context,
            @NonNull Uri derivationPath,
            @WalletContractV1.Purpose int purpose) {
        Bundle callArgs = new Bundle();
        callArgs.putInt(WalletContractV1.EXTRA_PURPOSE, purpose);
        Bundle result = context.getContentResolver().call(
                WalletContractV1.AUTHORITY_WALLET_PROVIDER,
                WalletContractV1.RESOLVE_BIP32_DERIVATION_PATH_METHOD,
                derivationPath.toString(),
                callArgs);
        if (result == null) {
            throw new UnsupportedOperationException("Failed to invoke method '" +
                    WalletContractV1.RESOLVE_BIP32_DERIVATION_PATH_METHOD + "'");
        }
        Uri resolvedDerivationPath = Uri.parse(result.getString(
                WalletContractV1.RESOLVED_BIP32_DERIVATION_PATH));
        if (resolvedDerivationPath == null) {
            throw new UnsupportedOperationException("Failed to resolve BIP32 derivation path");
        }
        return resolvedDerivationPath;
    }

    private static boolean stringArrayContains(@NonNull String[] array, String value) {
        for (String s : array) {
            if (s.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
