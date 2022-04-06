/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.authorize

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.solanamobile.seedvaultimpl.ApplicationDependencyContainer
import com.solanamobile.seedvaultimpl.R
import com.solanamobile.seedvaultimpl.SeedVaultImplApplication
import com.solanamobile.seedvaultimpl.databinding.FragmentAuthorizeBinding
import com.solanamobile.seedvaultimpl.model.SeedDetails
import kotlinx.coroutines.launch

class AuthorizeFragment : Fragment() {
    private lateinit var dependencyContainer: ApplicationDependencyContainer
    private val activityViewModel: com.solanamobile.seedvaultimpl.ui.AuthorizeViewModel by activityViewModels()
    private val viewModel: AuthorizeViewModel by viewModels { AuthorizeViewModel.provideFactory(dependencyContainer.seedRepository, activityViewModel) }

    private var _binding: FragmentAuthorizeBinding? = null
    private val binding get() = _binding!!

    override fun onAttach(context: Context) {
        super.onAttach(context)
        dependencyContainer = (requireActivity().application as SeedVaultImplApplication).dependencyContainer
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthorizeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonOk.setOnClickListener {
            clearFocusOnNonFocusableInputEvent(it)
            viewModel.checkEnteredPIN()
        }

        binding.buttonSimulateBiometrics.setOnClickListener {
            clearFocusOnNonFocusableInputEvent(it)
            viewModel.biometricAuthorizationSuccess()
        }

        binding.edittextPin.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) {
                viewModel.setPIN((v as TextView).text.toString())
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    binding.labelAuthorizationType.setText(when (uiState.authorizationType) {
                        AuthorizeUiState.AuthorizationType.SEED -> R.string.label_authorize_seed
                        AuthorizeUiState.AuthorizationType.TRANSACTION -> R.string.label_authorize_transaction
                        AuthorizeUiState.AuthorizationType.PUBLIC_KEY -> R.string.label_authorize_public_key
                        null -> android.R.string.unknownName
                    })
                    binding.edittextPin.setText(uiState.pin)
                    if (uiState.showAttemptFailedHint) {
                        binding.labelErrorIncorrectPin.visibility = View.VISIBLE
                        binding.labelErrorInvalidPin.visibility = View.GONE
                    } else {
                        binding.labelErrorIncorrectPin.visibility = View.GONE
                        binding.labelErrorInvalidPin.visibility = if (uiState.pin.isEmpty() ||
                            uiState.pin.length in SeedDetails.PIN_MIN_LENGTH..SeedDetails.PIN_MAX_LENGTH) View.GONE else View.VISIBLE
                    }
                    binding.labelFingerprintOption.visibility = if (uiState.enableBiometrics) View.VISIBLE else View.GONE
                    binding.buttonSimulateBiometrics.visibility = if (uiState.enableBiometrics) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
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