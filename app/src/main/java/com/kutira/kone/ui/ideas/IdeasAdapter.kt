package com.kutira.kone.ui.ideas

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.kutira.kone.R
import com.kutira.kone.data.model.DesignIdea
import com.kutira.kone.databinding.ItemIdeaBinding

class IdeasAdapter(
    private val ideas: List<DesignIdea>,
    private val onClick: (DesignIdea) -> Unit
) : RecyclerView.Adapter<IdeasAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemIdeaBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(idea: DesignIdea) {
            binding.tvTitle.text = idea.title
            binding.tvMaterials.text = idea.materials.joinToString(" • ")
            binding.tvDifficulty.text = idea.difficulty
            binding.ivIcon.setImageResource(idea.iconRes)
            val ctx = binding.root.context
            val diffColor = when (idea.difficulty) {
                "Easy"   -> ContextCompat.getColor(ctx, R.color.difficulty_easy)
                "Medium" -> ContextCompat.getColor(ctx, R.color.difficulty_medium)
                "Hard"   -> ContextCompat.getColor(ctx, R.color.difficulty_hard)
                else     -> ContextCompat.getColor(ctx, R.color.text_secondary)
            }
            binding.tvDifficulty.setTextColor(diffColor)
            binding.root.setOnClickListener { onClick(idea) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemIdeaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(ideas[position])
    override fun getItemCount() = ideas.size
}
