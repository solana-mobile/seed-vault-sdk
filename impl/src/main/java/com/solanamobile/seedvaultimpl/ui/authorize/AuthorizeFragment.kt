/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.authorize

import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.fingerprint.FingerprintManager
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Log
import android.view.*
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
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
import com.solanamobile.seedvaultimpl.ApplicationDependencyContainer
import com.solanamobile.seedvaultimpl.R
import com.solanamobile.seedvaultimpl.SeedVaultImplApplication
import com.solanamobile.seedvaultimpl.databinding.FragmentAuthorizeBinding
import com.solanamobile.seedvaultimpl.ui.authorizeinfo.AuthorizeInfoDialogFragment
import com.solanamobile.seedvaultimpl.ui.selectseed.SelectSeedDialogFragment
import kotlinx.coroutines.*
import kotlin.coroutines.resume

class AuthorizeFragment : Fragment() {
    private lateinit var dependencyContainer: ApplicationDependencyContainer
    private val activityViewModel: com.solanamobile.seedvaultimpl.ui.AuthorizeViewModel by activityViewModels()
    private val viewModel: AuthorizeViewModel by viewModels {
        AuthorizeViewModel.provideFactory(
            dependencyContainer.seedRepository,
            activityViewModel,
            requireActivity().application
        )
    }

    private var _binding: FragmentAuthorizeBinding? = null
    private val binding get() = _binding!!

    private var supportsFingerprint: Boolean = false

    private var messageShowing: Boolean = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        dependencyContainer = (requireActivity().application as SeedVaultImplApplication).dependencyContainer

        supportsFingerprint = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    binding.labelAuthorizationType.setText(when (uiState.authorizationType) {
                        AuthorizeUiState.AuthorizationType.SEED -> R.string.label_authorize_seed
                        AuthorizeUiState.AuthorizationType.TRANSACTION -> R.string.label_authorize_transaction
                        AuthorizeUiState.AuthorizationType.MESSAGE -> R.string.label_authorize_message
                        AuthorizeUiState.AuthorizationType.PUBLIC_KEY -> R.string.label_authorize_public_key
                        null -> android.R.string.unknownName
                    })
                    binding.imageviewAppIcon.setImageDrawable(uiState.requestorAppIcon)
                    binding.textAppName.text = uiState.requestorAppName

                    val fingerprintWidgetVisibility =
                        if (uiState.allowBiometrics) View.VISIBLE else View.GONE
                    binding.dividerFingerprintBelow.visibility = fingerprintWidgetVisibility
                    binding.labelFingerprintOption.visibility = fingerprintWidgetVisibility
                    binding.imageviewFingerprintIcon.visibility = fingerprintWidgetVisibility

                    val pinWidgetVisibility = if (uiState.allowPIN) View.VISIBLE else View.GONE
                    binding.btnPin.visibility = pinWidgetVisibility

                    val authorizeSeedWidgetVisibility =
                        if (uiState.authorizationType == AuthorizeUiState.AuthorizationType.SEED) View.VISIBLE else View.GONE
                    binding.imageviewAuthorizeInfo.visibility = authorizeSeedWidgetVisibility
                    binding.labelAuthorizeInfo.visibility = authorizeSeedWidgetVisibility
                    binding.labelAuthorizeFor.visibility = authorizeSeedWidgetVisibility
                    binding.groupFor.visibility = authorizeSeedWidgetVisibility

                    binding.groupAuthorizeApp.isEnabled = (uiState.authorizationType == AuthorizeUiState.AuthorizationType.SEED)

                    binding.textSeedName.text = uiState.seedName

                    if (!messageShowing) {
                        uiState.message?.let { message ->
                            val toast =
                                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT)
                            toast.addCallback(object : Toast.Callback() {
                                override fun onToastHidden() {
                                    messageShowing = false
                                    viewModel.onMessageShown()
                                }
                            })
                            messageShowing = true
                            toast.show()
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (!supportsFingerprint) return@repeatOnLifecycle

                var fingerprintSession: Job? = null
                viewModel.uiState.collect { uiState ->
                    if (fingerprintSession != null) {
                        if (!uiState.allowBiometrics) {
                            fingerprintSession!!.cancel()
                            fingerprintSession = null
                        }
                    } else {
                        if (uiState.allowBiometrics) {
                            fingerprintSession = launch { processFingerprints() }
                        }
                    }
                }
            }
        }

        binding.groupAuthorizeApp.setOnClickListener {
            AuthorizeInfoDialogFragment().show(parentFragmentManager, AuthorizeInfoDialogFragment::class.simpleName)
        }

        binding.groupFor.setOnClickListener {
            SelectSeedDialogFragment().show(parentFragmentManager, SelectSeedDialogFragment::class.simpleName)
        }

        binding.btnCancel.setOnClickListener {
            viewModel.cancel()
        }

        binding.btnPin.setOnClickListener {
            showPinEntryDialog()
        }
    }

    private fun showPinEntryDialog() {
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
        dialog.requireViewById<AppCompatEditText>(R.id.edittext_pin)
            .setOnEditorActionListener { _, actionId, event ->
                // If either the IME is dismissed (IME_ACTION_DONE), or the ENTER key is pressed
                // on a physical keyboard (for convenience when using the emulator), treat this
                // as having clicked the positive button.
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (actionId == EditorInfo.IME_ACTION_UNSPECIFIED &&
                            event.action == KeyEvent.ACTION_DOWN &&
                            event.keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    dialog.getButton(Dialog.BUTTON_POSITIVE).callOnClick()
                    true
                } else {
                    false
                }
            }
    }

    private suspend fun processFingerprints() {
        val fpManager = requireActivity().getSystemService(Context.FINGERPRINT_SERVICE)!! as FingerprintManager
        val cancelFpSession = CancellationSignal()

        try {
            while (true) {
                suspendCancellableCoroutine { continuation ->
                    val fpCallbacks = object : FingerprintManager.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult?) {
                            super.onAuthenticationSucceeded(result)
                            viewModel.biometricAuthorizationSuccess()
                            continuation.resume(Unit)
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            viewModel.biometricsAuthorizationFailed()
                            continuation.resume(Unit)
                            animateFingerprintIconShake()
                        }

                        override fun onAuthenticationHelp(
                            helpCode: Int,
                            helpString: CharSequence?
                        ) {
                            super.onAuthenticationHelp(helpCode, helpString)
                            viewModel.biometricAuthorizationRecoverableError(helpCode, helpString)
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence?
                        ) {
                            super.onAuthenticationError(errorCode, errString)
                            if (errorCode != FingerprintManager.FINGERPRINT_ERROR_CANCELED) {
                                viewModel.biometricAuthorizationUnrecoverableError(errorCode, errString)
                                continuation.resume(Unit)
                            }
                        }
                    }

                    Log.d(TAG, "Starting a FP session")
                    fpManager.authenticate(null, cancelFpSession, 0, fpCallbacks, null)
                    // stops here and waits for continuation.resume() (or cancellation)
                }
            }
        } finally {
            Log.d(TAG, "Cancelling active FP session")
            cancelFpSession.cancel()
        }
    }

    private fun animateFingerprintIconShake() {
        val shake = AnimationUtils.loadAnimation(requireContext(), R.anim.anim_shake)
        binding.imageviewFingerprintIcon.startAnimation(shake)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        private val TAG = AuthorizeFragment::class.simpleName
    }
}