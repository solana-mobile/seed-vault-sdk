/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.example.walletapidemo.ui.seeds

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.walletapidemo.databinding.ItemSeedSummaryBinding
import com.example.walletapidemo.model.Seed
import com.example.walletapidemo.usecase.GetNameUseCase

class SeedListAdapter(
    val onClick: (Seed) -> Unit,
    val onDelete: (Seed) -> Unit
) : ListAdapter<Seed, SeedListAdapter.SeedViewHolder>(SeedDiffCallback) {
    class SeedViewHolder(
        private val binding: ItemSeedSummaryBinding,
        private val onClick: (Seed) -> Unit,
        private val onDelete: (Seed) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        private var currentSeed: Seed? = null

        init {
            binding.root.setOnClickListener {
                currentSeed?.let {
                    onClick(it)
                }
            }
            binding.imagebuttonSeedDelete.setOnClickListener {
                currentSeed?.let {
                    onDelete(it)
                }
            }
        }

        fun bind(s: Seed) {
            currentSeed = s.also {
                binding.textviewSeedName.text = GetNameUseCase.getName(it)
            }
        }
    }

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeedViewHolder {
        val binding = ItemSeedSummaryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SeedViewHolder(binding, onClick, onDelete)
    }

    override fun onBindViewHolder(holder: SeedViewHolder, position: Int) {
        val seed = getItem(position)
        holder.bind(seed)
    }

    override fun getItemId(position: Int): Long {
        val seed = getItem(position)
        return seed.id.toLong()
    }
}

private object SeedDiffCallback : DiffUtil.ItemCallback<Seed>() {
    override fun areItemsTheSame(oldItem: Seed, newItem: Seed): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Seed, newItem: Seed): Boolean {
        return GetNameUseCase.getName(oldItem) == GetNameUseCase.getName(newItem)
    }
}