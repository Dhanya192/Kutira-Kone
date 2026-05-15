package com.kutira.kone.ui.ideas

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.kutira.kone.R
import com.kutira.kone.data.model.DesignIdea
import com.kutira.kone.databinding.FragmentIdeasBinding

class IdeasFragment : Fragment() {

    private var _binding: FragmentIdeasBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIdeasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ideas = listOf(
            DesignIdea(1,  "😷 Face Mask",       "Reusable fabric face mask from cotton scraps",        listOf("Cotton"),                   "Easy",   R.drawable.ic_idea_mask),
            DesignIdea(2,  "💜 Scrunchie",        "Trendy hair scrunchie from any fabric type",          listOf("Silk","Cotton","Polyester"), "Easy",   R.drawable.ic_idea_scrunchie),
            DesignIdea(3,  "👜 Patchwork Pouch",  "Small pouch combining colorful fabric pieces",        listOf("Cotton","Linen"),            "Medium", R.drawable.ic_idea_pouch),
            DesignIdea(4,  "🪆 Doll Clothes",     "Miniature outfits for handmade dolls",                listOf("Cotton","Silk","Wool"),      "Medium", R.drawable.ic_idea_doll),
            DesignIdea(5,  "📖 Fabric Bookmark",  "Fold thin strips into beautiful bookmarks",           listOf("Any"),                       "Easy",   R.drawable.ic_idea_bookmark),
            DesignIdea(6,  "🛍️ Mini Tote Bag",   "Upcycle larger scraps into a small utility bag",      listOf("Cotton"),                    "Hard",   R.drawable.ic_idea_bag),
            DesignIdea(7,  "🪔 Quilted Coaster",  "Layer and sew scraps into heat-resistant coasters",   listOf("Cotton","Wool"),             "Medium", R.drawable.ic_idea_coaster),
            DesignIdea(8,  "🌸 Fabric Flower",    "Twist and fold scraps into decorative flowers",       listOf("Silk","Polyester"),          "Easy",   R.drawable.ic_idea_flower),
            DesignIdea(9,  "🧸 Stuffed Animal",   "Fill small bags with fabric scraps for soft toys",    listOf("Cotton"),                    "Hard",   R.drawable.ic_idea_toy)
        )

        binding.rvIdeas.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvIdeas.adapter = IdeasAdapter(ideas) { showIdeaDetail(it) }

        // Launch full-screen AI Activity — same pattern as MadhuMarga
        binding.btnAiAssistant.setOnClickListener {
            startActivity(Intent(requireContext(), AiActivity::class.java))
        }
    }

    private fun showIdeaDetail(idea: DesignIdea) {
        val sheet = BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme)
        val sheetView = LayoutInflater.from(requireContext())
            .inflate(R.layout.bottom_sheet_idea_detail, null)

        sheetView.findViewById<android.widget.ImageView>(R.id.ivIdeaIcon).setImageResource(idea.iconRes)
        sheetView.findViewById<TextView>(R.id.tvIdeaTitle).text = idea.title
        sheetView.findViewById<TextView>(R.id.tvIdeaDesc).text = idea.description

        val diffColor = when (idea.difficulty) {
            "Easy"   -> androidx.core.content.ContextCompat.getColor(requireContext(), R.color.difficulty_easy)
            "Medium" -> androidx.core.content.ContextCompat.getColor(requireContext(), R.color.difficulty_medium)
            "Hard"   -> androidx.core.content.ContextCompat.getColor(requireContext(), R.color.difficulty_hard)
            else     -> androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary)
        }
        val tvDiff = sheetView.findViewById<TextView>(R.id.tvIdeaDifficulty)
        tvDiff.text = "⚡ ${idea.difficulty}"
        tvDiff.setTextColor(diffColor)

        sheetView.findViewById<TextView>(R.id.tvIdeaMaterials).text =
            "📦 Materials: ${idea.materials.joinToString(", ")}"
        sheetView.findViewById<TextView>(R.id.tvIdeaTip).text =
            "🌱 Upcycling fabric scraps reduces landfill waste and supports a circular economy in your village!"

        sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnIdeaClose)
            .setOnClickListener { sheet.dismiss() }
        sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnIdeaFind)
            .setOnClickListener {
                sheet.dismiss()
                navigateToHomeWithFilter(idea.materials.firstOrNull() ?: "All")
            }

        sheet.setContentView(sheetView)
        sheet.show()
    }

    private fun navigateToHomeWithFilter(material: String) {
        val activity = requireActivity()
        val bottomNav = activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
        val navHostFragment = activity.supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? androidx.navigation.fragment.NavHostFragment
        val navController = navHostFragment?.navController
        if (navController != null) {
            val args = Bundle().apply { putString("filterMaterial", material) }
            navController.navigate(R.id.homeFragment, args,
                androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.homeFragment, true).build())
        }
        bottomNav?.selectedItemId = R.id.homeFragment
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
