/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvault;

import static android.app.Activity.RESULT_FIRST_USER;

import android.net.Uri;
import android.provider.BaseColumns;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * The programming contract for the Seed Vault Wallet API
 *
 * @version 0.1
 * @todo set the version to 1.0 before shipping
 */
public final class WalletContractV1 {
    /**
     * Package name of the Seed Vault, which implements this Wallet API contract
     *
     * @todo set to the final Seed Vault package before shipping
     */
    public static final String PACKAGE_SEED_VAULT = "com.solanamobile.seedvaultimpl";

    /**
     * Authority of all Wallet API Android interfaces (e.g. Intent actions, content providers, etc)
     */
    public static final String AUTHORITY_WALLET = "com.solanamobile.seedvault.wallet.v1";

    /**
     * Intent action to request that a new seed be authorized for usage by the calling app. The
     * Intent should also contain an {@link #EXTRA_PURPOSE} extra, specifying the purpose for which
     * the seed should be authorized.
     * <p/>On {@link android.app.Activity#RESULT_OK}, the resulting
     * Intent will contain an {@link #EXTRA_AUTH_TOKEN} extra with the authorization token for the
     * newly authorized seed.
     * <p/>If the Activity is cancelled for any reason, {@link android.app.Activity#RESULT_CANCELED}
     * will be returned. If the specified purpose is not one of the {@code PURPOSE_*} values,
     * {@link #RESULT_INVALID_PURPOSE} will be returned. If there are no seeds available to be
     * authorized with the specified purpose, {@link #RESULT_NO_AVAILABLE_SEEDS} will be returned.
     * If the user failed to authorize the transaction, {@link #RESULT_AUTHENTICATION_FAILED} will
     * be returned.
     */
    public static final String ACTION_AUTHORIZE_SEED_ACCESS = AUTHORITY_WALLET + ".ACTION_AUTHORIZE_SEED_ACCESS";

    /**
     * Intent action to request that a transaction be signed. The Intent data should specify the
     * account derivation path as a Uri, with a scheme of either {@link #BIP32_URI_SCHEME} or
     * {@link #BIP44_URI_SCHEME}. The Intent should also contain an {@link #EXTRA_AUTH_TOKEN} extra
     * specifying the authorized seed with which to sign, and an {@link #EXTRA_TRANSACTION} extra
     * containing the payload to be signed.
     * <p/>On {@link android.app.Activity#RESULT_OK}, the resulting
     * Intent will contain an {@link #EXTRA_SIGNATURE} extra with the transaction signature payload.
     * <p/>If the Activity is cancelled for any reason, {@link android.app.Activity#RESULT_CANCELED}
     * will be returned. If the specified derivation path is not a valid BIP32 or BIP44 derivation
     * path Uri, {@link #RESULT_INVALID_DERIVATION_PATH} will be returned. If the specified auth
     * token is not valid, {@link #RESULT_INVALID_AUTH_TOKEN} will be returned. If the specified
     * transaction is not valid for signing with this auth token,
     * {@link #RESULT_INVALID_TRANSACTION} will be returned. If the user failed to authorize the
     * transaction, {@link #RESULT_AUTHENTICATION_FAILED} will be returned.
     *
     * @see Bip32DerivationPath
     * @see Bip44DerivationPath
     */
    public static final String ACTION_SIGN_TRANSACTION = AUTHORITY_WALLET + ".ACTION_SIGN_TRANSACTION";

    /**
     * Intent action to request the public key for an account. The Intent data should specify the
     * account derivation path as a Uri, with a scheme of either {@link #BIP32_URI_SCHEME} or
     * {@link #BIP44_URI_SCHEME}. The Intent should also contain an {@link #EXTRA_AUTH_TOKEN} extra
     * specifying the authorized seed from which to derive the public key. If this account is
     * present in {@link #ACCOUNTS_TABLE}, the public key will be returned without requiring
     * user authentication.
     * <p/>On {@link android.app.Activity#RESULT_OK}, the resulting Intent will contain an
     * {@link #EXTRA_PUBLIC_KEY} extra with the public key payload.
     * <p/>If the Activity is cancelled for any reason, {@link android.app.Activity#RESULT_CANCELED}
     * will be returned. If the specified derivation path is not a valid BIP32 or BIP44 derivation
     * path Uri, {@link #RESULT_INVALID_DERIVATION_PATH} will be returned. If the specified auth
     * token is not valid, {@link #RESULT_INVALID_AUTH_TOKEN} will be returned. If no key exists for
     * the specified derivation path (which is a possibility in some key derivation schemes),
     * {@link #RESULT_KEY_UNAVAILABLE} will be returned. If the user failed to authorize the
     * transaction, {@link #RESULT_AUTHENTICATION_FAILED} will be returned.
     *
     * @see Bip32DerivationPath
     * @see Bip44DerivationPath
     */
    public static final String ACTION_GET_PUBLIC_KEY = AUTHORITY_WALLET + ".ACTION_GET_PUBLIC_KEY";

