package com.kutira.kone.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kutira.kone.R
import com.kutira.kone.data.model.FabricScrap
import com.kutira.kone.databinding.ItemScrapBinding

class ScrapAdapter(
    private val currentUserId: String = "",
    private val isProfileScreen: Boolean = false,
    private val onSwapClick: (FabricScrap) -> Unit,
    private val onEditClick: (FabricScrap) -> Unit = {},
    private val onAddPhotoClick: ((FabricScrap) -> Unit)? = null
) : ListAdapter<FabricScrap, ScrapAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemScrapBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(scrap: FabricScrap) {
            binding.tvTitle.text = scrap.title
            binding.tvMaterial.text = scrap.material
            binding.tvColor.text = scrap.color
            binding.tvSize.text = "${scrap.sizeMeters}m"
            binding.tvLocation.text = if (scrap.locationName.isNotEmpty())
                "📍 ${scrap.locationName}" else "📍 Location not set"
            binding.tvUser.text = "by ${scrap.userName}"

            // Load image — show "Add Photo" overlay if no image
            if (scrap.imageUrl.isNotEmpty()) {
                Glide.with(binding.root.context)
                    .load(scrap.imageUrl)
                    .placeholder(R.drawable.ic_fabric_placeholder)
                    .error(R.drawable.ic_fabric_placeholder)
                    .centerCrop()
                    .into(binding.ivScrap)
                binding.layoutAddPhoto.visibility = View.GONE
            } else {
                binding.ivScrap.setImageResource(R.drawable.ic_fabric_placeholder)
                // Show "Add Photo" only on owner's own scraps
                if (scrap.userId == currentUserId) {
                    binding.layoutAddPhoto.visibility = View.VISIBLE
                    binding.layoutAddPhoto.setOnClickListener { onAddPhotoClick?.invoke(scrap) }
                } else {
                    binding.layoutAddPhoto.visibility = View.GONE
                }
            }

            // Color tag
            val color = when (scrap.color.lowercase()) {
                "red"    -> android.graphics.Color.parseColor("#FFCDD2")
                "blue"   -> android.graphics.Color.parseColor("#BBDEFB")
                "green"  -> android.graphics.Color.parseColor("#C8E6C9")
                "yellow" -> android.graphics.Color.parseColor("#FFF9C4")
                "orange" -> android.graphics.Color.parseColor("#FFE0B2")
                "purple" -> android.graphics.Color.parseColor("#E1BEE7")
                "pink"   -> android.graphics.Color.parseColor("#FCE4EC")
                "white"  -> android.graphics.Color.parseColor("#F5F5F5")
                "black"  -> android.graphics.Color.parseColor("#424242")
                else     -> android.graphics.Color.parseColor("#E0E0E0")
            }
            binding.colorDot.setBackgroundColor(color)

            val isOwner = scrap.userId == currentUserId

            if (isProfileScreen) {
                binding.btnSwap.visibility = View.GONE
            } else {
                binding.btnSwap.visibility = View.VISIBLE
                if (isOwner) {
                    binding.btnSwap.text = "✏️ Edit"
                    binding.btnSwap.setOnClickListener { onEditClick(scrap) }
                } else {
                    binding.btnSwap.text = "🔄 Request Swap"
                    binding.btnSwap.setOnClickListener { onSwapClick(scrap) }
                }
            }

            binding.root.setOnClickListener {
                if (scrap.userId == currentUserId) onEditClick(scrap)
                else onSwapClick(scrap)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScrapBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<FabricScrap>() {
        override fun areItemsTheSame(a: FabricScrap, b: FabricScrap) = a.id == b.id
        override fun areContentsTheSame(a: FabricScrap, b: FabricScrap) = a == b
    }
}
