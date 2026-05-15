package com.kutira.kone.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import com.kutira.kone.R
import com.kutira.kone.data.model.FabricScrap
import com.kutira.kone.data.model.SwapRequest
import com.kutira.kone.data.repository.FabricRepository
import com.kutira.kone.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val repo = FabricRepository()
    private lateinit var adapter: ScrapAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentUserId = repo.currentUserId()

        adapter = ScrapAdapter(
            currentUserId = currentUserId,
            onSwapClick = { scrap -> showScrapPreviewSheet(scrap) },
            onEditClick = { scrap -> showEditDialog(scrap) }
        )
        binding.rvScraps.layoutManager = LinearLayoutManager(requireContext())
        binding.rvScraps.adapter = adapter

        setupFilters()

        binding.fabUpload.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_upload)
        }

        binding.swipeRefresh.setOnRefreshListener { loadScraps() }

        val filterMaterial = arguments?.getString("filterMaterial") ?: ""
        if (filterMaterial.isNotEmpty()) {
            loadScrapsByMaterial(filterMaterial)
            binding.chipGroup.post {
                for (i in 0 until binding.chipGroup.childCount) {
                    val chip = binding.chipGroup.getChildAt(i) as? com.google.android.material.chip.Chip
                    if (chip?.text?.toString().equals(filterMaterial, ignoreCase = true) ||
                        (filterMaterial == "Any" && chip?.text?.toString() == "All")) {
                        chip?.isChecked = true
                        break
                    }
                }
            }
        } else {
            loadScraps()
        }
    }

    override fun onResume() {
        super.onResume()
        val filterMaterial = arguments?.getString("filterMaterial") ?: ""
        if (filterMaterial.isEmpty()) loadScraps()
    }

    private fun setupFilters() {
        val filters = listOf("All", "Silk", "Cotton", "Wool", "Polyester", "Linen")
        filters.forEach { material ->
            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                text = material
                isCheckable = true
                if (material == "All") isChecked = true
            }
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) loadScrapsByMaterial(material)
            }
            binding.chipGroup.addView(chip)
        }
    }

    private fun loadScraps() {
        lifecycleScope.launch {
            _binding?.progressBar?.visibility = View.VISIBLE
            val scraps = try { repo.getAllScraps() } catch (e: Exception) { emptyList() }
            if (_binding == null) return@launch
            binding.progressBar.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false
            if (scraps.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.rvScraps.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.rvScraps.visibility = View.VISIBLE
                adapter.submitList(scraps)
            }
        }
    }

    private fun loadScrapsByMaterial(material: String) {
        lifecycleScope.launch {
            _binding?.progressBar?.visibility = View.VISIBLE
            val scraps = try {
                if (material == "All" || material == "Any") repo.getAllScraps()
                else repo.getScrapsByMaterial(material)
            } catch (e: Exception) { emptyList() }
            if (_binding == null) return@launch
            binding.progressBar.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false
            if (scraps.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.rvScraps.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.rvScraps.visibility = View.VISIBLE
                val currentUserId = repo.currentUserId()
                val filtered = if (material == "All" || material == "Any") scraps
                               else scraps.filter { it.userId != currentUserId }
                adapter.submitList(filtered)
            }
        }
    }

    // ── Scrap Preview Bottom Sheet ──────────────────────────────────

    private fun showScrapPreviewSheet(scrap: FabricScrap) {
        val currentUserId = repo.currentUserId()
        if (scrap.userId == currentUserId) {
            showEditDialog(scrap)
            return
        }

        val sheet = BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme)
        val sheetView = LayoutInflater.from(requireContext())
            .inflate(R.layout.bottom_sheet_scrap_preview, null)

        val ivPreview = sheetView.findViewById<android.widget.ImageView>(R.id.ivPreview)
        if (scrap.imageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(scrap.imageUrl)
                .placeholder(R.drawable.ic_fabric_placeholder)
                .error(R.drawable.ic_fabric_placeholder)
                .centerCrop()
                .into(ivPreview)
        } else {
            ivPreview.setImageResource(R.drawable.ic_fabric_placeholder)
        }

        val colorDot = sheetView.findViewById<View>(R.id.colorDotPreview)
        val colorHex = when (scrap.color.lowercase()) {
            "red"    -> "#FFCDD2"; "blue"   -> "#BBDEFB"; "green"  -> "#C8E6C9"
            "yellow" -> "#FFF9C4"; "orange" -> "#FFE0B2"; "purple" -> "#E1BEE7"
            "pink"   -> "#FCE4EC"; "white"  -> "#F5F5F5"; "black"  -> "#424242"
            else     -> "#E0E0E0"
        }
        colorDot.setBackgroundColor(android.graphics.Color.parseColor(colorHex))

        sheetView.findViewById<android.widget.TextView>(R.id.tvPreviewTitle).text = scrap.title
        sheetView.findViewById<android.widget.TextView>(R.id.tvPreviewUser).text = "by ${scrap.userName}"
        sheetView.findViewById<android.widget.TextView>(R.id.tvPreviewMaterial).text = scrap.material
        sheetView.findViewById<android.widget.TextView>(R.id.tvPreviewColor).text = scrap.color
        sheetView.findViewById<android.widget.TextView>(R.id.tvPreviewSize).text = "${scrap.sizeMeters}m"
        sheetView.findViewById<android.widget.TextView>(R.id.tvPreviewLocation).text =
            "📍 ${scrap.locationName.ifEmpty { "Location not set" }}"

        val tvDesc = sheetView.findViewById<android.widget.TextView>(R.id.tvPreviewDescription)
        if (scrap.description.isNotEmpty()) {
            tvDesc.text = "\"${scrap.description}\""
            tvDesc.visibility = View.VISIBLE
        }

        sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPreviewClose)
            .setOnClickListener { sheet.dismiss() }

        sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPreviewAction)
            .setOnClickListener {
                sheet.dismiss()
                showSwapRequestDialog(scrap)
            }

        sheet.setContentView(sheetView)
        sheet.show()
    }

    private fun showSwapRequestDialog(scrap: FabricScrap) {
        val input = TextInputEditText(requireContext()).apply {
            hint = "Write a message (optional)"
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Request Swap for \"${scrap.title}\"")
            .setMessage("Send a swap request to ${scrap.userName}")
            .setView(input)
            .setPositiveButton("Send Request") { _, _ ->
                val message = input.text.toString().trim()
                    .ifEmpty { "Hi! I'd love to swap for your ${scrap.material} scraps." }
                sendSwapRequest(scrap, message)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendSwapRequest(scrap: FabricScrap, message: String) {
        val currentUserId = repo.currentUserId()
        if (currentUserId.isEmpty()) {
            Toast.makeText(requireContext(), "Please login to send swap requests", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val existing = try {
                repo.getRequestsSentByMe().any { it.scrapId == scrap.id }
            } catch (e: Exception) { false }

            if (existing) {
                if (isAdded) Toast.makeText(requireContext(), "You already requested this item", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val request = SwapRequest(
                requesterId = currentUserId,
                requesterName = repo.currentUserEmail().substringBefore("@"),
                ownerId = scrap.userId,
                scrapId = scrap.id,
                scrapTitle = scrap.title,
                message = message,
                status = "pending"
            )
            val result = repo.sendSwapRequest(request)
            if (!isAdded) return@launch
            result.fold(
                onSuccess = { Toast.makeText(requireContext(), "Swap request sent! 🎉", Toast.LENGTH_SHORT).show() },
                onFailure = { Toast.makeText(requireContext(), "Failed: ${it.message}", Toast.LENGTH_LONG).show() }
            )
        }
    }

    // ── Edit Scrap Dialog ──────────────────────────────────────────

    private fun showEditDialog(scrap: FabricScrap) {
        if (!isAdded) return
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_scrap, null)

        val etTitle       = dialogView.findViewById<android.widget.EditText>(R.id.etTitle)
        val etDescription = dialogView.findViewById<android.widget.EditText>(R.id.etDescription)
        val btnSave       = dialogView.findViewById<android.widget.TextView>(R.id.btnSave)
        val btnCancel     = dialogView.findViewById<android.widget.TextView>(R.id.btnCancel)
        val btnDelete     = dialogView.findViewById<android.widget.TextView>(R.id.btnDelete)

        etTitle.setText(scrap.title)
        etDescription.setText(scrap.description)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val newTitle = etTitle.text.toString().trim()
            val newDesc  = etDescription.text.toString().trim()
            if (newTitle.isEmpty()) {
                Toast.makeText(requireContext(), "Title required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                try {
                    repo.updateScrap(scrap.id, newTitle, newDesc)
                    if (!isAdded) return@launch
                    Toast.makeText(requireContext(), "Updated ✅", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    loadScraps()
                } catch (e: Exception) {
                    if (isAdded) Toast.makeText(requireContext(), "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnDelete.setOnClickListener {
            confirmDelete(scrap)
            dialog.dismiss()
        }
    }

    private fun confirmDelete(scrap: FabricScrap) {
        if (!isAdded) return
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Scrap?")
            .setMessage("\"${scrap.title}\" will be permanently deleted.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val result = try { repo.deleteScrap(scrap.id) } catch (e: Exception) { Result.failure(e) }
                    if (!isAdded) return@launch
                    result.fold(
                        onSuccess = {
                            Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
                            loadScraps()
                        },
                        onFailure = {
                            Toast.makeText(requireContext(), "Delete failed: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
