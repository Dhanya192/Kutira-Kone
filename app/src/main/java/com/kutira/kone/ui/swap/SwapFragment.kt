package com.kutira.kone.ui.swap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.kutira.kone.data.repository.FabricRepository
import com.kutira.kone.databinding.FragmentSwapBinding
import kotlinx.coroutines.launch

class SwapFragment : Fragment() {

    private var _binding: FragmentSwapBinding? = null
    private val binding get() = _binding!!
    private val repo = FabricRepository()
    private lateinit var adapter: SwapRequestAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSwapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SwapRequestAdapter(
            currentUserId = repo.currentUserId(),
            onAccept = { swap -> updateSwap(swap.id, "accepted") },
            onReject = { swap -> updateSwap(swap.id, "rejected") }
        )
        binding.rvRequests.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRequests.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { loadRequests() }
        loadRequests()
    }

    private fun loadRequests() {
        lifecycleScope.launch {
            _binding?.progressBar?.visibility = View.VISIBLE

            val received = try { repo.getMySwapRequests() } catch (e: Exception) { emptyList() }
            val sent = try { repo.getRequestsSentByMe() } catch (e: Exception) { emptyList() }

            val allRequests = (received + sent)
                .distinctBy { it.id }
                .sortedByDescending { it.timestamp }

            if (_binding == null) return@launch

            binding.progressBar.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false

            if (allRequests.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.rvRequests.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.rvRequests.visibility = View.VISIBLE
                adapter.submitList(allRequests)
            }
        }
    }

    private fun updateSwap(swapId: String, status: String) {
        lifecycleScope.launch {
            val result = try {
                repo.updateSwapStatus(swapId, status)
            } catch (e: Exception) {
                Result.failure(e)
            }
            if (!isAdded) return@launch
            result.fold(
                onSuccess = {
                    val msg = if (status == "accepted") "Swap accepted! 🎉" else "Request declined"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    loadRequests()
                },
                onFailure = {
                    Toast.makeText(requireContext(), it.message ?: "Something went wrong", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
