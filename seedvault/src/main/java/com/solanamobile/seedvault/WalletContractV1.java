/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvault;

import static android.app.Activity.RESULT_FIRST_USER;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.BaseColumns;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.RequiresApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * The programming contract for the Seed Vault Wallet API
 *
 * @version 0.2.6
 */
@RequiresApi(api = Build.VERSION_CODES.M) // library minSdk is 17
public final class WalletContractV1 {
    /**
     * Package name of the Seed Vault, which implements this Wallet API contract
     *
     * TODO set to the final Seed Vault package before shipping, and rev class version
     */
    public static final String PACKAGE_SEED_VAULT = "com.solanamobile.seedvaultimpl";

    /**
     * Permission name of the Seed Vault access permission. This will be a runtime permission for
     * which all consumers of the Seed Vault must request access.
     */
    public static final String PERMISSION_ACCESS_SEED_VAULT = "com.solanamobile.seedvault.ACCESS_SEED_VAULT";

    /**
     * Permission name of the Seed Vault service permission. This will be a privileged permission
     * when running with a real implementation of Seed Vault (where it must be provided as part of
     * the system, for security purposes).
     */
    public static final String PERMISSION_SEED_VAULT_IMPL = "com.solanamobile.seedvault.SEED_VAULT_IMPL";

    /**
     * Authority of all Wallet API Android interfaces (e.g. Intent actions, content providers, etc)
     */
    public static final String AUTHORITY_WALLET = "com.solanamobile.seedvault.wallet.v1";

    /**
     * Intent action to request that a new seed be authorized for usage by the calling app. The
     * Intent should also contain an {@link #EXTRA_PURPOSE} extra, specifying the purpose for which
     * the seed should be authorized.
     * <p>On {@link android.app.Activity#RESULT_OK}, the resulting
     * Intent will contain an {@link #EXTRA_AUTH_TOKEN} extra with the authorization token for the
     * newly authorized seed.</p>
     * <p>If the Activity is cancelled for any reason, {@link android.app.Activity#RESULT_CANCELED}
     * will be returned. If the specified purpose is not one of the {@code PURPOSE_*} values,
     * {@link #RESULT_INVALID_PURPOSE} will be returned. If there are no seeds available to be
     * authorized with the specified purpose, {@link #RESULT_NO_AVAILABLE_SEEDS} will be returned.
     * If the user failed to authorize the transaction, {@link #RESULT_AUTHENTICATION_FAILED} will
     * be returned.</p>
     */
    public static final String ACTION_AUTHORIZE_SEED_ACCESS = AUTHORITY_WALLET + ".ACTION_AUTHORIZE_SEED_ACCESS";

    /**
     * Intent action to request that a set of transactions be signed. The Intent should contain an
     * {@link #EXTRA_SIGNING_REQUEST} extra {@link SigningRequest}s, one per transaction to be
     * signed. Each {@link SigningRequest} may contain multiple requested signature BIP derivation
     * paths. These derivation paths should be {@link Uri}s with a scheme of either
     * {@link #BIP32_URI_SCHEME} or {@link #BIP44_URI_SCHEME}. The Intent should also contain an
     * {@link #EXTRA_AUTH_TOKEN} extra specifying the authorized seed with which to sign.
     * <p>On {@link android.app.Activity#RESULT_OK}, the resulting Intent will contain an
     * {@link #EXTRA_SIGNING_RESPONSE} extra with {@link SigningResponse}s, one per
     * {@link SigningRequest}. Each {@link SigningResponse} contains the requested signatures.</p>
     * <p>If the Activity is cancelled for any reason, {@link android.app.Activity#RESULT_CANCELED}
     * will be returned. If the specified auth token is not valid,
     * {@link #RESULT_INVALID_AUTH_TOKEN} will be returned. If any transaction is not valid for
     * signing with this auth token, {@link #RESULT_INVALID_PAYLOAD} will be returned. If any
     * requested signature derivation path is not a valid BIP32 or BIP44 derivation path Uri,
     * {@link #RESULT_INVALID_DERIVATION_PATH} will be returned. If the user failed to authorize
     * signing the set of transactions, {@link #RESULT_AUTHENTICATION_FAILED} will be returned. If
     * the number of {@link SigningRequest}s or the number of BIP derivation paths in a
     * {@link SigningRequest} is more than the quantity supported by the Seed Vault implementation,
     * {@link #RESULT_IMPLEMENTATION_LIMIT_EXCEEDED} will be returned.</p>
     *
     * @see Bip32DerivationPath
     * @see Bip44DerivationPath
     */
    public static final String ACTION_SIGN_TRANSACTION = AUTHORITY_WALLET + ".ACTION_SIGN_TRANSACTION";

