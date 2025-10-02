/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault;

import static android.content.pm.PermissionInfo.PROTECTION_FLAG_PRIVILEGED;
import static android.content.pm.PermissionInfo.PROTECTION_MASK_BASE;
import static android.content.pm.PermissionInfo.PROTECTION_SIGNATURE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.List;

/**
 * Programming interfaces for interacting with the Seed Vault (non-Wallet interfaces)
 *
 * @version 0.4.0
 */
@RequiresApi(api = Build.VERSION_CODES.M) // library minSdk is 17
public class SeedVault {
    private SeedVault() {}

    /**
     * Implementations of Seed Vault that support the
     * {@link WalletContractV1#PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED} permissions must necessarily
     * use &lt;intent-filter&gt;s with varying order attributes to properly check for the two
     * possible permissions. Pre-API 33 (Android 13), ResolverActivity would end up stripping
     * Parcelable extras from {@link android.content.Intent}s where the parceled type is unknown to
     * the system before forwarding to the correct handling {@link android.app.Activity}. As such,
     * privileged Seed Vault implementations are only possible starting with Android 13.
     */
    public static final int MIN_API_FOR_SEED_VAULT_PRIVILEGED = Build.VERSION_CODES.TIRAMISU;

    /**
     * The different levels of access that an app can be granted to the Seed Vault
     */
    public enum AccessType {
        /**
         * The app has no access to the Seed Vault (i.e. it holds neither the
         * {@link WalletContractV1#PERMISSION_ACCESS_SEED_VAULT} nor the
         * {@link WalletContractV1#PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED} permissions
         */
        NONE,

        /**
         * The app has normal access to the Seed Vault (i.e. it holds the
         * {@link WalletContractV1#PERMISSION_ACCESS_SEED_VAULT} permission)
         */
        STANDARD,

        /**
         * The app has privileged access to the Seed Vault (i.e. it holds the
         * {@link WalletContractV1#PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED} permission)
         */
        PRIVILEGED;

        /**
         * Test whether this {@link AccessType} grants some level of access to the Seed Vault
         * @return true for {@link #STANDARD} or {@link #PRIVILEGED}, else false
         */
        public boolean isGranted() {
            return (this == STANDARD || this == PRIVILEGED);
        }
    }

    /**
     * Check whether a secure implementation of the Seed Vault is available on this device
     * @param context the {@link Context} in which to perform this request
     * @return true if a secure implementation of the Seed Vault is available, else false
     */
    public static boolean isAvailable(@NonNull Context context) {
        return isAvailable(context, false);
    }

