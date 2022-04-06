/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.example.fakewallet.ui.setaccountname

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.example.fakewallet.R
import com.example.fakewallet.databinding.DialogSetAccountNameBinding

class SetAccountNameDialogFragment(
    private val onSetAccountName: (String) -> Unit
) : DialogFragment() {
    private lateinit var binding: DialogSetAccountNameBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        val builder = AlertDialog.Builder(activity)

        binding = DialogSetAccountNameBinding.inflate(activity.layoutInflater)
        return builder
            .setView(binding.root)
            .setTitle(R.string.title_set_account_name)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                val name = binding.edittextAccountName.text.toString()
                onSetAccountName(name)
                dialog.dismiss()
            }.setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }.show()
    }
}