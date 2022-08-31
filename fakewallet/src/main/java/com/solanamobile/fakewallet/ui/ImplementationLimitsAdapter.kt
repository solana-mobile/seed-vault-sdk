/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.fakewallet.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.solanamobile.fakewallet.databinding.ItemImplementationLimitBinding

typealias ImplementationLimit = Pair<String, Long>

class ImplementationLimitsAdapter(private val onTestExceedLimit: (String) -> Unit) :
    ListAdapter<ImplementationLimit, ImplementationLimitsAdapter.ImplementationLimitViewHolder>(
        ImplementationLimitDiffCallback
    ) {
    inner class ImplementationLimitViewHolder(
        private val binding: ItemImplementationLimitBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(implementationLimit: ImplementationLimit) {
            binding.textviewImplementationLimit.text = implementationLimit.first + "=" + implementationLimit.second.toString()
            binding.buttonExceedImplementationLimit.setOnClickListener {
                onTestExceedLimit(
                    implementationLimit.first
                )
            }
        }
    }

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ImplementationLimitViewHolder {
        val binding = ItemImplementationLimitBinding.inflate(LayoutInflater.from(parent.context),
            parent, false)
        return ImplementationLimitViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImplementationLimitViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    override fun getItemId(position: Int): Long {
        val item = getItem(position)
        return item.first.hashCode().toLong()
    }
}

private object ImplementationLimitDiffCallback : DiffUtil.ItemCallback<ImplementationLimit>() {
    override fun areItemsTheSame(oldItem: ImplementationLimit, newItem: ImplementationLimit): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: ImplementationLimit, newItem: ImplementationLimit): Boolean {
        return oldItem.first == newItem.first && oldItem.second == newItem.second
    }
}