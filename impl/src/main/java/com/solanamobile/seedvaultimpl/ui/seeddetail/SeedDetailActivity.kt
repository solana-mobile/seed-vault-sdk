/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.seeddetail

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvaultimpl.ApplicationDependencyContainer
import com.solanamobile.seedvaultimpl.R
import com.solanamobile.seedvaultimpl.SeedVaultImplApplication
import com.solanamobile.seedvaultimpl.databinding.ActivitySeedDetailBinding
import com.solanamobile.seedvaultimpl.model.Authorization
import com.solanamobile.seedvaultimpl.model.SeedDetails
import kotlinx.coroutines.launch

class SeedDetailActivity : AppCompatActivity() {
    private lateinit var dependencyContainer: ApplicationDependencyContainer
    private val viewModel: SeedDetailViewModel by viewModels {
        SeedDetailViewModel.provideFactory(dependencyContainer.seedRepository)
    }

    private lateinit var binding: ActivitySeedDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dependencyContainer = (application as SeedVaultImplApplication).dependencyContainer

        binding = ActivitySeedDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setResult(Activity.RESULT_CANCELED) // this will be set to RESULT_OK on success

        when (intent.action) {
            WalletContractV1.ACTION_CREATE_SEED,
            WalletContractV1.ACTION_IMPORT_SEED -> {
                val authorize = if (intent.hasExtra(WalletContractV1.EXTRA_PURPOSE)) {
                    val authorizePurposeInt = intent.getIntExtra(WalletContractV1.EXTRA_PURPOSE, -1)
                    val authorizePurpose = try {
                        Authorization.Purpose.fromWalletContractConstant(authorizePurposeInt)
                    } catch (e: IllegalArgumentException) {
                        Log.e(TAG, "Invalid purpose $authorizePurposeInt specified for Intent $intent; terminating...", e)
                        setResult(WalletContractV1.RESULT_INVALID_PURPOSE)
                        finish()
                        return
                    }

                    callingPackage?.let { packageName ->
                        PreAuthorizeSeed(
                            packageManager.getPackageUid(packageName, 0),
                            authorizePurpose
                        )
                    }
                } else null

                if (intent.action == WalletContractV1.ACTION_CREATE_SEED) {
                    viewModel.createNewSeed(authorize)
                } else {
                    viewModel.importExistingSeed(authorize)
                }
            }
            ACTION_EDIT_SEED -> {
                val seedId = intent.getLongExtra(EXTRA_SEED_ID, -1L)
                if (seedId == -1L) {
                    Log.e(TAG, "Invalid seed ID $seedId specified for Intent $intent; terminating...")
                    finish()
                    return
                }
                viewModel.editSeed(seedId)
            }
            else -> throw IllegalArgumentException("Unsupported Intent $intent")
        }

        val seedPhraseAdapter = SeedPhraseAdapter { i, s ->
            viewModel.setSeedPhraseWord(i, s)
        }

        val authorizedAppsAdapter = AuthorizedAppsAdapter { auth ->
            viewModel.deauthorize(auth.authToken)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.seedDetailUiState.collect {
                    binding.edittextSeedName.setTextKeepState(it.name)
                    binding.edittextPin.setTextKeepState(it.pin)
                    binding.labelErrorInvalidPin.visibility = if (it.pin.isEmpty() ||
                        it.pin.length in SeedDetails.PIN_MIN_LENGTH..SeedDetails.PIN_MAX_LENGTH) View.GONE else View.VISIBLE
                    binding.switchEnableBiometrics.isChecked = it.enableBiometrics
                    binding.chipgroupPhraseLength.visibility = if (it.isCreateMode) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                    val longPhrase = it.phraseLength == SeedDetailUiState.SeedPhraseLength.SEED_PHRASE_24_WORDS
                    if (longPhrase) {
                        binding.chipPhraseLength24.isChecked = true
                    } else {
                        binding.chipPhraseLength12.isChecked = true
                    }
                    seedPhraseAdapter.submitList(it.phrase.take(it.phraseLength.length).mapIndexed { i, s ->
                        SeedPhraseAdapter.Word(i, s, !it.isCreateMode)
                    })
                    if (!it.isCreateMode && it.authorizedApps.isNotEmpty()) {
                        binding.labelAuthorizedApps.visibility = View.VISIBLE
                        binding.recyclerviewAuthorizedApps.visibility = View.VISIBLE
                        authorizedAppsAdapter.submitList(it.authorizedApps)
                    } else {
                        binding.labelAuthorizedApps.visibility = View.GONE
                        binding.recyclerviewAuthorizedApps.visibility = View.GONE
                    }
                }
            }
        }

        binding.apply {
            edittextSeedName.setOnFocusChangeListener { view, hasFocus ->
                if (!hasFocus) {
                    viewModel.setName((view as TextView).text.toString())
                }
            }
            chipPhraseLength12.setOnClickListener { view ->
                clearFocusOnNonFocusableInputEvent(view)
                viewModel.setSeedPhraseLength(SeedDetailUiState.SeedPhraseLength.SEED_PHRASE_12_WORDS)
            }
            chipPhraseLength24.setOnClickListener { view ->
                clearFocusOnNonFocusableInputEvent(view)
                viewModel.setSeedPhraseLength(SeedDetailUiState.SeedPhraseLength.SEED_PHRASE_24_WORDS)
            }
            edittextPin.setOnFocusChangeListener { view, hasFocus ->
                if (!hasFocus) {
                    viewModel.setPIN((view as TextView).text.toString())
                }
            }
            switchEnableBiometrics.setOnCheckedChangeListener { view, checked ->
                clearFocusOnNonFocusableInputEvent(view)
                viewModel.enableBiometrics(checked)
            }
            recyclerviewSeedPhrase.adapter = seedPhraseAdapter
            recyclerviewAuthorizedApps.adapter = authorizedAppsAdapter
            recyclerviewAuthorizedApps.addItemDecoration(DividerItemDecoration(recyclerviewAuthorizedApps.context, DividerItemDecoration.VERTICAL))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_seed_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_ok -> {
                // An EditText may currently have the focus. Since we only save text to the ViewModel
                // when focus is lost, force the entire fragment view hierarchy to lose focus.
                binding.root.clearFocus()

                lifecycleScope.launch {
                    viewModel.saveSeed()?.let { authToken ->
                        val intent = if (authToken != -1L)
                            Intent().putExtra(WalletContractV1.EXTRA_AUTH_TOKEN, authToken)
                        else
                            null
                        setResult(Activity.RESULT_OK, intent)
                        finish()
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // When in touch mode, tapping on a non-focusable element (such as a button) will not cause the
    // EditText to lose focus. Since we only commit edited text to the ViewModel in response to a
    // loss of focus event, we won't report any edited text to the ViewModel. This method should be
    // invoked when handling input events to widgets that are not focusable in touch mode, to ensure
    // that the EditText focus is cleared (and any edited text reported to the ViewModel).
    private fun clearFocusOnNonFocusableInputEvent(v: View) {
        if (binding.root.isInTouchMode) {
            check(!v.isFocusableInTouchMode) { "Expected view $v to be non-focusable in touch mode" }
            binding.root.clearFocus()
        }
    }

    companion object {
        private val TAG = SeedDetailActivity::class.simpleName
        const val ACTION_EDIT_SEED = "com.solanamobile.seedvaultimpl.ui.seeddetail.ACTION_EDIT_SEED"
        const val EXTRA_SEED_ID = "seed_id"
    }
}