/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.fakewallet.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.solanamobile.fakewallet.R
import com.solanamobile.fakewallet.databinding.ItemLabelWithAddButtonBinding

class CreateSeedAdapter(
    private val onCreateNewSeed: () -> Unit
) : RecyclerView.Adapter<CreateSeedAdapter.ViewHolder>() {
    inner class ViewHolder(
        binding: ItemLabelWithAddButtonBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.label.setText(R.string.label_create_new_seed)
            binding.buttonAdd.setOnClickListener {
                onCreateNewSeed()
            }
        }
    }

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLabelWithAddButtonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = Unit
    override fun getItemCount(): Int = 1
    override fun getItemId(position: Int): Long = 1L
}