package com.solanamobile.fakewallet.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.solanamobile.seedvault.WalletContractV1

class PermissionGauntletActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (checkSelfPermission(WalletContractV1.PERMISSION_ACCESS_SEED_VAULT) == PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        requestPermissions(arrayOf(WalletContractV1.PERMISSION_ACCESS_SEED_VAULT), 0)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (grantResults[0]) {
            PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "Access Seed Vault permission granted")
                startActivity(Intent(this, MainActivity::class.java))
            }
            else -> {
                Log.e(TAG, "Access Seed Vault permission not granted")
            }
        }
        finish()
    }

    companion object {
        private val TAG = PermissionGauntletActivity::class.simpleName
    }
}