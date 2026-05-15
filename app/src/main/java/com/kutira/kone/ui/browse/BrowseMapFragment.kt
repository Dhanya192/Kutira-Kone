package com.kutira.kone.ui.browse

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.kutira.kone.R
import com.kutira.kone.data.model.FabricScrap
import com.kutira.kone.data.repository.FabricRepository
import com.kutira.kone.databinding.FragmentBrowseBinding
import com.kutira.kone.ui.home.ScrapAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

class BrowseMapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentBrowseBinding? = null
    private val binding get() = _binding!!
    private val repo = FabricRepository()
    private var googleMap: GoogleMap? = null
    private var allScraps: List<FabricScrap> = emptyList()
    private lateinit var listAdapter: ScrapAdapter

    // User's real device location — null until resolved
    private var userLat: Double = 0.0
    private var userLng: Double = 0.0
    private var locationResolved = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.any { it }) {
            resolveUserLocation()
        } else {
            Toast.makeText(requireContext(), "Location permission denied — showing all scraps", Toast.LENGTH_SHORT).show()
            centerMapOnScraps()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        listAdapter = ScrapAdapter(
            currentUserId = repo.currentUserId(),
            onSwapClick = { scrap ->
                Toast.makeText(requireContext(), "Request swap for ${scrap.title}", Toast.LENGTH_SHORT).show()
            },
            onEditClick = { scrap ->
                Toast.makeText(requireContext(), "Edit your scrap: ${scrap.title}", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvNearby.adapter = listAdapter
        binding.rvNearby.layoutManager = LinearLayoutManager(requireContext())

        binding.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnMap -> {
                        binding.mapContainer.visibility = View.VISIBLE
                        binding.rvNearby.visibility = View.GONE
                    }
                    R.id.btnList -> {
                        binding.mapContainer.visibility = View.GONE
                        binding.rvNearby.visibility = View.VISIBLE
                    }
                }
            }
        }

        binding.seekRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val km = progress + 1
                binding.tvRadius.text = if (km >= 1000) "Within ${km / 1000.0}Mm" else "Within ${km}km"
                filterByRadius(km.toDouble())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        // Request location and load data
        requestUserLocation()
        loadData()
    }

    private fun requestUserLocation() {
        val fineGranted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            resolveUserLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun resolveUserLocation() {
        try {
            val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

            if (location != null) {
                userLat = location.latitude
                userLng = location.longitude
                locationResolved = true

                // Move map to user's location once both are ready
                googleMap?.let { map ->
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(userLat, userLng), 5f)
                    )
                    showUserMarker(map)
                }
                // Re-apply radius filter with real coordinates
                val currentKm = (binding.seekRadius.progress + 1).toDouble()
                filterByRadius(currentKm)
            } else {
                // GPS not available yet — center on scraps instead
                centerMapOnScraps()
            }
        } catch (e: SecurityException) {
            centerMapOnScraps()
        }
    }

    /** Fallback: if no GPS, center map on the average position of loaded scraps */
    private fun centerMapOnScraps() {
        val validScraps = allScraps.filter { it.latitude != 0.0 && it.longitude != 0.0 }
        if (validScraps.isNotEmpty()) {
            val avgLat = validScraps.map { it.latitude }.average()
            val avgLng = validScraps.map { it.longitude }.average()
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(avgLat, avgLng), 11f))
        }
    }

    private fun showUserMarker(map: GoogleMap) {
        map.addMarker(
            MarkerOptions()
                .position(LatLng(userLat, userLng))
                .title("📍 You are here")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        )
    }

    private fun loadData() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val rawScraps = try { repo.getAllScraps() } catch (e: Exception) { emptyList() }

            // Geocode any scraps that have a locationName but no real coords,
            // so they show up on the map regardless of how they were uploaded.
            allScraps = withContext(Dispatchers.IO) {
                rawScraps.map { scrap ->
                    if ((scrap.latitude == 0.0 && scrap.longitude == 0.0) && scrap.locationName.isNotBlank()) {
                        try {
                            val geocoder = android.location.Geocoder(requireContext())
                            val results = geocoder.getFromLocationName(scrap.locationName, 1)
                            if (!results.isNullOrEmpty()) {
                                scrap.copy(
                                    latitude = results[0].latitude,
                                    longitude = results[0].longitude
                                )
                            } else scrap
                        } catch (e: Exception) { scrap }
                    } else scrap
                }
            }

            binding.progressBar.visibility = View.GONE
            populateMap(allScraps)
            listAdapter.submitList(allScraps)

            // If location was resolved before data loaded, apply radius filter
            if (locationResolved) {
                val km = (binding.seekRadius.progress + 1).toDouble()
                filterByRadius(km)
            } else {
                centerMapOnScraps()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false

        // If location already resolved, center on user; else wait for data
        if (locationResolved) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(userLat, userLng), 5f))
            showUserMarker(map)
        } else if (allScraps.isNotEmpty()) {
            centerMapOnScraps()
        }
        // Populate markers if data already loaded
        if (allScraps.isNotEmpty()) populateMap(allScraps)
    }

    private fun populateMap(scraps: List<FabricScrap>) {
        val map = googleMap ?: return
        map.clear()

        // Re-draw user marker if location known
        if (locationResolved) showUserMarker(map)

        scraps.forEach { scrap ->
            if (scrap.latitude != 0.0 && scrap.longitude != 0.0) {
                val hue = when (scrap.material) {
                    "Silk"      -> BitmapDescriptorFactory.HUE_VIOLET
                    "Cotton"    -> BitmapDescriptorFactory.HUE_CYAN
                    "Wool"      -> BitmapDescriptorFactory.HUE_ORANGE
                    "Linen"     -> BitmapDescriptorFactory.HUE_YELLOW
                    "Denim"     -> BitmapDescriptorFactory.HUE_BLUE
                    "Polyester" -> BitmapDescriptorFactory.HUE_MAGENTA
                    else        -> BitmapDescriptorFactory.HUE_GREEN
                }
                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(scrap.latitude, scrap.longitude))
                        .title(scrap.title)
                        .snippet("${scrap.material} · ${scrap.color} · ${scrap.sizeMeters}m · by ${scrap.userName}")
                        .icon(BitmapDescriptorFactory.defaultMarker(hue))
                )
            }
        }
    }

    private fun filterByRadius(km: Double) {
        if (!locationResolved) {
            // No GPS — show everything
            populateMap(allScraps)
            listAdapter.submitList(allScraps)
            return
        }

        val filtered = allScraps.filter { s ->
            if (s.latitude == 0.0 && s.longitude == 0.0) false
            else haversineKm(userLat, userLng, s.latitude, s.longitude) <= km
        }
        populateMap(filtered)
        listAdapter.submitList(filtered)

        // Draw radius circle
        googleMap?.addCircle(
            CircleOptions()
                .center(LatLng(userLat, userLng))
                .radius(km * 1000)
                .strokeColor(0x50FF6F00.toInt())
                .fillColor(0x10FF6F00.toInt())
                .strokeWidth(2f)
        )
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