    /** An unspecified error occurred in response to one of the {@code ACTION_*} actions */
    public static final int RESULT_UNSPECIFIED_ERROR = RESULT_FIRST_USER + 1000;

    /**
     * An invalid or unknown auth token was provided to {@link #ACTION_SIGN_TRANSACTION} or
     * {@link #ACTION_GET_PUBLIC_KEY}. Note that this can occur for a previously valid auth token
     * if an app is deauthorized by the user.
     */
    public static final int RESULT_INVALID_AUTH_TOKEN = RESULT_FIRST_USER + 1001;

    /**
     * The transaction payload provided to {@link #ACTION_SIGN_TRANSACTION} was not valid for the
     * signing purpose associated with the corresponding {@link #EXTRA_AUTH_TOKEN}
     */
    public static final int RESULT_INVALID_TRANSACTION = RESULT_FIRST_USER + 1002;

    /**
     * The user declined to authenticate, or failed authentication, in response to an
     * {@link #ACTION_AUTHORIZE_SEED_ACCESS}, {@link #ACTION_SIGN_TRANSACTION}, or
     * {@link #ACTION_GET_PUBLIC_KEY} action
     */
    public static final int RESULT_AUTHENTICATION_FAILED = RESULT_FIRST_USER + 1003;

    /**
     * There are no seeds available to authorize in response to
     * {@link #ACTION_AUTHORIZE_SEED_ACCESS} for the requested purpose. This may be due to either
     * there being no seeds available for that purpose, or that the app has already authorized all
     * available seeds for that purpose. Callers can check the
     * {@link #AUTHORIZED_SEEDS_TABLE} to disambiguate between these two cases.
     */
    public static final int RESULT_NO_AVAILABLE_SEEDS = RESULT_FIRST_USER + 1004;

    /**
     * The {@link #EXTRA_PURPOSE} value provided to {@link #ACTION_AUTHORIZE_SEED_ACCESS} is not a
     * known {@code PURPOSE_*}
     */
    public static final int RESULT_INVALID_PURPOSE = RESULT_FIRST_USER + 1005;

    /**
     * The Uri data provided to {@link #ACTION_SIGN_TRANSACTION} or {@link #ACTION_GET_PUBLIC_KEY}
     * is not a valid BIP32 or BIP44 Uri
     *
     * @see Bip32DerivationPath
     * @see Bip44DerivationPath
     */
    public static final int RESULT_INVALID_DERIVATION_PATH = RESULT_FIRST_USER + 1006;

    /**
     * No key is available for the BIP derivation path provided to {@link #ACTION_GET_PUBLIC_KEY}
     * (which is a possibility in some key derivation schemes)
     */
    public static final int RESULT_KEY_UNAVAILABLE = RESULT_FIRST_USER + 1007;

    /**
     * Purpose of this action, query, etc. It should be one of the {@code PURPOSE_*} constants
     * defined in this class.
     * <p/>Type: {@code int}
     */
    public static final String EXTRA_PURPOSE = "Purpose";

    /**
     * Auth token for this action, query, etc, as previously returned in response to
     * {@link #ACTION_AUTHORIZE_SEED_ACCESS}. All auth tokens for the current app can be enumerated
     * with {@link #AUTHORIZED_SEEDS_TABLE}.
     * <p/>Type: {@code int}
     */
    public static final String EXTRA_AUTH_TOKEN = "AuthToken";

    /**
     * Transaction payload for {@link #ACTION_SIGN_TRANSACTION}
     * <p/>Type: {@code byte[]}
     */
    public static final String EXTRA_TRANSACTION = "Transaction";

    /**
     * Signature payload for response to {@link #ACTION_SIGN_TRANSACTION}
     * <p/>Type: {@code byte[]}
     */
    public static final String EXTRA_SIGNATURE = "Signature";

    /**
     * Public key payload for response to {@link #ACTION_GET_PUBLIC_KEY}
     * <p/>Type: {@code byte[]}
     */
    public static final String EXTRA_PUBLIC_KEY = "PublicKey";