    /**
     * Intent action to request that a set of messages be signed. The Intent should contain an
     * {@link #EXTRA_SIGNING_REQUEST} extra {@link SigningRequest}s, one per message to be
     * signed. Each {@link SigningRequest} may contain multiple requested signature BIP derivation
     * paths. These derivation paths should be {@link Uri}s with a scheme of either
     * {@link #BIP32_URI_SCHEME} or {@link #BIP44_URI_SCHEME}. The Intent should also contain an
     * {@link #EXTRA_AUTH_TOKEN} extra specifying the authorized seed with which to sign.
     * <p>On {@link android.app.Activity#RESULT_OK}, the resulting Intent will contain an
     * {@link #EXTRA_SIGNING_RESPONSE} extra with {@link SigningResponse}s, one per
     * {@link SigningRequest}. Each {@link SigningResponse} contains the requested signatures.</p>
     * <p>If the Activity is cancelled for any reason, {@link android.app.Activity#RESULT_CANCELED}
     * will be returned. If the specified auth token is not valid,
     * {@link #RESULT_INVALID_AUTH_TOKEN} will be returned. If any message is not valid for
     * signing with this auth token, {@link #RESULT_INVALID_PAYLOAD} will be returned. If any
     * requested signature derivation path is not a valid BIP32 or BIP44 derivation path Uri,
     * {@link #RESULT_INVALID_DERIVATION_PATH} will be returned. If the user failed to authorize
     * signing the set of messages, {@link #RESULT_AUTHENTICATION_FAILED} will be returned. If
     * the number of {@link SigningRequest}s or the number of BIP derivation paths in a
     * {@link SigningRequest} is more than the quantity supported by the Seed Vault implementation,
     * {@link #RESULT_IMPLEMENTATION_LIMIT_EXCEEDED} will be returned.</p>
     *
     * @see Bip32DerivationPath
     * @see Bip44DerivationPath
     */
    public static final String ACTION_SIGN_MESSAGE = AUTHORITY_WALLET + ".ACTION_SIGN_MESSAGE";

    /**
     * Intent action to request the public key for a set of accounts. The Intent should contain an
     * {@link #EXTRA_DERIVATION_PATH} extra with account BIP derivation path URIs, each with a
     * scheme of either {@link #BIP32_URI_SCHEME} or {@link #BIP44_URI_SCHEME}. The Intent should
     * also contain an {@link #EXTRA_AUTH_TOKEN} extra specifying the authorized seed from which to
     * derive the account public keys. If all of these accounts are present in
     * {@link #ACCOUNTS_TABLE}, the public keys will be returned without requiring user
     * authentication.
     * <p>On {@link android.app.Activity#RESULT_OK}, the resulting Intent will contain an
     * {@link #EXTRA_PUBLIC_KEY} extra with {@link PublicKeyResponse}s, one per BIP derivation path
     * URI.</p>
     * <p>If the Activity is cancelled for any reason, {@link android.app.Activity#RESULT_CANCELED}
     * will be returned. If any requested account derivation path is not a valid BIP32 or BIP44
     * derivation path Uri, {@link #RESULT_INVALID_DERIVATION_PATH} will be returned. If the
     * specified auth token is not valid, {@link #RESULT_INVALID_AUTH_TOKEN} will be returned. If
     * the user failed to authorize the transaction, {@link #RESULT_AUTHENTICATION_FAILED} will be
     * returned. If the number of account BIP derivation path URIs is more than the quantity
     * supported by the Seed Vault implementation, {@link #RESULT_IMPLEMENTATION_LIMIT_EXCEEDED}
     * will be returned.</p>
     *
     * @see Bip32DerivationPath
     * @see Bip44DerivationPath
     */
    public static final String ACTION_GET_PUBLIC_KEY = AUTHORITY_WALLET + ".ACTION_GET_PUBLIC_KEY";

