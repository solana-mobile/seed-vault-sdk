package com.solanamobile.seedvault;

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
 * @version 0.2.4
 */
@RequiresApi(api = Build.VERSION_CODES.M) // library minSdk is 17
public class SeedVault {
    private SeedVault() {}

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

        for (PermissionInfo permission : pi.permissions) {
            if (WalletContractV1.PERMISSION_SEED_VAULT_IMPL.equals(permission.name) &&
                    ((permission.flags & PermissionInfo.PROTECTION_FLAG_PRIVILEGED) != 0)) {
                return true;
            }
        }

        return false;
    }
}