    /**
     * The purpose of this authorization is for signing transactions to be submitted to the Solana
     * blockchain
     */
    public static final int PURPOSE_SIGN_SOLANA_TRANSACTION = 0;

    /**
     * Uri scheme for BIP32 Uris. The BIP32 Uri format is {@code bip32:/<BIP32 derivation path>},
     * e.g. {@code bip32:/m/44'/501'/0'}.
     */
    public static final String BIP32_URI_SCHEME = "bip32";

    /**
     * Uri scheme for BIP44 Uris. The BIP44 Uri format is {@code bip44:/<BIP44 derivation levels>},
     * e.g. {@code bip44:/0'}. {@link #RESOLVE_BIP32_DERIVATION_PATH_METHOD} can be used to
     * resolve this to a BIP32 derivation path for a given auth token.
     */
    public static final String BIP44_URI_SCHEME = "bip44";

    /** The maximum supported number of levels in a {@link #BIP32_URI_SCHEME} derivation Uri */
    public static final int BIP32_URI_MAX_DEPTH = 20;

    /** The master key indicator element in a {@link #BIP32_URI_SCHEME} derivation Uri */
    public static final String BIP32_URI_MASTER_KEY_INDICATOR = "m";

    /**
     * The hardened index indicator element in a {@link #BIP32_URI_SCHEME} or
     * {@link #BIP44_URI_SCHEME} derivation Uri
     */
    public static final String BIP_URI_HARDENED_INDEX_IDENTIFIER = "'";

    /** Authority of the Seed Vault Wallet content provider */
    public static final String AUTHORITY_WALLET_PROVIDER = AUTHORITY_WALLET + ".walletprovider";

    /** Seed Vault Wallet content provider base content Uri */
    public static final Uri WALLET_PROVIDER_CONTENT_URI_BASE = Uri.parse("content://" + AUTHORITY_WALLET_PROVIDER);

    /** Wallet content provider authorized seeds table name */
    public static final String AUTHORIZED_SEEDS_TABLE = "authorizedseeds";

    /** Wallet content provider authorized seeds table content Uri */
    public static final Uri AUTHORIZED_SEEDS_CONTENT_URI = Uri.withAppendedPath(WALLET_PROVIDER_CONTENT_URI_BASE, AUTHORIZED_SEEDS_TABLE);

    /** Wallet content provider authorized seeds table MIME subtype */
    public static final String AUTHORIZED_SEEDS_MIME_SUBTYPE = "vnd." + AUTHORITY_WALLET_PROVIDER + "." + AUTHORIZED_SEEDS_TABLE;

    /** Type: {@code int} */
    public static final String AUTHORIZED_SEEDS_AUTH_TOKEN = BaseColumns._ID;

    /** Type: {@code int} (see {@code PURPOSE_*} constants) */
    public static final String AUTHORIZED_SEEDS_AUTH_PURPOSE = "AuthorizedSeeds_AuthPurpose";

    /** Type: {@code String} (may be blank) */
    public static final String AUTHORIZED_SEEDS_SEED_NAME = "AuthorizedSeeds_SeedName";

    /** All columns for the Wallet content provider authorized seeds table */
    public static final String[] AUTHORIZED_SEEDS_ALL_COLUMNS = {
            AUTHORIZED_SEEDS_AUTH_TOKEN, AUTHORIZED_SEEDS_AUTH_PURPOSE, AUTHORIZED_SEEDS_SEED_NAME};

    /** Wallet content provider unauthorized seeds table name */
    public static final String UNAUTHORIZED_SEEDS_TABLE = "unauthorizedseeds";

    /** Wallet content provider unauthorized seeds table content Uri */
    public static final Uri UNAUTHORIZED_SEEDS_CONTENT_URI = Uri.withAppendedPath(WALLET_PROVIDER_CONTENT_URI_BASE, UNAUTHORIZED_SEEDS_TABLE);

    /** Wallet content provider unauthorized seeds table MIME subtype */
    public static final String UNAUTHORIZED_SEEDS_MIME_SUBTYPE = "vnd." + AUTHORITY_WALLET_PROVIDER + "." + UNAUTHORIZED_SEEDS_TABLE;

    /** Type: {@code int} (see {@code PURPOSE_*} constants) */
    public static final String UNAUTHORIZED_SEEDS_AUTH_PURPOSE = BaseColumns._ID;

    /** Type: {@code int} (1 for true, 0 for false) */
    public static final String UNAUTHORIZED_SEEDS_HAS_UNAUTHORIZED_SEEDS = "UnauthorizedSeeds_HasUnauthorizedSeeds";