    /**
     * Intent action to request that a new seed be created by the Seed Vault. The Intent may
     * optionally contain an {@link #EXTRA_PURPOSE} extra, specifying the purpose for which the new
     * seed should be authorized for the calling app. This requires that the Intent be sent with
     * {@link android.app.Activity#startActivityForResult(Intent, int)}.
     * <p>On {@link android.app.Activity#RESULT_OK}, the resulting Intent will contain an
     * {@link #EXTRA_AUTH_TOKEN} extra with the authorization token for the newly created seed.</p>
     * <p>If seed creation is cancelled for any reason, {@link android.app.Activity#RESULT_CANCELED}
     * will be returned. If the specified purpose is not one of the {@code PURPOSE_*} values,
     * {@link #RESULT_INVALID_PURPOSE} will be returned.</p>
     * <p>NOTE: this action should be used with an implicit Intent; it should not specify
     * {@link #PACKAGE_SEED_VAULT}.</p>
     */
    public static final String ACTION_CREATE_SEED = AUTHORITY_WALLET + ".ACTION_CREATE_SEED";

    /**
     * Intent action to request that an existing seed be imported by the Seed Vault. The Intent
     * may optionally contain an {@link #EXTRA_PURPOSE} extra, specifying the purpose for which the
     * seed should be authorized for the calling app. This requires that the Intent be sent with
     * {@link android.app.Activity#startActivityForResult(Intent, int)}.
     * <p>On {@link android.app.Activity#RESULT_OK}, the resulting Intent will contain an
     * {@link #EXTRA_AUTH_TOKEN} extra with the authorization token for the imported seed.</p>
     * <p>If seed creation is cancelled for any reason, {@link android.app.Activity#RESULT_CANCELED}
     * will be returned. If the specified purpose is not one of the {@code PURPOSE_*} values,
     * {@link #RESULT_INVALID_PURPOSE} will be returned.</p>
     * <p>NOTE: this action should be used with an implicit Intent; it should not specify
     * {@link #PACKAGE_SEED_VAULT}.</p>
     */
    public static final String ACTION_IMPORT_SEED = AUTHORITY_WALLET + ".ACTION_IMPORT_SEED";

    /** An unspecified error occurred in response to one of the {@code ACTION_*} actions */
    public static final int RESULT_UNSPECIFIED_ERROR = RESULT_FIRST_USER + 1000;

    /**
     * An invalid or unknown auth token was provided to {@link #ACTION_SIGN_TRANSACTION} or
     * {@link #ACTION_GET_PUBLIC_KEY}. Note that this can occur for a previously valid auth token
     * if an app is deauthorized by the user.
     */
    public static final int RESULT_INVALID_AUTH_TOKEN = RESULT_FIRST_USER + 1001;

