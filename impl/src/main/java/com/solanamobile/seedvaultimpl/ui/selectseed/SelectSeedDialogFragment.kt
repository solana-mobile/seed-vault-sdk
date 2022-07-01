/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.selectseed

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvaultimpl.R
import com.solanamobile.seedvaultimpl.data.SeedRepository
import com.solanamobile.seedvaultimpl.databinding.FragmentSelectSeedBinding
import com.solanamobile.seedvaultimpl.ui.AuthorizeViewModel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class SelectSeedDialogFragment : DialogFragment() {
    private val activityViewModel: AuthorizeViewModel by activityViewModels()
    private val seedRepository: SeedRepository by inject()
    private val viewModel: SelectSeedViewModel by viewModels {
        SelectSeedViewModel.provideFactory(
            seedRepository,
            activityViewModel
        )
    }

    private var _binding: FragmentSelectSeedBinding? = null
    private val binding get() = _binding!!

    init {
        setStyle(STYLE_NO_FRAME, R.style.Theme_SeedVaultImpl_FullScreenDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSelectSeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val seedListAdapter = SeedListAdapter(onSelect = { seedId ->
            viewModel.setSelectedSeed(seedId)
        })
        binding.recyclerviewSeeds.adapter = seedListAdapter
        binding.recyclerviewSeeds.addItemDecoration(
            DividerItemDecoration(binding.recyclerviewSeeds.context, DividerItemDecoration.VERTICAL)
        )

        binding.buttonBack.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    viewModel.selectSeedForAuthorization()
                    dismiss()
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "Failed authorizing selected seed for UID", e)
                    activityViewModel.completeAuthorizationWithError(WalletContractV1.RESULT_UNSPECIFIED_ERROR)
                }
            }
        }


        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectSeedUiState.collect { uiState ->
                    seedListAdapter.submitList(uiState.seeds.map { seed ->
                        SeedWithSelectionState(seed, uiState.selectedSeedId == seed.id)
                    })
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        private val TAG = SelectSeedDialogFragment::class.simpleName
    }
}