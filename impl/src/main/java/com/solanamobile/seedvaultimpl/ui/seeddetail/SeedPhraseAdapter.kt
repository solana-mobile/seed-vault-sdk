/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.seeddetail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.solanamobile.seedvaultimpl.databinding.ItemSeedPhraseWordBinding
import com.solanamobile.seedvaultimpl.usecase.Bip39PhraseUseCase

class SeedPhraseAdapter(
    private val onWordChanged: (index: Int, word: String) -> Unit
) : ListAdapter<SeedPhraseAdapter.Word, SeedPhraseAdapter.SeedPhraseWordViewHolder>(WordDiffCallback) {
    data class Word(
        val index: Int,
        val word: String,
        val isReadOnly: Boolean
    )

    init {
        setHasStableIds(true)
    }

    class SeedPhraseWordViewHolder(
        val binding: ItemSeedPhraseWordBinding,
        private val onWordChanged: (index: Int, word: String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(word: Word) {
            binding.apply {
                labelSeedPhraseWordIndex.text = "${word.index + 1}."
                edittextautocompleteSeedPhraseWord.setTextKeepState(word.word)
                edittextautocompleteSeedPhraseWord.isEnabled = !word.isReadOnly
                if (word.isReadOnly && edittextautocompleteSeedPhraseWord.adapter != null) {
                    edittextautocompleteSeedPhraseWord.setAdapter(null)
                } else if (!word.isReadOnly && edittextautocompleteSeedPhraseWord.adapter == null) {
                    edittextautocompleteSeedPhraseWord.setAdapter(
                        ArrayAdapter(binding.root.context,
                            android.R.layout.simple_dropdown_item_1line,
                            Bip39PhraseUseCase.bip39EnglishWordlist)
                    )
                }
                edittextautocompleteSeedPhraseWord.setOnFocusChangeListener { view, hasFocus ->
                    if (!hasFocus) {
                        val newWord = (view as TextView).text.toString()
                        if (newWord != word.word) {
                            onWordChanged(word.index, newWord)
                        }
                    }
                }
                var isWordRecognized = true
                if (!word.isReadOnly && word.word.isNotEmpty()) {
                    try {
                        Bip39PhraseUseCase.toIndex(word.word)
                    } catch (_: IllegalArgumentException) {
                        isWordRecognized = false
                    }
                }
                labelErrorUnrecognizedWord.visibility = if (isWordRecognized) View.GONE else View.VISIBLE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeedPhraseWordViewHolder {
        val binding = ItemSeedPhraseWordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SeedPhraseWordViewHolder(binding, onWordChanged)
    }

    override fun onBindViewHolder(holder: SeedPhraseWordViewHolder, position: Int) {
        val word = getItem(position)
        holder.bind(word)
    }

    override fun getItemId(position: Int): Long {
        val word = getItem(position)
        return word.index.toLong()
    }
}

private object WordDiffCallback : DiffUtil.ItemCallback<SeedPhraseAdapter.Word>() {
    override fun areItemsTheSame(
        oldItem: SeedPhraseAdapter.Word,
        newItem: SeedPhraseAdapter.Word
    ): Boolean {
        return oldItem.index == newItem.index
    }

    override fun areContentsTheSame(
        oldItem: SeedPhraseAdapter.Word,
        newItem: SeedPhraseAdapter.Word
    ): Boolean {
        return oldItem == newItem
    }
}