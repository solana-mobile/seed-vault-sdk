/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.fakewallet.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.solanamobile.fakewallet.R
import com.solanamobile.fakewallet.databinding.ItemSeedBinding
import com.solanamobile.fakewallet.usecase.SeedPurposeUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class SeedListAdapter(
    private val lifecycleScope: CoroutineScope,
    private val implementationLimits: Flow<ImplementationLimits>,
    private val onSignTransaction: (Seed, Account) -> Unit,
    private val onSignMessage: (Seed, Account) -> Unit,
    private val onAccountNameUpdated: (Seed, Account, String) -> Unit,
    private val onDeauthorizeSeed: (Seed) -> Unit,
    private val onRequestPublicKeys: (Seed) -> Unit,
    private val onSignMaxTransactionsWithMaxSignatures: (Seed) -> Unit,
    private val onSignMaxMessagesWithMaxSignatures: (Seed) -> Unit
) : ListAdapter<Seed, SeedListAdapter.SeedViewHolder>(SeedDiffCallback) {
    data class ImplementationLimits(
        val maxSigningRequests: Int,
        val maxRequestedSignatures: Int,
        val firstRequestedPublicKey: String,
        val lastRequestedPublicKey: String
    )

    inner class SeedViewHolder(
        private val binding: ItemSeedBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private var seed: Seed? = null
        private val adapter: AccountListAdapter = AccountListAdapter(
            onSignTransaction = { account ->
                seed?.let { s ->
                    onSignTransaction(s, account)
                }
            },
            onSignMessage = { account ->
                seed?.let { s ->
                    onSignMessage(s, account)
                }
            },
            onAccountNameUpdated = { account, name ->
                seed?.let { s ->
                    onAccountNameUpdated(s, account, name)
                }
            }
        )

        init {
            lifecycleScope.launch {
                implementationLimits.collect {
                    binding.buttonSignMaxTransactionsWithMaxSignatures.text =
                        binding.root.context.getString(
                            R.string.action_sign_max_transactions_with_max_signatures,
                            it.maxSigningRequests,
                            it.maxRequestedSignatures
                        )
                    binding.buttonSignMaxMessagesWithMaxSignatures.text =
                        binding.root.context.getString(
                            R.string.action_sign_max_messages_with_max_signatures,
                            it.maxSigningRequests,
                            it.maxRequestedSignatures
                        )
                    binding.buttonRequestPublicKeys.text = binding.root.context.getString(
                        R.string.action_request_public_keys,
                        it.firstRequestedPublicKey,
                        it.lastRequestedPublicKey
                    )
                }
            }

            binding.buttonDeauthorizeSeed.setOnClickListener {
                seed?.let { s ->
                    onDeauthorizeSeed(s)
                }
            }
            binding.buttonRequestPublicKeys.setOnClickListener {
                seed?.let { s ->
                    onRequestPublicKeys(s)
                }
            }
            binding.buttonSignMaxTransactionsWithMaxSignatures.setOnClickListener {
                seed?.let { s ->
                    onSignMaxTransactionsWithMaxSignatures(s)
                }
            }
            binding.buttonSignMaxMessagesWithMaxSignatures.setOnClickListener {
                seed?.let { s ->
                    onSignMaxMessagesWithMaxSignatures(s)
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
        return SeedViewHolder(binding)
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