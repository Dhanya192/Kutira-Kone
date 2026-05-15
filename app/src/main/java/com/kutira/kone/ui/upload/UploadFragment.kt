package com.kutira.kone.ui.upload

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.kutira.kone.data.model.FabricScrap
import com.kutira.kone.data.repository.FabricRepository
import com.kutira.kone.databinding.FragmentUploadBinding
import com.kutira.kone.databinding.DialogMapPickerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UploadFragment : Fragment() {

    private var _binding: FragmentUploadBinding? = null
    private val binding get() = _binding!!
    private val repo = FabricRepository()

    private var selectedImageUri: Uri? = null
    private var currentLat = 0.0
    private var currentLng = 0.0
    private var resolvedUserName: String = ""

    // Ephemeral dialog pin state
    private var dialogPinLat = 0.0
    private var dialogPinLng = 0.0
    private var dialogPinName = ""

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = result.data?.data
            selectedImageUri?.let {
                binding.ivPreview.setImageURI(it)
                binding.tvPickImage.visibility = View.GONE
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pickImage()
        else Toast.makeText(requireContext(), "Permission required", Toast.LENGTH_SHORT).show()
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.any { it }) getLocation()
        else Toast.makeText(
            requireContext(),
            "Location permission denied. Use Drop Pin or type manually.",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUploadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val materials = listOf("Cotton","Silk","Wool","Polyester","Linen","Denim","Velvet","Other")
        binding.spinnerMaterial.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, materials)

        val colors = listOf(
            "Red","Blue","Green","Yellow","Orange",
            "Purple","Pink","White","Black","Brown","Grey","Multi"
        )
        binding.spinnerColor.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, colors)

        binding.cardPickImage.setOnClickListener { requestImagePermission() }
        binding.btnGetLocation.setOnClickListener { requestLocationPermission() }
        binding.btnDropPin.setOnClickListener { openFullScreenMapPicker() }
        binding.btnRepin.setOnClickListener { openFullScreenMapPicker() }
        binding.btnUpload.setOnClickListener { validateAndUpload() }
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        lifecycleScope.launch {
            val user = repo.getCurrentUser()
            resolvedUserName = when {
                user != null && user.name.isNotBlank() -> user.name
                else -> {
                    val email = repo.currentUserEmail()
                    email.substringBefore("@")
                        .replaceFirstChar { it.uppercase() }
                        .replace(".", " ")
                        .replace("_", " ")
                        .trim()
                        .ifEmpty { "Artisan" }
                }
            }
        }
    }

    // ── Full-Screen Map Picker Dialog ──────────────────────────────

    private fun openFullScreenMapPicker() {
        val dialog = Dialog(requireContext(), android.R.style.Theme_DeviceDefault_NoActionBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val dialogBinding = DialogMapPickerBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.dialogToolbar.setNavigationOnClickListener { dialog.dismiss() }

        // Seed state from any existing confirmed location
        dialogPinLat = currentLat
        dialogPinLng = currentLng
        dialogPinName = ""

        // ── Use MapView directly — no FragmentManager, no crash ──────
        val mapView: MapView = dialogBinding.mapViewPicker
        mapView.onCreate(null)
        mapView.onResume()

        var dialogMarker: com.google.android.gms.maps.model.Marker? = null
        var liveMap: GoogleMap? = null

        mapView.getMapAsync { map ->
            liveMap = map
            map.uiSettings.isZoomControlsEnabled = true

            val startPos = if (dialogPinLat != 0.0) LatLng(dialogPinLat, dialogPinLng)
                           else LatLng(20.5937, 78.9629)
            val zoom = if (dialogPinLat != 0.0) 13f else 4f
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(startPos, zoom))

            // Restore existing pin
            if (dialogPinLat != 0.0) {
                dialogMarker = map.addMarker(
                    MarkerOptions()
                        .position(LatLng(dialogPinLat, dialogPinLng))
                        .title("📍 Your Location")
                        .draggable(true)
                )
                dialogBinding.btnConfirmPin.isEnabled = true
                val existing = binding.tvLocation.text.toString().removePrefix("📍 ").trim()
                dialogBinding.tvDialogLocation.text = "📍 $existing"
            }

            map.setOnMapClickListener { latLng ->
                dialogMarker?.remove()
                dialogMarker = map.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title("📍 Your Location")
                        .draggable(true)
                )
                dialogPinLat = latLng.latitude
                dialogPinLng = latLng.longitude
                dialogBinding.tvDialogLocation.text = "⏳ Resolving address..."
                dialogBinding.btnConfirmPin.isEnabled = false

                lifecycleScope.launch(Dispatchers.IO) {
                    val name = reverseGeocode(latLng.latitude, latLng.longitude)
                    dialogPinName = name
                    withContext(Dispatchers.Main) {
                        dialogBinding.tvDialogLocation.text = "📍 $name"
                        dialogBinding.btnConfirmPin.isEnabled = true
                    }
                }
            }

            map.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
                override fun onMarkerDragStart(m: com.google.android.gms.maps.model.Marker) {}
                override fun onMarkerDrag(m: com.google.android.gms.maps.model.Marker) {}
                override fun onMarkerDragEnd(m: com.google.android.gms.maps.model.Marker) {
                    val pos = m.position
                    dialogPinLat = pos.latitude
                    dialogPinLng = pos.longitude
                    dialogBinding.tvDialogLocation.text = "⏳ Resolving..."
                    dialogBinding.btnConfirmPin.isEnabled = false
                    lifecycleScope.launch(Dispatchers.IO) {
                        val name = reverseGeocode(pos.latitude, pos.longitude)
                        dialogPinName = name
                        withContext(Dispatchers.Main) {
                            dialogBinding.tvDialogLocation.text = "📍 $name"
                            dialogBinding.btnConfirmPin.isEnabled = true
                        }
                    }
                }
            })
        }

        // ── Search bar: geocode typed text and move map ───────────────
        fun doSearch() {
            val query = dialogBinding.etSearchLocation.text.toString().trim()
            if (query.isEmpty()) return

            // Hide keyboard
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(dialogBinding.etSearchLocation.windowToken, 0)

            dialogBinding.tvDialogLocation.text = "⏳ Searching…"
            dialogBinding.btnConfirmPin.isEnabled = false

            lifecycleScope.launch(Dispatchers.IO) {
                val result = geocodeAddress(query)
                withContext(Dispatchers.Main) {
                    val map = liveMap
                    if (result.first != 0.0 && map != null) {
                        val latLng = LatLng(result.first, result.second)
                        dialogMarker?.remove()
                        dialogMarker = map.addMarker(
                            MarkerOptions()
                                .position(latLng)
                                .title("📍 $query")
                                .draggable(true)
                        )
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f))
                        dialogPinLat = result.first
                        dialogPinLng = result.second

                        // Reverse-geocode for a clean name
                        val name = withContext(Dispatchers.IO) { reverseGeocode(result.first, result.second) }
                        dialogPinName = name
                        dialogBinding.tvDialogLocation.text = "📍 $name"
                        dialogBinding.btnConfirmPin.isEnabled = true
                    } else {
                        dialogBinding.tvDialogLocation.text = "⚠️ Location not found — try a different name"
                        Toast.makeText(requireContext(), "Location not found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialogBinding.btnSearchGo.setOnClickListener { doSearch() }
        dialogBinding.etSearchLocation.setOnEditorActionListener { _, _, _ ->
            doSearch(); true
        }

        dialogBinding.btnConfirmPin.setOnClickListener {
            if (dialogPinLat != 0.0) {
                currentLat = dialogPinLat
                currentLng = dialogPinLng
                val locationText = dialogPinName.ifEmpty {
                    "%.4f, %.4f".format(currentLat, currentLng)
                }
                binding.tvLocation.text = "📍 $locationText"
                binding.etManualLocation.setText(locationText)
                binding.cardPinConfirm.visibility = View.VISIBLE
                binding.tvPinConfirmText.text = "📍 $locationText"
                binding.tvLocationHint.visibility = View.GONE
                binding.btnDropPin.text = "🗺️ Change Pin"
                Toast.makeText(requireContext(), "Location pinned ✓", Toast.LENGTH_SHORT).show()
            }
            mapView.onPause()
            mapView.onDestroy()
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            try { mapView.onPause(); mapView.onDestroy() } catch (_: Exception) {}
        }

        dialog.show()
    }

    private suspend fun reverseGeocode(lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(requireContext())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                listOfNotNull(
                    addresses[0].locality,
                    addresses[0].subAdminArea,
                    addresses[0].adminArea
                ).distinct().take(2).joinToString(", ").ifEmpty { "Pin location" }
            } else "Pin location"
        } catch (e: Exception) {
            "%.4f, %.4f".format(lat, lng)
        }
    }

    // ── GPS Detect Location ────────────────────────────────────────

    private fun requestImagePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED)
            pickImage()
        else permissionLauncher.launch(permission)
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    private fun getLocation() {
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "Permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        if (location != null) {
            currentLat = location.latitude
            currentLng = location.longitude
            binding.tvLocation.text = "⏳ Getting location..."

            lifecycleScope.launch(Dispatchers.IO) {
                val locationName = reverseGeocode(currentLat, currentLng)
                withContext(Dispatchers.Main) {
                    binding.tvLocation.text = "📍 $locationName"
                    binding.etManualLocation.setText(locationName)
                    binding.btnGetLocation.text = "📡 Location Set ✓"
                    binding.cardPinConfirm.visibility = View.VISIBLE
                    binding.tvPinConfirmText.text = "📍 $locationName (GPS)"
                    binding.tvLocationHint.visibility = View.GONE
                }
            }
        } else {
            Toast.makeText(
                requireContext(),
                "Could not detect GPS. Try outdoors, use Drop Pin, or type manually.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ── Upload ─────────────────────────────────────────────────────

    private fun validateAndUpload() {
        val title = binding.etTitle.text.toString().trim()
        val sizeText = binding.etSize.text.toString().trim()
        val manualLocation = binding.etManualLocation.text.toString().trim()

        if (title.isEmpty()) { binding.etTitle.error = "Required"; return }
        if (sizeText.isEmpty()) { binding.etSize.error = "Required"; return }
        if (selectedImageUri == null) {
            Toast.makeText(requireContext(), "Please select a photo", Toast.LENGTH_SHORT).show()
            return
        }

        val size = sizeText.toDoubleOrNull() ?: run {
            binding.etSize.error = "Invalid number"
            return
        }

        if (currentLat == 0.0 && currentLng == 0.0 && manualLocation.isEmpty()) {
            Toast.makeText(requireContext(), "Please set a location (Detect, Drop Pin, or type manually)", Toast.LENGTH_LONG).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnUpload.isEnabled = false
        binding.btnUpload.text = "Uploading..."

        lifecycleScope.launch {
            try {
                // Geocode manual text if no GPS/pin coords, so BrowseMap can plot it
                val finalLat: Double
                val finalLng: Double
                val finalLocationName: String

                if (currentLat != 0.0 && currentLng != 0.0) {
                    finalLat = currentLat
                    finalLng = currentLng
                    finalLocationName = manualLocation.ifEmpty {
                        binding.tvLocation.text.toString()
                            .removePrefix("📍 ").removePrefix("⏳ ").trim()
                            .ifEmpty { "Unknown location" }
                    }
                } else {
                    // Geocode the manually-entered address so map can show it
                    binding.btnUpload.text = "Geocoding location..."
                    val (lat, lng) = withContext(Dispatchers.IO) { geocodeAddress(manualLocation) }
                    finalLat = lat
                    finalLng = lng
                    finalLocationName = manualLocation
                    if (lat != 0.0) { currentLat = lat; currentLng = lng }
                }

                binding.btnUpload.text = "Uploading image..."

                val imageUrl = withContext(Dispatchers.IO) {
                    repo.uploadImageAndGetUrl(requireContext(), selectedImageUri!!)
                }

                if (imageUrl == null) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnUpload.isEnabled = true
                    binding.btnUpload.text = "Upload"
                    Toast.makeText(requireContext(), "Image upload failed. Check Logcat for GCS errors.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                binding.btnUpload.text = "Saving..."

                val userId = repo.currentUserId()
                if (userId.isEmpty()) {
                    Toast.makeText(requireContext(), "Login required", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val userName = resolvedUserName.ifEmpty {
                    repo.getCurrentUser()?.name?.takeIf { it.isNotBlank() }
                        ?: repo.currentUserEmail().substringBefore("@")
                            .replaceFirstChar { it.uppercase() }
                            .ifEmpty { "Artisan" }
                }

                val scrap = FabricScrap(
                    userId = userId,
                    userName = userName,
                    title = title,
                    description = binding.etDescription.text.toString(),
                    material = binding.spinnerMaterial.selectedItem.toString(),
                    color = binding.spinnerColor.selectedItem.toString(),
                    sizeMeters = size,
                    imageUrl = imageUrl,
                    latitude = finalLat,
                    longitude = finalLng,
                    locationName = finalLocationName,
                    isAvailableForSwap = binding.switchSwap.isChecked,
                    status = "available"
                )

                val result = repo.uploadScrap(scrap)

                binding.progressBar.visibility = View.GONE
                binding.btnUpload.isEnabled = true
                binding.btnUpload.text = "Upload"

                result.fold(
                    onSuccess = {
                        Toast.makeText(requireContext(), "Uploaded successfully! 🎉", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    },
                    onFailure = { e ->
                        Toast.makeText(requireContext(), "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                )

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnUpload.isEnabled = true
                binding.btnUpload.text = "Upload"
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun geocodeAddress(address: String): Pair<Double, Double> {
        return try {
            val geocoder = Geocoder(requireContext())
            val results = geocoder.getFromLocationName(address, 1)
            if (!results.isNullOrEmpty()) Pair(results[0].latitude, results[0].longitude)
            else Pair(0.0, 0.0)
        } catch (e: Exception) {
            Pair(0.0, 0.0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
