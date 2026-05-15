package com.kutira.kone.data.model

data class FabricScrap(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val title: String = "",
    val description: String = "",
    val material: String = "",   // Silk, Cotton, Wool, etc.
    val color: String = "",
    val sizeMeters: Double = 0.0,
    val imageUrl: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val locationName: String = "",
    val isAvailableForSwap: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "available" // available, swapped, sold
)

data class SwapRequest(
    val id: String = "",
    val requesterId: String = "",
    val requesterName: String = "",
    val ownerId: String = "",
    val scrapId: String = "",
    val scrapTitle: String = "",
    val message: String = "",
    val status: String = "pending", // pending, accepted, rejected
    val timestamp: Long = System.currentTimeMillis()
)

data class DesignIdea(
    val id: Int = 0,
    val title: String = "",
    val description: String = "",
    val materials: List<String> = emptyList(),
    val difficulty: String = "",
    val iconRes: Int = 0
)

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val location: String = "",
    val scrapsListed: Int = 0,
    val swapsCompleted: Int = 0
)
