/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.seeds

import android.content.Intent
import android.os.Bundle
import android.view.*
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
import com.solanamobile.seedvaultimpl.databinding.ActivitySeedsBinding
import com.solanamobile.seedvaultimpl.ui.seeddetail.SeedDetailActivity
import kotlinx.coroutines.launch

class SeedsActivity : AppCompatActivity() {
    private lateinit var dependencyContainer: ApplicationDependencyContainer
    private val viewModel: SeedsViewModel by viewModels { SeedsViewModel.provideFactory(dependencyContainer.seedRepository) }

    private lateinit var binding: ActivitySeedsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dependencyContainer = (application as SeedVaultImplApplication).dependencyContainer

        binding = ActivitySeedsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.seedsList.addItemDecoration(DividerItemDecoration(binding.seedsList.context, DividerItemDecoration.VERTICAL))

        val seedListAdapter = SeedListAdapter(
            onClick = {
                val intent = Intent(SeedDetailActivity.ACTION_EDIT_SEED).setClass(
                    this,
                    SeedDetailActivity::class.java
                ).putExtra(SeedDetailActivity.EXTRA_SEED_ID, it.id)
                startActivity(intent)
            },
            onDelete = {
                viewModel.deleteSeed(it.id)
            }
        )
        binding.seedsList.adapter = seedListAdapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.seedsUiState.collect {
                    seedListAdapter.submitList(it.seeds)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_seeds, menu)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.seedsUiState.collect {
                    menu.findItem(R.id.action_add).isVisible = it.canCreateSeeds
                }
            }
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add -> {
                val intent = Intent(WalletContractV1.ACTION_IMPORT_SEED).setClass(
                    this,
                    SeedDetailActivity::class.java
                ).putExtra(
                    WalletContractV1.EXTRA_PURPOSE,
                    WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION
                )
                startActivity(intent)
                true
            }
            R.id.action_clear -> {
                viewModel.deleteAllSeeds()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}