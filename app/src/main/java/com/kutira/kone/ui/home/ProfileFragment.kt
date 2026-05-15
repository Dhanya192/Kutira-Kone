package com.kutira.kone.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.kutira.kone.MainActivity
import com.kutira.kone.data.repository.FabricRepository
import com.kutira.kone.databinding.FragmentProfileBinding
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val repo = FabricRepository()
    private lateinit var listingsAdapter: ScrapAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentUserId = repo.currentUserId()

        listingsAdapter = ScrapAdapter(
            currentUserId = currentUserId,
            isProfileScreen = true,
            onSwapClick = {},
            onEditClick = { scrap ->
                Toast.makeText(requireContext(), "Edit ${scrap.title}", Toast.LENGTH_SHORT).show()
            }
        )

        binding.rvMyListings.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMyListings.adapter = listingsAdapter

        binding.btnLogout.setOnClickListener {
            (activity as? MainActivity)?.logout()
        }

        loadProfile()
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            // Show loading state
            binding.tvScrapsListed.text = "…"
            binding.tvSwapsCompleted.text = "…"

            // Load user info
            val user = try { repo.getCurrentUser() } catch (e: Exception) { null }
            val email = repo.currentUserEmail()

            binding.tvName.text = user?.name?.takeIf { it.isNotEmpty() } ?: email.substringBefore("@").ifEmpty { "Artisan" }
            binding.tvEmail.text = email.ifEmpty { "demo@kutira.com" }

            // Load my listings — count is the real scrapsListed
            val listings = try { repo.getMyListings() } catch (e: Exception) { emptyList() }
            listingsAdapter.submitList(listings)
            binding.tvNoListings.visibility = if (listings.isEmpty()) View.VISIBLE else View.GONE

            // Scraps listed = actual listing count (live, not stale Firestore counter)
            binding.tvScrapsListed.text = listings.size.toString()

            // Swaps completed = from user doc (incremented by repo when swap accepted)
            binding.tvSwapsCompleted.text = (user?.swapsCompleted ?: 0).toString()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
