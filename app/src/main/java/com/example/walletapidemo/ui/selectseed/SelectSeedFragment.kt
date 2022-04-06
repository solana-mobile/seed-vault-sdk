/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.example.walletapidemo.ui.selectseed

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.seedvault.WalletContractV1
import com.example.walletapidemo.ApplicationDependencyContainer
import com.example.walletapidemo.WalletAPIDemoApplication
import com.example.walletapidemo.databinding.FragmentSelectSeedBinding
import com.example.walletapidemo.ui.AuthorizeViewModel
import kotlinx.coroutines.launch

class SelectSeedFragment : Fragment() {
    private lateinit var dependencyContainer: ApplicationDependencyContainer
    private val activityViewModel: AuthorizeViewModel by activityViewModels()
    private val viewModel: SelectSeedViewModel by viewModels { SelectSeedViewModel.provideFactory(dependencyContainer.seedRepository, activityViewModel) }

    private var _binding: FragmentSelectSeedBinding? = null
    private val binding get() = _binding!!

    override fun onAttach(context: Context) {
        super.onAttach(context)
        dependencyContainer = (requireActivity().application as WalletAPIDemoApplication).dependencyContainer
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

        binding.buttonOk.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    viewModel.selectSeedForAuthorization()
                    findNavController().navigate(SelectSeedFragmentDirections.actionSelectSeedFragmentToAuthorizeFragment())
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "Failed authorizing selected seed for UID", e)
                    activityViewModel.completeAuthorizationWithError(WalletContractV1.RESULT_UNSPECIFIED_ERROR)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectSeedUiState.collect { uiState ->
                    binding.buttonOk.isEnabled = (uiState.selectedSeedId != null)
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
        private val TAG = SelectSeedFragment::class.simpleName
    }
}