    /** All columns for the Wallet content provider unauthorized seeds table */
    public static final String[] UNAUTHORIZED_SEEDS_ALL_COLUMNS = {
            UNAUTHORIZED_SEEDS_AUTH_PURPOSE, UNAUTHORIZED_SEEDS_HAS_UNAUTHORIZED_SEEDS};

    /** Wallet content provider accounts table name */
    public static final String ACCOUNTS_TABLE = "accounts";

    /** Wallet content provider accounts table content Uri */
    public static final Uri ACCOUNTS_CONTENT_URI = Uri.withAppendedPath(WALLET_PROVIDER_CONTENT_URI_BASE, ACCOUNTS_TABLE);

    /** Wallet content provider accounts table MIME subtype */
    public static final String ACCOUNTS_MIME_SUBTYPE = "vnd." + AUTHORITY_WALLET_PROVIDER + "." + ACCOUNTS_TABLE;

    /** Type: {@code int} */
    public static final String ACCOUNTS_ACCOUNT_ID = BaseColumns._ID;

    /** Type: {@code: String} (string value of a {@link #BIP32_URI_SCHEME} Uri) */
    public static final String ACCOUNTS_BIP32_DERIVATION_PATH = "Accounts_Bip32DerivationPath";

    /** Type: {@code byte[]} */
    public static final String ACCOUNTS_PUBLIC_KEY_RAW = "Accounts_PublicKeyRaw";

    /** Type: {@code String} (Base58 encoding of {@link #ACCOUNTS_PUBLIC_KEY_RAW}) */
    public static final String ACCOUNTS_PUBLIC_KEY_BASE58 = "Accounts_PublicKeyBase58";

    /** Type {@code String} (may be blank) */
    public static final String ACCOUNTS_ACCOUNT_NAME = "Accounts_AccountName";

    /** Type: {@code int} (1 for true, 0 for false) */
    public static final String ACCOUNTS_ACCOUNT_IS_USER_WALLET = "Accounts_IsUserWallet";

    /** Type: {@code int} (1 for true, 0 for false) */
    public static final String ACCOUNTS_ACCOUNT_IS_VALID = "Accounts_IsValid";

    /** All columns for the Wallet content provider accounts table */
    public static final String[] ACCOUNTS_ALL_COLUMNS = {
            ACCOUNTS_ACCOUNT_ID, ACCOUNTS_BIP32_DERIVATION_PATH, ACCOUNTS_PUBLIC_KEY_RAW,
            ACCOUNTS_PUBLIC_KEY_BASE58, ACCOUNTS_ACCOUNT_NAME, ACCOUNTS_ACCOUNT_IS_USER_WALLET,
            ACCOUNTS_ACCOUNT_IS_VALID};

    /**
     * Wallet content provider method to resolve a {@link #BIP32_URI_SCHEME} or
     * {@link #BIP44_URI_SCHEME} derivation path Uri into a normalized form for the specified
     * purpose. The arg should be the derivation path Uri, and the extras bundle should contain an
     * {@link #EXTRA_PURPOSE} extra. The result bundle will contain an
     * {@link #RESOLVED_BIP32_DERIVATION_PATH} extra.
     */
    public static final String RESOLVE_BIP32_DERIVATION_PATH_METHOD = "ResolveBipDerivationPath";

    /**
     * The resolved {@link #BIP32_URI_SCHEME} derivation path URI
     * <p/>Type: {@code String} (string value of a {@link #BIP32_URI_SCHEME} Uri)
     * */
    public static final String RESOLVED_BIP32_DERIVATION_PATH = "ResolveBipDerivationPath_ResolvedBip32DerivationPath";

    /** Annotation for the valid account ID range */
    @Retention(RetentionPolicy.SOURCE)
    @IntRange(from=0, to=Integer.MAX_VALUE)
    public @interface AccountId {}

    /** Annotation for the valid auth token range */
    @Retention(RetentionPolicy.SOURCE)
    @IntRange(from=0, to=Integer.MAX_VALUE)
    public @interface AuthToken {}

    /** Annotation for the valid BIP index range */
    @Retention(RetentionPolicy.SOURCE)
    @IntRange(from=0, to=Integer.MAX_VALUE)
    public @interface BipIndex {}

    /** Annotation for the valid Purpose values */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({PURPOSE_SIGN_SOLANA_TRANSACTION})
    public @interface Purpose {}

    private WalletContractV1() {}
}
