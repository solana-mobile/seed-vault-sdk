/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.seeds

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import com.solanamobile.seedvaultimpl.R
import com.solanamobile.seedvaultimpl.data.SeedRepository
import com.solanamobile.seedvaultimpl.databinding.FragmentSeedsBinding
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class SeedsFragment : Fragment() {
    private val seedRepository: SeedRepository by inject()
    private val viewModel: SeedsViewModel by viewModels {
        SeedsViewModel.provideFactory(
            seedRepository
        )
    }

    private var _binding: FragmentSeedsBinding? = null
    private val binding get() = _binding!! // Only valid between onViewCreated and onViewDestroyed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSeedsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.seedsList.addItemDecoration(
            DividerItemDecoration(
                binding.seedsList.context,
                DividerItemDecoration.VERTICAL
            )
        )

        val seedListAdapter = SeedListAdapter(
            onClick = {
                findNavController().navigate(
                    SeedsFragmentDirections.actionSeedsFragmentToSeedDetailFragmentForUpdate(
                        it.id
                    )
                )
            },
            onDelete = {
                viewModel.deleteSeed(it.id)
            }
        )
        binding.seedsList.adapter = seedListAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.seedsUiState.collect {
                    seedListAdapter.submitList(it.seeds)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_seeds, menu)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.seedsUiState.collect {
                    menu.findItem(R.id.action_add).isVisible = it.canCreateSeeds
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add -> {
                findNavController().navigate(SeedsFragmentDirections.actionSeedsFragmentToSeedDetailFragmentForCreate())
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