/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.authorize

import android.content.Context
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.solanamobile.seedvaultimpl.R
import com.solanamobile.seedvaultimpl.data.SeedRepository
import com.solanamobile.seedvaultimpl.databinding.FragmentAuthorizeBinding
import com.solanamobile.seedvaultimpl.ui.authorizeinfo.AuthorizeInfoDialogFragment
import com.solanamobile.seedvaultimpl.ui.selectseed.SelectSeedDialogFragment
import com.solanamobile.seedvaultimpl.usecase.BipDerivationUseCase
import com.solanamobile.seedvaultimpl.usecase.PrepopulateKnownAccountsUseCase
import com.solanamobile.seedvaultimpl.usecase.SignTransactionUseCase
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class AuthorizeFragment : Fragment() {
    private val activityViewModel: com.solanamobile.seedvaultimpl.ui.AuthorizeViewModel by activityViewModels()
    private val seedRepository: SeedRepository by inject()
    private val signTransactionUseCase: SignTransactionUseCase by inject()
    private val bipDerivationUseCase: BipDerivationUseCase by inject()
    private val prepopulateKnownAccountsUseCase: PrepopulateKnownAccountsUseCase by inject()
    private val viewModel: AuthorizeViewModel by viewModels {
        AuthorizeViewModel.provideFactory(
            seedRepository,
            activityViewModel,
            requireActivity().application,
            signTransactionUseCase = signTransactionUseCase,
            bipDerivationUseCase = bipDerivationUseCase,
            prepopulateKnownAccountsUseCase = prepopulateKnownAccountsUseCase
        )
    }

    private var _binding: FragmentAuthorizeBinding? = null
    private val binding get() = _binding!!

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    binding.labelAuthorizationType.setText(
                        when (uiState.authorizationType) {
                            AuthorizeUiState.AuthorizationType.SEED -> R.string.label_authorize_seed
                            AuthorizeUiState.AuthorizationType.TRANSACTION -> R.string.label_authorize_transaction
                            AuthorizeUiState.AuthorizationType.PUBLIC_KEY -> R.string.label_authorize_public_key
                            null -> android.R.string.unknownName
                        }
                    )
                    binding.imageviewAppIcon.setImageDrawable(uiState.requestorAppIcon)
                    binding.textAppName.text = uiState.requestorAppName

                    val fingerprintWidgetVisibility =
                        if (uiState.enableBiometrics) View.VISIBLE else View.GONE
                    binding.dividerFingerprintBelow.visibility = fingerprintWidgetVisibility
                    binding.labelFingerprintOption.visibility = fingerprintWidgetVisibility
                    binding.imageviewFingerprintIcon.visibility = fingerprintWidgetVisibility
                    binding.imageviewFingerprintErrorIcon.visibility = fingerprintWidgetVisibility

                    val pinWidgetVisibility = if (uiState.enablePIN) View.VISIBLE else View.GONE
                    binding.btnPin.visibility = pinWidgetVisibility

                    val authorizeSeedWidgetVisibility =
                        if (uiState.authorizationType == AuthorizeUiState.AuthorizationType.SEED) View.VISIBLE else View.GONE
                    binding.imageviewAuthorizeInfo.visibility = authorizeSeedWidgetVisibility
                    binding.labelAuthorizeInfo.visibility = authorizeSeedWidgetVisibility
                    binding.labelAuthorizeFor.visibility = authorizeSeedWidgetVisibility
                    binding.groupFor.visibility = authorizeSeedWidgetVisibility

                    binding.groupAuthorizeApp.isEnabled =
                        (uiState.authorizationType == AuthorizeUiState.AuthorizationType.SEED)

                    binding.textSeedName.text = uiState.seedName

                    uiState.message?.let { message ->
                        val toast = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT)
                        toast.addCallback(object : Toast.Callback() {
                            override fun onToastHidden() {
                                viewModel.onMessageShown()
                            }
                        })
                        toast.show()
                    }
                }
            }
        }

        binding.groupAuthorizeApp.setOnClickListener {
            AuthorizeInfoDialogFragment().show(
                parentFragmentManager,
                AuthorizeInfoDialogFragment::class.simpleName
            )
        }

        binding.groupFor.setOnClickListener {
            SelectSeedDialogFragment().show(
                parentFragmentManager,
                SelectSeedDialogFragment::class.simpleName
            )
        }

        binding.btnCancel.setOnClickListener {
            viewModel.cancel()
        }

        binding.btnPin.setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.label_enter_pin)
                .setView(R.layout.dialog_enter_pin)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    dialog as AlertDialog
                    val editText = dialog.requireViewById<AppCompatEditText>(R.id.edittext_pin)
                    val pin = editText.text
                    viewModel.checkEnteredPIN(pin?.toString() ?: "")
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
            dialog.show()
        }

        binding.imageviewFingerprintIcon.setOnClickListener {
            viewModel.biometricAuthorizationSuccess()
        }

        binding.imageviewFingerprintErrorIcon.setOnClickListener {
            viewModel.biometricsAuthorizationFailed()

            @Suppress("DEPRECATION")
            val vibrator = requireActivity().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))

            // TODO: shake animation
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}