    /**
     * A transaction payload provided to {@link #ACTION_SIGN_TRANSACTION} was not valid for the
     * signing purpose associated with the corresponding {@link #EXTRA_AUTH_TOKEN}
     */
    public static final int RESULT_INVALID_PAYLOAD = RESULT_FIRST_USER + 1002;
    public static final int RESULT_INVALID_TRANSACTION = RESULT_INVALID_PAYLOAD; // Legacy alias

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
     * The {@link #EXTRA_PURPOSE} value provided to {@link #ACTION_AUTHORIZE_SEED_ACCESS},
     * {@link #ACTION_CREATE_SEED}, or {@link #ACTION_IMPORT_SEED} is not a known {@code PURPOSE_*}
     */
    public static final int RESULT_INVALID_PURPOSE = RESULT_FIRST_USER + 1005;

    /**
     * A BIP derivation path URI provided to {@link #ACTION_SIGN_TRANSACTION} or
     * {@link #ACTION_GET_PUBLIC_KEY} is not a valid BIP32 or BIP44 Uri
     *
     * @see Bip32DerivationPath
     * @see Bip44DerivationPath
     */
    public static final int RESULT_INVALID_DERIVATION_PATH = RESULT_FIRST_USER + 1006;

    /**
     * An implementation limit was exceeded for this action. The
     * {@link #IMPLEMENTATION_LIMITS_CONTENT_URI} Wallet content provider table can be queried to
     * get the Seed Vault implementation limits.
     */
    public static final int RESULT_IMPLEMENTATION_LIMIT_EXCEEDED = RESULT_FIRST_USER + 1007;

    /**
     * Purpose of this action, query, etc. It should be one of the {@code PURPOSE_*} constants
     * defined in this class.
     * <p>Type: {@code int}</p>
     */
    public static final String EXTRA_PURPOSE = "Purpose";

    /**
     * Auth token for this action, query, etc, as previously returned in response to
     * {@link #ACTION_AUTHORIZE_SEED_ACCESS}. All auth tokens for the current app can be enumerated
     * with {@link #AUTHORIZED_SEEDS_TABLE}.
     * <p>Type: {@code long}</p>
     */
    public static final String EXTRA_AUTH_TOKEN = "AuthToken";

    /**
     * A set of {@link SigningRequest}s for {@link #ACTION_SIGN_TRANSACTION}
     * <p>Type: {@link java.util.ArrayList}{@code <}{@link SigningRequest}{@code >}</p>
     */
    public static final String EXTRA_SIGNING_REQUEST = "SigningRequest";

    /**
     * A set of {@link SigningResponse}s for response to {@link #ACTION_SIGN_TRANSACTION}
     * <p>Type: {@link java.util.ArrayList}{@code <}{@link SigningResponse}{@code >}</p>
     */
    public static final String EXTRA_SIGNING_RESPONSE = "SigningResponse";

    /**
     * A set of BIP derivation path URIs for {@link #ACTION_GET_PUBLIC_KEY}
     * <p>Type: {@link java.util.ArrayList}{@code <}{@link Uri}{@code >}</p>
     */
    public static final String EXTRA_DERIVATION_PATH = "DerivationPath";

    /**
     * A set of account public keys for response to {@link #ACTION_GET_PUBLIC_KEY}
     * <p>Type: {@link java.util.ArrayList}{@code <}{@link PublicKeyResponse}{@code >}</p>
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

    /**
     * The minimum number of {@link SigningRequest}s per {@link #ACTION_SIGN_TRANSACTION} that all
     * Seed Vault implementations must support
     */
    public static final int MIN_SUPPORTED_SIGNING_REQUESTS = 3;

    /**
     * The minimum number of requested signatures per {@link SigningRequest} that all Seed Vault
     * implementations must support
     */
    public static final int MIN_SUPPORTED_REQUESTED_SIGNATURES = 3;

    /**
     * The minimum number of BIP derivation paths per {@link #ACTION_GET_PUBLIC_KEY} that all Seed
     * Vault implementations must support
     */
    public static final int MIN_SUPPORTED_REQUESTED_PUBLIC_KEYS = 10;

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

    /** Type: {@code long} */
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

    /** Type: {@code short} (1 for true, 0 for false) */
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

    /** Type: {@code long} */
    public static final String ACCOUNTS_ACCOUNT_ID = BaseColumns._ID;

    /** Type: {@code String} (string value of a {@link #BIP32_URI_SCHEME} Uri) */
    public static final String ACCOUNTS_BIP32_DERIVATION_PATH = "Accounts_Bip32DerivationPath";

    /** Type: {@code byte[]} */
    public static final String ACCOUNTS_PUBLIC_KEY_RAW = "Accounts_PublicKeyRaw";

    /**
     * Type: {@code String} (Purpose-specific encoding of {@link #ACCOUNTS_PUBLIC_KEY_RAW};
     * for e.g., Base58-encoding for {@link #PURPOSE_SIGN_SOLANA_TRANSACTION})
     */
    public static final String ACCOUNTS_PUBLIC_KEY_ENCODED = "Accounts_PublicKeyEncoded";

    /** Type {@code String} (may be blank) */
    public static final String ACCOUNTS_ACCOUNT_NAME = "Accounts_AccountName";

    /** Type: {@code short} (1 for true, 0 for false) */
    public static final String ACCOUNTS_ACCOUNT_IS_USER_WALLET = "Accounts_IsUserWallet";

    /** Type: {@code short} (1 for true, 0 for false) */
    public static final String ACCOUNTS_ACCOUNT_IS_VALID = "Accounts_IsValid";

    /** All columns for the Wallet content provider accounts table */
    public static final String[] ACCOUNTS_ALL_COLUMNS = {
            ACCOUNTS_ACCOUNT_ID, ACCOUNTS_BIP32_DERIVATION_PATH, ACCOUNTS_PUBLIC_KEY_RAW,
            ACCOUNTS_PUBLIC_KEY_ENCODED, ACCOUNTS_ACCOUNT_NAME, ACCOUNTS_ACCOUNT_IS_USER_WALLET,
            ACCOUNTS_ACCOUNT_IS_VALID};

    /** Wallet content provider implementation limits table name */
    public static final String IMPLEMENTATION_LIMITS_TABLE = "implementationlimits";

    /** Wallet content provider implementation limits table content Uri */
    public static final Uri IMPLEMENTATION_LIMITS_CONTENT_URI = Uri.withAppendedPath(WALLET_PROVIDER_CONTENT_URI_BASE, IMPLEMENTATION_LIMITS_TABLE);

    /** Wallet content provider implementation limits table MIME subtype */
    public static final String IMPLEMENTATION_LIMITS_MIME_SUBTYPE = "vnd." + AUTHORITY_WALLET_PROVIDER + "." + IMPLEMENTATION_LIMITS_TABLE;

    /** Type: {@code int} (see {@code PURPOSE_*} constants) */
    public static final String IMPLEMENTATION_LIMITS_AUTH_PURPOSE = BaseColumns._ID;

    /** Type: {@code short} */
    public static final String IMPLEMENTATION_LIMITS_MAX_SIGNING_REQUESTS = "MaxSigningRequests";

    /** Type: {@code short} */
    public static final String IMPLEMENTATION_LIMITS_MAX_REQUESTED_SIGNATURES = "MaxRequestedSignatures";

    /** Type: {@code short} */
    public static final String IMPLEMENTATION_LIMITS_MAX_REQUESTED_PUBLIC_KEYS = "MaxRequestedPublicKeys";

    /** All columns for the Wallet content provider implementation limits table */
    public static final String[] IMPLEMENTATION_LIMITS_ALL_COLUMNS = {
            IMPLEMENTATION_LIMITS_AUTH_PURPOSE, IMPLEMENTATION_LIMITS_MAX_SIGNING_REQUESTS,
            IMPLEMENTATION_LIMITS_MAX_REQUESTED_SIGNATURES,
            IMPLEMENTATION_LIMITS_MAX_REQUESTED_PUBLIC_KEYS};

    /**
     * Wallet content provider method to resolve a {@link #BIP32_URI_SCHEME} or
     * {@link #BIP44_URI_SCHEME} derivation path Uri into a normalized form for the specified
     * purpose. The arg should be the derivation path Uri, and the extras bundle should contain an
     * {@link #EXTRA_PURPOSE} extra. The result bundle will contain an
     * {@link #EXTRA_RESOLVED_BIP32_DERIVATION_PATH} extra.
     */
    public static final String RESOLVE_BIP32_DERIVATION_PATH_METHOD = "ResolveBipDerivationPath";

    /**
     * The resolved {@link #BIP32_URI_SCHEME} derivation path URI
     * <p>Type: {@code Uri} (a {@link #BIP32_URI_SCHEME} Uri)</p>
     * */
    public static final String EXTRA_RESOLVED_BIP32_DERIVATION_PATH = "ResolveBipDerivationPath_ResolvedBip32DerivationPath";

    /** Annotation for the valid account ID range */
    @Retention(RetentionPolicy.SOURCE)
    @IntRange(from=0, to=Long.MAX_VALUE)
    public @interface AccountId {}

    /** Annotation for the valid auth token range */
    @Retention(RetentionPolicy.SOURCE)
    @IntRange(from=0, to=Long.MAX_VALUE)
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
