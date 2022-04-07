/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvault;

import static android.app.Activity.RESULT_FIRST_USER;

import android.net.Uri;
import android.provider.BaseColumns;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class WalletContractV1 {
    // TODO: set to the final Wallet API implementation before shipping
    public static final String PACKAGE_SEED_VAULT = "com.solanamobile.seedvaultimpl";

    public static final String AUTHORITY_WALLET = "com.solanamobile.seedvault.wallet.v1";

    public static final int RESULT_UNSPECIFIED_ERROR = RESULT_FIRST_USER + 1000;
    public static final int RESULT_UNKNOWN_ACTION = RESULT_FIRST_USER + 1001;
    public static final int RESULT_INVALID_AUTH_TOKEN = RESULT_FIRST_USER + 1002;
    public static final int RESULT_INVALID_TRANSACTION = RESULT_FIRST_USER + 1003;
    public static final int RESULT_AUTHENTICATION_FAILED = RESULT_FIRST_USER + 1004;
    public static final int RESULT_NO_AVAILABLE_SEEDS = RESULT_FIRST_USER + 1005;
    public static final int RESULT_INVALID_PURPOSE = RESULT_FIRST_USER + 1006;
    public static final int RESULT_INVALID_DERIVATION_PATH = RESULT_FIRST_USER + 1007;
    public static final int RESULT_KEY_UNAVAILABLE = RESULT_FIRST_USER + 1008;

    public static final String ACTION_AUTHORIZE_SEED_ACCESS = AUTHORITY_WALLET + ".ACTION_AUTHORIZE_SEED_ACCESS";
    public static final String ACTION_SIGN_TRANSACTION = AUTHORITY_WALLET + ".ACTION_SIGN_TRANSACTION";
    public static final String ACTION_GET_PUBLIC_KEY = AUTHORITY_WALLET + ".ACTION_GET_PUBLIC_KEY";

    public static final String EXTRA_PURPOSE = "Purpose";
    public static final String EXTRA_AUTH_TOKEN = "AuthToken";
    public static final String EXTRA_TRANSACTION = "Transaction";
    public static final String EXTRA_SIGNATURE = "Signature";
    public static final String EXTRA_PUBLIC_KEY = "PublicKey";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({PURPOSE_SIGN_SOLANA_TRANSACTION})
    public @interface Purpose {}
    public static final int PURPOSE_SIGN_SOLANA_TRANSACTION = 0;

    public static final int AUTH_TOKEN_INVALID = -1;

    public static final String BIP32_URI_SCHEME = "bip32";
    public static final String BIP44_URI_SCHEME = "bip44";
    public static final int BIP32_URI_MAX_DEPTH = 20;
    public static final String BIP32_URI_MASTER_KEY_INDICATOR = "m";
    public static final String BIP32_URI_HARDENED_INDEX_IDENTIFIER = "'";

    public static final String AUTHORITY_WALLET_PROVIDER = AUTHORITY_WALLET + ".walletprovider";
    public static final Uri WALLET_PROVIDER_CONTENT_URI_BASE = Uri.parse("content://" + AUTHORITY_WALLET_PROVIDER);

    public static final String WALLET_AUTHORIZED_SEEDS_TABLE = "authorizedseeds";
    public static final Uri WALLET_AUTHORIZED_SEEDS_CONTENT_URI = Uri.withAppendedPath(WALLET_PROVIDER_CONTENT_URI_BASE, WALLET_AUTHORIZED_SEEDS_TABLE);
    public static final String WALLET_AUTHORIZED_SEEDS_MIME_SUBTYPE = "vnd." + AUTHORITY_WALLET_PROVIDER + "." + WALLET_AUTHORIZED_SEEDS_TABLE;
    // BaseColumns._ID is the auth token for each authorized seed
    public static final String AUTH_PURPOSE = "AuthorizedSeeds_AuthPurpose";
    public static final String SEED_NAME = "AuthorizedSeeds_SeedName";
    public static final String[] WALLET_AUTHORIZED_SEEDS_ALL_COLUMNS = {
            BaseColumns._ID, AUTH_PURPOSE, SEED_NAME };

    public static final String WALLET_UNAUTHORIZED_SEEDS_TABLE = "unauthorizedseeds";
    public static final Uri WALLET_UNAUTHORIZED_SEEDS_CONTENT_URI = Uri.withAppendedPath(WALLET_PROVIDER_CONTENT_URI_BASE, WALLET_UNAUTHORIZED_SEEDS_TABLE);
    public static final String WALLET_UNAUTHORIZED_SEEDS_MIME_SUBTYPE = "vnd." + AUTHORITY_WALLET_PROVIDER + "." + WALLET_UNAUTHORIZED_SEEDS_TABLE;
    public static final String HAS_UNAUTHORIZED_SEEDS = "UnauthorizedSeeds_HasUnauthorizedSeeds";
    public static final String[] WALLET_UNAUTHORIZED_SEEDS_ALL_COLUMNS = {
            HAS_UNAUTHORIZED_SEEDS };

    public static final String WALLET_ACCOUNTS_TABLE = "accounts";
    public static final Uri WALLET_ACCOUNTS_CONTENT_URI = Uri.withAppendedPath(WALLET_PROVIDER_CONTENT_URI_BASE, WALLET_ACCOUNTS_TABLE);
    public static final String WALLET_ACCOUNTS_MIME_SUBTYPE = "vnd." + AUTHORITY_WALLET_PROVIDER + "." + WALLET_ACCOUNTS_TABLE;
    // BaseColumns._ID is the unique account identifier
    public static final String BIP32_DERIVATION_PATH = "Accounts_Bip32DerivationPath";
    public static final String PUBLIC_KEY_RAW = "Accounts_PublicKeyRaw";
    public static final String PUBLIC_KEY_BASE58 = "Accounts_PublicKeyBase58";
    public static final String ACCOUNT_NAME = "Accounts_AccountName";
    public static final String ACCOUNT_IS_USER_WALLET = "Accounts_IsUserWallet";
    public static final String ACCOUNT_IS_VALID = "Accounts_IsValid";
    public static final String[] WALLET_ACCOUNTS_ALL_COLUMNS = {
            BaseColumns._ID, BIP32_DERIVATION_PATH, PUBLIC_KEY_RAW, PUBLIC_KEY_BASE58,
            ACCOUNT_NAME, ACCOUNT_IS_USER_WALLET, ACCOUNT_IS_VALID };

    private WalletContractV1() {}
}
