/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.selectseed

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.solanamobile.seedvaultimpl.databinding.ItemSeedNameBinding
import com.solanamobile.seedvaultimpl.model.Seed
import com.solanamobile.seedvaultimpl.usecase.GetNameUseCase

typealias SeedWithSelectionState = Pair<Seed, Boolean>

class SeedListAdapter(
    val onSelect: (Long?) -> Unit
) : ListAdapter<SeedWithSelectionState, SeedListAdapter.SeedViewHolder>(SeedDiffCallback) {
    class SeedViewHolder(
        private val binding: ItemSeedNameBinding,
        private val onSelect: (Long?) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        private lateinit var seed: Seed

        init {
            binding.root.setOnClickListener {
                onSelect(seed.id)
            }
        }

        fun bind(seed: Seed, isSelected: Boolean) {
            this.seed = seed
            binding.radioSeedSelected.isChecked = isSelected
            binding.textSeedName.text = GetNameUseCase.getName(seed)
        }
    }

    init {
        setHasStableIds(true)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        // Item change animations causes items to flicker when they are added/removed. This
        // ListAdapter is backed by a ViewModel, which produces new lists when the model changes.
        val itemAnimator = recyclerView.itemAnimator
        if (itemAnimator is SimpleItemAnimator) {
            itemAnimator.supportsChangeAnimations = false
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeedViewHolder {
        val binding = ItemSeedNameBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SeedViewHolder(binding, onSelect)
    }

    override fun onBindViewHolder(holder: SeedViewHolder, position: Int) {
        val seedWithSelectionState = getItem(position)
        holder.bind(seedWithSelectionState.first, seedWithSelectionState.second)
    }

    override fun getItemId(position: Int): Long {
        val seedWithSelectionState = getItem(position)
        return seedWithSelectionState.first.id
    }
}

private object SeedDiffCallback : DiffUtil.ItemCallback<SeedWithSelectionState>() {
    override fun areItemsTheSame(oldItem: SeedWithSelectionState, newItem: SeedWithSelectionState): Boolean {
        return oldItem.first.id == newItem.first.id
    }

    override fun areContentsTheSame(oldItem: SeedWithSelectionState, newItem: SeedWithSelectionState): Boolean {
        return (GetNameUseCase.getName(oldItem.first) == GetNameUseCase.getName(newItem.first)) &&
                (oldItem.second == newItem.second)
    }
}