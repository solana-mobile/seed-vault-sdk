/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.fakewallet.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.solanamobile.fakewallet.R
import com.solanamobile.fakewallet.databinding.ItemHasUnauthorizedSeedsBinding

class HasUnauthorizedSeedsAdapter(
    private val onAuthorizeNewSeed: () -> Unit
) : ListAdapter<Boolean, HasUnauthorizedSeedsAdapter.HasUnauthorizedSeedsViewHolder>(HasUnauthorizedSeedsDiffCallback) {
    inner class HasUnauthorizedSeedsViewHolder(
        private val binding: ItemHasUnauthorizedSeedsBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.buttonAuthorizeSeed.setOnClickListener {
                onAuthorizeNewSeed()
            }
        }

        fun bind(hasUnauthorizedSeeds: Boolean) {
            binding.buttonAuthorizeSeed.visibility = if (hasUnauthorizedSeeds) View.VISIBLE else View.GONE
            binding.textviewHasUnauthorizedSeeds.text = binding.root.context.resources.getQuantityText(
                R.plurals.label_has_unauthorized_seeds, if (hasUnauthorizedSeeds) 1 else 0)
        }
    }

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HasUnauthorizedSeedsViewHolder {
        val binding = ItemHasUnauthorizedSeedsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HasUnauthorizedSeedsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HasUnauthorizedSeedsViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }
}

object HasUnauthorizedSeedsDiffCallback : DiffUtil.ItemCallback<Boolean>() {
    override fun areItemsTheSame(oldItem: Boolean, newItem: Boolean): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: Boolean, newItem: Boolean): Boolean {
        return oldItem == newItem
    }
}