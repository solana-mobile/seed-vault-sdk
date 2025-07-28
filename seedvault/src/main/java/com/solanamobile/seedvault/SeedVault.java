/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault;

import static android.content.pm.PermissionInfo.PROTECTION_FLAG_PRIVILEGED;
import static android.content.pm.PermissionInfo.PROTECTION_MASK_BASE;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * Programming interfaces for interacting with the Seed Vault (non-Wallet interfaces)
 *
 * @version 0.3.2
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
                    PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

        if (allowSimulated) {
            return true;
        }

        if (pi.permissions != null) {
            for (PermissionInfo permission : pi.permissions) {
                if (WalletContractV1.PERMISSION_SEED_VAULT_IMPL.equals(permission.name)) {
                    return hasPrivilegedProtectionFlag(permission);
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

    private static boolean hasPrivilegedProtectionFlag(@NonNull PermissionInfo permission) {
        int privilegedFlag = permission.protectionLevel
                & ~PROTECTION_MASK_BASE
                & PROTECTION_FLAG_PRIVILEGED;
        return privilegedFlag != 0;
    }
}
