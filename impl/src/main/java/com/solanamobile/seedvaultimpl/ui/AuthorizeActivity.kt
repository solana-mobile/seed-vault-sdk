/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui

import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import com.solanamobile.seedvaultimpl.AuthorizeNavGraphDirections
import com.solanamobile.seedvaultimpl.R
import com.solanamobile.seedvaultimpl.databinding.ActivityAuthorizeBinding
import kotlinx.coroutines.launch

class AuthorizeActivity : AppCompatActivity() {
    private val viewModel: AuthorizeViewModel by viewModels()
    private lateinit var binding: ActivityAuthorizeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAuthorizeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Default result code to RESULT_CANCELED; all valid and error return paths will replace
        // this with a more appropriate result code.
        setResult(RESULT_CANCELED)

        // Mapping callingActivity (if one exists) to UID
        val packageName = callingActivity?.packageName ?: ""
        val uid = try {
            val pmUid = packageManager.getPackageUid(packageName, 0)
            Log.d(TAG, "Mapped package $packageName to UID $pmUid")
            pmUid
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Requestor package UID not found", e)
            null
        }

        viewModel.setRequest(callingActivity, uid, intent)

        // Handle viewmodel events
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event.event) {
                        AuthorizeEventType.COMPLETE -> {
                            Log.i(TAG, "Returning result=${event.resultCode}/intent=${event.data} from AuthorizeActivity")
                            setResult(event.resultCode!!, event.data)
                            finish()
                        }
                        AuthorizeEventType.START_SEED_SELECTION -> {
                            Log.d(TAG, "AuthorizeActivity; navigating to seed selection")
                            val navController = findNavController(R.id.nav_host_fragment_content_authorize)
                            navController.navigate(AuthorizeNavGraphDirections.actionSelectSeedFragment())
                        }
                        AuthorizeEventType.START_AUTHORIZATION -> {
                            Log.d(TAG, "AuthorizeActivity; navigating to authorization")
                            val navController = findNavController(R.id.nav_host_fragment_content_authorize)
                            navController.navigate(AuthorizeNavGraphDirections.actionAuthorizeFragment())
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val TAG = AuthorizeActivity::class.simpleName
    }
}