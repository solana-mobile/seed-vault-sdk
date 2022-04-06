/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.example.walletapidemo.ui.seeddetail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.walletapidemo.databinding.ItemAuthorizedAppBinding
import com.example.walletapidemo.model.Authorization

class AuthorizedAppsAdapter(
    private val onDeauthorize: (Authorization) -> Unit
) : ListAdapter<Authorization, AuthorizedAppsAdapter.AuthorizationViewHolder>(AuthorizationDiff) {
    class AuthorizationViewHolder(
        private val binding: ItemAuthorizedAppBinding,
        private val onDeauthorize: (Authorization) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var authorization: Authorization? = null

        init {
            binding.imagebuttonDeauthorizeApp.setOnClickListener {
                authorization?.let {
                    onDeauthorize(it)
                }
            }
        }

        fun bind(authorization: Authorization) {
            this.authorization = authorization
            val packages = binding.root.context.packageManager.getPackagesForUid(authorization.uid)
            val text = packages?.joinToString("\n") ?: authorization.uid.toString()
            binding.labelAppName.text = text
            binding.labelPurpose.text = authorization.purpose.toString()
        }
    }

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AuthorizationViewHolder {
        val binding = ItemAuthorizedAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AuthorizationViewHolder(binding, onDeauthorize)
    }

    override fun onBindViewHolder(holder: AuthorizationViewHolder, position: Int) {
        val authorization = getItem(position)
        holder.bind(authorization)
    }

    override fun getItemId(position: Int): Long {
        val authorization = getItem(position)
        return authorization.uid.toLong()
    }
}

private object AuthorizationDiff : DiffUtil.ItemCallback<Authorization>() {
    override fun areItemsTheSame(oldItem: Authorization, newItem: Authorization): Boolean {
        return oldItem.uid == newItem.uid
    }

    override fun areContentsTheSame(oldItem: Authorization, newItem: Authorization): Boolean {
        return oldItem == newItem
    }
}