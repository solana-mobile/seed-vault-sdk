/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.fakewallet.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.solanamobile.fakewallet.databinding.ItemAccountBinding
import com.solanamobile.fakewallet.ui.setaccountname.SetAccountNameDialogFragment

class AccountListAdapter(
    private val onSignTransaction: (Account) -> Unit,
    private val onAccountNameUpdated: (Account, String) -> Unit
) : ListAdapter<Account, AccountListAdapter.AccountViewHolder>(AccountDiffCallback) {
    class AccountViewHolder(
        private val binding: ItemAccountBinding,
        private val onSignTransaction: (Account) -> Unit,
        private val onAccountNameUpdated: (Account, String) -> Unit
    ) : ViewHolder(binding.root) {
        private var account: Account? = null

        init {
            binding.buttonSignTransaction.setOnClickListener {
                account?.let { a ->
                    onSignTransaction(a)
                }
            }
            val activity = binding.root.context as FragmentActivity
            binding.buttonEditName.setOnClickListener {
                SetAccountNameDialogFragment(
                    onSetAccountName = { name ->
                        account?.let { a ->
                            if (name != a.name) {
                                onAccountNameUpdated(a, name)
                            }
                        }
                    }
                ).show(activity.supportFragmentManager, null)
            }
        }

        fun bind(account: Account) {
            this.account = account
            binding.textviewAccountName.text = account.name
            binding.textviewPublicKey.text = account.publicKeyBase58
            binding.textviewDerivationPath.text = account.derivationPath.toString()
        }
    }

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val binding = ItemAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AccountViewHolder(binding, onSignTransaction, onAccountNameUpdated)
    }

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        val account = getItem(position)
        holder.bind(account)
    }

    override fun getItemId(position: Int): Long {
        val account = getItem(position)
        return account.id
    }
}

private object AccountDiffCallback : DiffUtil.ItemCallback<Account>() {
    override fun areItemsTheSame(oldItem: Account, newItem: Account): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Account, newItem: Account): Boolean {
        return oldItem == newItem
    }
}