/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.example.walletapidemo.ui.appdetail

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.walletapidemo.ApplicationDependencyContainer
import com.example.walletapidemo.WalletAPIDemoApplication
import com.example.walletapidemo.databinding.FragmentAppDetailBinding
import com.example.walletapidemo.ui.AuthorizeViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class AppDetailFragment : Fragment() {
    private lateinit var dependencyContainer: ApplicationDependencyContainer
    private val activityViewModel: AuthorizeViewModel by activityViewModels()

    private var _binding: FragmentAppDetailBinding? = null
    private val binding get() = _binding!! // Valid between onCreateView and onDestroyView

    override fun onAttach(context: Context) {
        super.onAttach(context)
        dependencyContainer = (requireActivity().application as WalletAPIDemoApplication).dependencyContainer
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                activityViewModel.requests.collect { request ->
                    try {
                        val activity = requireActivity()
                        val callingApplicationInfo = activity.packageManager.getApplicationInfo(request.requestor?.packageName ?: "", 0)
                        binding.imageviewAppIcon.setImageDrawable(callingApplicationInfo.loadIcon(activity.packageManager))
                        binding.labelAppName.text = callingApplicationInfo.loadLabel(activity.packageManager)
                    } catch (e: PackageManager.NameNotFoundException) {
                        Log.w(TAG, "Requestor details not found", e)
                        binding.imageviewAppIcon.setImageDrawable(null)
                        binding.labelAppName.text = ""
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val TAG = AppDetailFragment::class.simpleName
    }
}