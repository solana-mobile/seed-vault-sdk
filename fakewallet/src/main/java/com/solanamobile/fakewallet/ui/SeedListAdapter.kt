/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.fakewallet.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.solanamobile.fakewallet.databinding.ItemSeedBinding
import com.solanamobile.fakewallet.usecase.SeedPurposeUseCase

class SeedListAdapter(
    private val onSignTransaction: (Seed, Account) -> Unit,
    private val onAccountNameUpdated: (Seed, Account, String) -> Unit,
    private val onDeauthorizeSeed: (Seed) -> Unit,
    private val onRequestPublicKeyForM1000HAndM1001H: (Seed) -> Unit,
    private val onSignTwoTransactionsWithTwoSignatures: (Seed) -> Unit
) : ListAdapter<Seed, SeedListAdapter.SeedViewHolder>(SeedDiffCallback) {
    class SeedViewHolder(
        private val binding: ItemSeedBinding,
        private val onSignTransaction: (Seed, Account) -> Unit,
        private val onAccountNameUpdated: (Seed, Account, String) -> Unit,
        private val onDeauthorizeSeed: (Seed) -> Unit,
        private val onRequestPublicKeyForM1000HAndM1001H: (Seed) -> Unit,
        private val onSignTwoTransactionsWithTwoSignatures: (Seed) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        private var seed: Seed? = null
        private val adapter: AccountListAdapter = AccountListAdapter(
            onSignTransaction = { account ->
                seed?.let { s ->
                    onSignTransaction(s, account)
                }
            },
            onAccountNameUpdated = { account, name ->
                seed?.let { s ->
                    onAccountNameUpdated(s, account, name)
                }
            }
        )

        init {
            binding.buttonDeauthorizeSeed.setOnClickListener {
                seed?.let { s ->
                    onDeauthorizeSeed(s)
                }
            }
            binding.buttonRequestTwoPublicKeys.setOnClickListener {
                seed?.let { s ->
                    onRequestPublicKeyForM1000HAndM1001H(s)
                }
            }
            binding.buttonSignTwoTransactionsWithTwoSignatures.setOnClickListener {
                seed?.let { s ->
                    onSignTwoTransactionsWithTwoSignatures(s)
                }
            }
            binding.recyclerviewAccounts.adapter = adapter
        }

        fun bind(seed: Seed) {
            this.seed = seed
            binding.textviewPurpose.text = SeedPurposeUseCase(seed.purpose)
            binding.textviewSeedName.text = seed.name
            adapter.submitList(seed.accounts)
        }
    }

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeedViewHolder {
        val binding = ItemSeedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SeedViewHolder(binding, onSignTransaction, onAccountNameUpdated, onDeauthorizeSeed,
            onRequestPublicKeyForM1000HAndM1001H, onSignTwoTransactionsWithTwoSignatures)
    }

    override fun onBindViewHolder(holder: SeedViewHolder, position: Int) {
        val seed = getItem(position)
        holder.bind(seed)
    }

    override fun getItemId(position: Int): Long {
        val seed = getItem(position)
        return seed.authToken
    }
}

private object SeedDiffCallback : DiffUtil.ItemCallback<Seed>() {
    override fun areItemsTheSame(oldItem: Seed, newItem: Seed): Boolean {
        return oldItem.authToken == newItem.authToken
    }

    override fun areContentsTheSame(oldItem: Seed, newItem: Seed): Boolean {
        return oldItem == newItem
    }
}