/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.seeddetail

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import com.solanamobile.seedvaultimpl.R
import com.solanamobile.seedvaultimpl.data.SeedRepository
import com.solanamobile.seedvaultimpl.databinding.FragmentSeedDetailBinding
import com.solanamobile.seedvaultimpl.model.SeedDetails
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class SeedDetailFragment : Fragment() {
    private val seedRepository: SeedRepository by inject()
    private val viewModel: SeedDetailViewModel by viewModels {
        SeedDetailViewModel.provideFactory(
            seedRepository,
            args.seedId
        )
    }

    private val args: SeedDetailFragmentArgs by navArgs()

    private var _binding: FragmentSeedDetailBinding? = null
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
        _binding = FragmentSeedDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val seedPhraseAdapter = SeedPhraseAdapter { i, s ->
            viewModel.setSeedPhraseWord(i, s)
        }

        val authorizedAppsAdapter = AuthorizedAppsAdapter { auth ->
            viewModel.deauthorize(auth.authToken)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.seedDetailUiState.collect {
                    binding.edittextSeedName.setTextKeepState(it.name)
                    binding.edittextPin.setTextKeepState(it.pin)
                    binding.labelErrorInvalidPin.visibility = if (it.pin.isEmpty() ||
                        it.pin.length in SeedDetails.PIN_MIN_LENGTH..SeedDetails.PIN_MAX_LENGTH
                    ) View.GONE else View.VISIBLE
                    binding.switchEnableBiometrics.isChecked = it.enableBiometrics
                    binding.chipgroupPhraseLength.visibility = if (it.isCreateMode) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                    val longPhrase =
                        it.phraseLength == SeedDetailUiState.SeedPhraseLength.SEED_PHRASE_24_WORDS
                    if (longPhrase) {
                        binding.chipPhraseLength24.isChecked = true
                    } else {
                        binding.chipPhraseLength12.isChecked = true
                    }
                    seedPhraseAdapter.submitList(
                        it.phrase.take(it.phraseLength.length).mapIndexed { i, s ->
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
            recyclerviewAuthorizedApps.addItemDecoration(
                DividerItemDecoration(
                    recyclerviewAuthorizedApps.context,
                    DividerItemDecoration.VERTICAL
                )
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_seed_detail, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_ok -> {
                // An EditText may currently have the focus. Since we only save text to the ViewModel
                // when focus is lost, force the entire fragment view hierarchy to lose focus.
                binding.root.clearFocus()
                if (viewModel.saveSeed()) {
                    findNavController().popBackStack()
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
}