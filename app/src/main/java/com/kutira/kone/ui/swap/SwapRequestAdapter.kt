package com.kutira.kone.ui.swap

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kutira.kone.data.model.SwapRequest
import com.kutira.kone.databinding.ItemSwapRequestBinding

class SwapRequestAdapter(
    private val currentUserId: String,
    private val onAccept: (SwapRequest) -> Unit,
    private val onReject: (SwapRequest) -> Unit
) : ListAdapter<SwapRequest, SwapRequestAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemSwapRequestBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(req: SwapRequest) {
            val isSentByMe = req.requesterId == currentUserId
            binding.tvRequesterName.text =
                if (isSentByMe) "You (Sent)" else req.requesterName
            binding.tvScrapTitle.text = "For: ${req.scrapTitle}"
            binding.tvMessage.text = req.message

            val isOwner = req.ownerId == currentUserId

            when (req.status) {

                "pending" -> {
                    binding.tvStatus.text = "⏳ Pending"
                    binding.tvStatus.setTextColor(Color.parseColor("#FF9800"))

                    if (isOwner) {
                        // OWNER → can accept/reject
                        binding.btnAccept.visibility = View.VISIBLE
                        binding.btnReject.visibility = View.VISIBLE
                    } else {
                        // REQUESTER → just view
                        binding.btnAccept.visibility = View.GONE
                        binding.btnReject.visibility = View.GONE
                    }

                    binding.btnChat.visibility = View.GONE
                }

                "accepted" -> {
                    binding.tvStatus.text = "✅ Accepted"
                    binding.tvStatus.setTextColor(Color.parseColor("#4CAF50"))

                    binding.btnAccept.visibility = View.GONE
                    binding.btnReject.visibility = View.GONE

                    // BOTH users can chat
                    binding.btnChat.visibility = View.VISIBLE
                }

                "rejected" -> {
                    binding.tvStatus.text = "❌ Declined"
                    binding.tvStatus.setTextColor(Color.parseColor("#F44336"))

                    binding.btnAccept.visibility = View.GONE
                    binding.btnReject.visibility = View.GONE
                    binding.btnChat.visibility = View.GONE
                }
            }

            binding.btnAccept.setOnClickListener { onAccept(req) }
            binding.btnReject.setOnClickListener { onReject(req) }
            binding.btnChat.setOnClickListener {
                val context = binding.root.context
                val intent = android.content.Intent(context, com.kutira.kone.ui.chat.ChatActivity::class.java)
                val otherUserId = if (req.ownerId == currentUserId) {
                    req.requesterId
                } else {
                    req.ownerId
                }

                intent.putExtra("userId", otherUserId)
                context.startActivity(intent)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSwapRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class DiffCallback : DiffUtil.ItemCallback<SwapRequest>() {
        override fun areItemsTheSame(a: SwapRequest, b: SwapRequest) = a.id == b.id
        override fun areContentsTheSame(a: SwapRequest, b: SwapRequest) = a == b
    }
}
