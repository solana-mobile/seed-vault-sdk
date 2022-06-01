/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.authorizeinfo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.solanamobile.seedvaultimpl.R
import com.solanamobile.seedvaultimpl.databinding.FragmentAuthorizeInfoBinding

class AuthorizeInfoDialogFragment : DialogFragment() {
    private lateinit var viewBinding: FragmentAuthorizeInfoBinding

    init {
        setStyle(STYLE_NO_FRAME, R.style.Theme_SeedVaultImpl_FullScreenDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewBinding = FragmentAuthorizeInfoBinding.inflate(inflater, container, false)
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.buttonBack.setOnClickListener {
            dismiss()
        }
    }
}