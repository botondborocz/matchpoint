package org.ttproject.screens

import android.Manifest
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import org.ttproject.AppColors

@Composable
actual fun NativeMap(
    modifier: Modifier,
    locations: List<TTClub>,
    selectedClub: TTClub?,
    userLocationTrigger: Int,
    bottomPadding: Dp,
    isDark: Boolean,
    onMarkerClick: (TTClub) -> Unit,
    onBoundsChanged: (MapBounds) -> Unit
) {
    val context = LocalContext.current

    // --- STATE ---
    // 👇 1. Silently check if they already granted it in a previous session
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }

    val budapest = LatLng(47.4979, 19.0402)
//    val cameraPositionState = rememberCameraPositionState {
//        position = CameraPosition.fromLatLngZoom(budapest, 15f)
//    }

    // 👇 2. Try to grab the exact last known location synchronously
    val initialUserLocation = remember(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                val locationManager = ContextCompat.getSystemService(context, LocationManager::class.java)
                val loc = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                if (loc != null) LatLng(loc.latitude, loc.longitude) else null
            } catch (e: SecurityException) {
                null
            }
        } else null
    }

    // 👇 3. Initialize the camera state!
    val cameraPositionState = rememberCameraPositionState {
        // If we found their location, use it! Otherwise, fallback to Budapest.
        val startingLatLng = initialUserLocation ?: LatLng(47.4979, 19.0402)

        // Notice the 15f zoom! This also fixes the cold-start bounding box performance
        // we talked about earlier by loading fewer markers on frame 1.
        position = CameraPosition.fromLatLngZoom(startingLatLng, 15f)
    }

    // 👇 2. Catch the map bounds whenever the user stops dragging/zooming
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            cameraPositionState.projection?.visibleRegion?.latLngBounds?.let { bounds ->
                onBoundsChanged(
                    MapBounds(
                        north = bounds.northeast.latitude,
                        south = bounds.southwest.latitude,
                        east = bounds.northeast.longitude,
                        west = bounds.southwest.longitude
                    )
                )
            }
        }
    }

    // --- PERMISSIONS ---
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
    }

    // 👇 2. DELETED the LaunchedEffect(Unit) that forced the prompt on startup!

    // --- LIVE LOCATION TRACKING ---
    DisposableEffect(hasLocationPermission) {
        val locationManager = ContextCompat.getSystemService(context, LocationManager::class.java)

        val locationListener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                userLocation = LatLng(loc.latitude, loc.longitude)
            }
        }

        if (hasLocationPermission && locationManager != null) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 2f, locationListener)
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 2f, locationListener)
            } catch (e: SecurityException) {
                // Ignore if location is restricted
            }
        }

        onDispose {
            locationManager?.removeUpdates(locationListener)
        }
    }

    // --- ZOOM TO SELECTED CLUB ---
    LaunchedEffect(selectedClub) {
        if (selectedClub != null) {
            val targetLocation = LatLng(selectedClub.lat, selectedClub.lng)
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(targetLocation, 15f),
                durationMs = 800
            )
        }
    }

    // --- ZOOM TO ME ("Center Me" Button) ---
    // 👇 3. We listen to BOTH the trigger and the permission state!
    LaunchedEffect(userLocationTrigger, hasLocationPermission) {
        if (userLocationTrigger > 0) {
            if (hasLocationPermission) {
                // We have permission! Go ahead and pan the camera.
                try {
                    val locationManager = ContextCompat.getSystemService(context, LocationManager::class.java)
                    val lastLocation = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                    lastLocation?.let { loc ->
                        cameraPositionState.animate(
                            update = CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 15f),
                            durationMs = 800
                        )
                    }
                } catch (e: SecurityException) {
                    // Ignore
                }
            } else {
                // We DON'T have permission yet. Launch the prompt!
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    // ... (The rest of your code: Google Maps JSON and GoogleMap composable remain exactly the same)

    // --- GOOGLE MAPS DARK MODE JSON ---
    val darkMapStyle = """
        [
          {"elementType": "geometry", "stylers": [{"color": "#242f3e"}]},
          {"elementType": "labels.text.fill", "stylers": [{"color": "#746855"}]},
          {"elementType": "labels.text.stroke", "stylers": [{"color": "#242f3e"}]},
          {"featureType": "administrative.locality", "elementType": "labels.text.fill", "stylers": [{"color": "#d59563"}]},
          {"featureType": "poi", "elementType": "labels.text.fill", "stylers": [{"color": "#d59563"}]},
          {"featureType": "poi.park", "elementType": "geometry", "stylers": [{"color": "#263c3f"}]},
          {"featureType": "poi.park", "elementType": "labels.text.fill", "stylers": [{"color": "#6b9a76"}]},
          {"featureType": "road", "elementType": "geometry", "stylers": [{"color": "#38414e"}]},
          {"featureType": "road", "elementType": "geometry.stroke", "stylers": [{"color": "#212a37"}]},
          {"featureType": "road", "elementType": "labels.text.fill", "stylers": [{"color": "#9ca5b3"}]},
          {"featureType": "road.highway", "elementType": "geometry", "stylers": [{"color": "#746855"}]},
          {"featureType": "road.highway", "elementType": "geometry.stroke", "stylers": [{"color": "#1f2835"}]},
          {"featureType": "road.highway", "elementType": "labels.text.fill", "stylers": [{"color": "#f3d19c"}]},
          {"featureType": "water", "elementType": "geometry", "stylers": [{"color": "#17263c"}]},
          {"featureType": "water", "elementType": "labels.text.fill", "stylers": [{"color": "#515c6d"}]},
          {"featureType": "water", "elementType": "labels.text.stroke", "stylers": [{"color": "#17263c"}]}
        ]
    """.trimIndent()

    // 👇 3. Read the Composable color safely here, inside the @Composable function
    val currentAccentColor = AppColors.AccentOrange

    // 👇 4. Pass the color into the remember key so it redraws if the theme changes!
    val normalBitmap = remember(context, currentAccentColor) {
        createClubBitmap(context, isSelected = false, currentAccentColor)
    }
    val selectedBitmap = remember(context, currentAccentColor) {
        createClubBitmap(context, isSelected = true, currentAccentColor)
    }

    // --- THE MAP ---
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        // 👇 Dynamic Bottom Padding applied here!
        contentPadding = PaddingValues(top = 72.dp, bottom = bottomPadding),
        properties = MapProperties(
            isMyLocationEnabled = false,
            // 👇 Apply Dark Mode JSON if app is in dark mode
            mapStyleOptions = if (isDark) MapStyleOptions(darkMapStyle) else null
        ),
        uiSettings = MapUiSettings(
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
            mapToolbarEnabled = false
        )
    ) {

        val normalMarker = remember(normalBitmap) {
            com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(normalBitmap)
        }
        val selectedMarker = remember(selectedBitmap) {
            com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(selectedBitmap)
        }

        // 1. DRAW CUSTOM USER LOCATION MARKER
        userLocation?.let { loc ->
            MarkerComposable(
                keys = arrayOf(loc),
                state = MarkerState(position = loc),
                zIndex = 2f,
                title = "Me"
            ) {
                // Outer Box acts as the touch target and the soft translucent "halo"
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(color = Color(0xFF00D2FF).copy(alpha = 0.2f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Inner Box is the crisp, physical dot
                    Box(
                        modifier = Modifier
                            .size(22.dp) // The size of the actual dot
                            .shadow(elevation = 4.dp, shape = CircleShape) // Drop shadow so it pops over roads
                            .background(color = Color(0xFF00D2FF), shape = CircleShape)
                            .border(width = 3.dp, color = Color.White, shape = CircleShape)
                    )
                }
            }
        }

        // 2. DRAW CLUB MARKERS FAST
        locations.forEach { club ->
            val isSelected = club.id == selectedClub?.id

            // 👇 3. Use the standard 'Marker' instead of 'MarkerComposable'
            Marker(
                state = MarkerState(position = LatLng(club.lat, club.lng)),
                title = club.id,
                zIndex = if (isSelected) 3f else 1f,
                // 👇 4. Pass our pre-drawn, hyper-fast Bitmaps!
                icon = if (isSelected) selectedMarker else normalMarker,
                onClick = {
                    onMarkerClick(club)
                    true
                }
            )
        }
    }
}

private fun createClubBitmap(
    context: android.content.Context,
    isSelected: Boolean,
    accentColor: Color // 👇 1. Add the color as a parameter!
): android.graphics.Bitmap {
    val density = context.resources.displayMetrics.density
    val sizeDp = if (isSelected) 48f else 36f
    val sizePx = (sizeDp * density).toInt()

    val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        // 👇 2. Use the passed parameter instead of AppColors
        color = if (isSelected) {
            accentColor.toArgb()
        } else {
            accentColor.copy(alpha = 0.8f).toArgb()
        }
    }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

    val textPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = (if (isSelected) 22f else 16f) * density
        textAlign = android.graphics.Paint.Align.CENTER
    }

    val yPos = (sizePx / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2)
    canvas.drawText("🏓", sizePx / 2f, yPos, textPaint)

    return bitmap
}