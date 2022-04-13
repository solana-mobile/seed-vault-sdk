/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.selectseed

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.selection.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.solanamobile.seedvaultimpl.databinding.ItemSeedNameBinding
import com.solanamobile.seedvaultimpl.model.Seed
import com.solanamobile.seedvaultimpl.usecase.GetNameUseCase

typealias SeedWithSelectionState = Pair<Seed, Boolean>

class SeedListAdapter(
    val onSelect: (Long?) -> Unit
) : ListAdapter<SeedWithSelectionState, SeedListAdapter.SeedViewHolder>(SeedDiffCallback) {
    class SeedViewHolder(
        private val binding: ItemSeedNameBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        val itemDetails get() = object : ItemDetailsLookup.ItemDetails<Long>() {
            override fun getPosition(): Int = bindingAdapterPosition
            override fun getSelectionKey(): Long = itemId
            override fun inSelectionHotspot(e: MotionEvent): Boolean = true
        }

        fun bind(seed: Seed, isSelected: Boolean) {
            binding.root.isActivated = isSelected
            binding.textviewSeedName.text = GetNameUseCase.getName(seed)
        }
    }

    init {
        setHasStableIds(true)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        val itemKeyProvider = object : ItemKeyProvider<Long>(SCOPE_MAPPED) {
            override fun getKey(position: Int): Long = currentList[position].first.id
            override fun getPosition(key: Long): Int = currentList.indexOfFirst { it.first.id == key }
        }

        val itemDetailsLookup = object : ItemDetailsLookup<Long>() {
            override fun getItemDetails(e: MotionEvent): ItemDetails<Long>? {
                return recyclerView.findChildViewUnder(e.x, e.y)?.let { view ->
                    (recyclerView.getChildViewHolder(view) as SeedViewHolder).itemDetails
                }
            }
        }

        val seedListSelectionTracker = SelectionTracker.Builder(
            "selected-seed-id",
            recyclerView,
            itemKeyProvider,
            itemDetailsLookup,
            StorageStrategy.createLongStorage()
        ).withSelectionPredicate(
            SelectionPredicates.createSelectSingleAnything()
        ).build()

        val selectionObserver = object : SelectionTracker.SelectionObserver<Long>() {
            override fun onSelectionChanged() {
                onSelect(seedListSelectionTracker.selection.firstOrNull())
            }
        }
        seedListSelectionTracker.addObserver(selectionObserver)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeedViewHolder {
        val binding = ItemSeedNameBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SeedViewHolder(binding)
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