    /**
     * Check whether an implementation of the Seed Vault is available on this device
     * @param context the {@link Context} in which to perform this request
     * @param allowSimulated if true, a simulated implementation of the Seed Vault is permissible.
     *      Otherwise, the implementation must be secure.
     * @return true if a secure implementation of the Seed Vault is available, else false
     */
    public static boolean isAvailable(@NonNull Context context, boolean allowSimulated) {
        final PackageInfo pi;
        try {
            pi = context.getPackageManager().getPackageInfo(WalletContractV1.PACKAGE_SEED_VAULT,
                    PackageManager.GET_PERMISSIONS | PackageManager.GET_SIGNATURES);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

        if (allowSimulated) {
            return true;
        }

        if (pi.permissions != null) {
            for (PermissionInfo permission : pi.permissions) {
                if (WalletContractV1.PERMISSION_SEED_VAULT_IMPL.equals(permission.name)) {
                    // If the permission does not use signature protection, it can't be considered
                    // secure. Note that this is only a precondition - additional checks are
                    // required.
                    if (!usesSignatureProtection(permission)) {
                        return false;
                    }

                    if (hasPrivilegedProtectionFlag(permission)) {
                        return true;
                    }

                    // Workaround - not all devices always set the PROTECTION_FLAG_PRIVILEGED bit
                    // correctly. For these devices, check that the app providing the permission has
                    // the same signing certificate as the 'android' package. These are both system
                    // apps, and so there should only be one signing certificate for each.
                    final PackageInfo androidPi;
                    try {
                        androidPi = context.getPackageManager().getPackageInfo("android",
                                PackageManager.GET_SIGNATURES);
                    } catch (PackageManager.NameNotFoundException e) {
                        return false;
                    }

                    assert androidPi.applicationInfo != null; // should never be null
                    assert androidPi.signatures != null; // should never be null
                    assert pi.signatures != null; // should never be null

                    return androidPi.applicationInfo.uid == Process.SYSTEM_UID
                            && androidPi.signatures[0].equals(pi.signatures[0]);
                }
            }
        }

        return false;
    }

    /**
     * Check what level of access (if any) this application has to the Seed Vault
     * @param context the {@link Context} in which to perform this request
     * @return one of the {@link AccessType} values corresponding to the level of access to Seed
     *      Vault that has been granted
     */
    @NonNull
    public static AccessType getAccessType(@NonNull Context context) {
        if (context.checkSelfPermission(WalletContractV1.PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED) ==
                PackageManager.PERMISSION_GRANTED) {
            return AccessType.PRIVILEGED;
        } else if (context.checkSelfPermission(WalletContractV1.PERMISSION_ACCESS_SEED_VAULT) ==
                PackageManager.PERMISSION_GRANTED) {
            return AccessType.STANDARD;
        } else {
            return AccessType.NONE;
        }
    }

    private static boolean usesSignatureProtection(@NonNull PermissionInfo permission) {
        return (permission.protectionLevel & PROTECTION_MASK_BASE) == PROTECTION_SIGNATURE;
    }

    private static boolean hasPrivilegedProtectionFlag(@NonNull PermissionInfo permission) {
        int privilegedFlag = permission.protectionLevel
                & ~PROTECTION_MASK_BASE
                & PROTECTION_FLAG_PRIVILEGED;
        return privilegedFlag != 0;
    }

    /**
     * Attempts to resolve the target component (package and class) of an Intent to the appropriate
     * Seed Vault handler. This is needed because the Android Intent disambiguation rules don't
     * always take required permissions into account. In particular, if the system selects a
     * persistent preferred Activity to handle an Intent, then it won't later verify if a calling
     * app has the appropriate permission to invoke the persistent preferred Activity. This function
     * will attempt to resolve the highest priority handler for a given Intent, taking required
     * Seed Vault permissions into account.
     * <p>NOTE: for {@link Intent}s not scoped to a particular package with
     * {@link Intent#setPackage(String)}, this method relies on the <code>priority</code> field of
     * <code>intent-filter</code> being set correctly. A Seed Vault implementation on a device
     * should set <code>priority</code> > 0 to ensure that it will always be selected as the handler
     * for Seed Vault methods.</p>
     * @param context the {@link Context} which will send this intent
     * @param intent the {@link Intent} which will be sent to Seed Vault. It may be an implicit or
     *               explicit Intent, but it should not have a component set (i.e.
     *               {@link Intent#getComponent()} should return null). After invoking this method,
     *               the component will be set for this Intent.
     * @throws IllegalArgumentException if intent already has a component set
     * @throws IllegalStateException if there is no Seed Vault implementation capable of receiving
     *      this intent, or if this app does not hold one of the
     *      {@link WalletContractV1#PERMISSION_ACCESS_SEED_VAULT} or
     *      {@link WalletContractV1#PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED} permissions.
     */
    @SuppressLint("InlinedApi") // allow usage of PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED even when API level is too low - it's safe to do so, as the detail never leaves this function
    public static void resolveComponentForIntent(
            @NonNull Context context,
            @NonNull Intent intent
    ) {
        if (intent.getComponent() != null) {
            throw new IllegalArgumentException("component should not be set prior to resolution");
        }

        List<ResolveInfo> resolved = context.getPackageManager().queryIntentActivities(intent, 0);

        final SeedVault.AccessType accessType = SeedVault.getAccessType(context);
        final String heldPermission;
        switch (accessType) {
            case STANDARD:
                heldPermission = WalletContractV1.PERMISSION_ACCESS_SEED_VAULT;
                break;
            case PRIVILEGED:
                heldPermission = WalletContractV1.PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED;
                break;
            case NONE:
            default:
                throw new IllegalStateException("No access to Seed Vault; callers must hold either the " +
                        WalletContractV1.PERMISSION_ACCESS_SEED_VAULT + " or " +
                        WalletContractV1.PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED + " permission");
        }

        // resolved will be ordered first by priority, and then by order, of the <intent-filter />
        // which is matched. Importantly, permissions are not taken into account by this ordering.
        // Search the list for the first target that requires the appropriate permission.
        // Note: A proper Seed Vault implementation should always set the `priority` field to > 0,
        // to ensure that the system implementation takes precedence over an installed apps that
        // attempt to also handle Seed Vault intents.
        for (final ResolveInfo ri : resolved) {
            if (heldPermission.equals(ri.activityInfo.permission)) {
                intent.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
                return;
            }
        }

        // Whoops, we didn't find a valid target activity. Since we already verified we have a Seed
        // Vault implementation (by way of being granted one of the Seed Vault permissions) this is
        // an error.
        throw new IllegalStateException("No target activity found for " + intent.getAction());
    }
}
