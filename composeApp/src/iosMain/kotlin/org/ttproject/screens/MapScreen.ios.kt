package org.ttproject.screens

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.CoreLocation.CLLocationManager
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKPointAnnotation
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKUserLocation
import platform.MapKit.MKMarkerAnnotationView
import platform.UIKit.UIColor
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.UIImage
import platform.UIKit.UIImageSymbolConfiguration
import platform.UIKit.UIImageSymbolWeightBold
import platform.UIKit.UIUserInterfaceStyle
import platform.darwin.NSObject
import kotlinx.cinterop.useContents
import org.ttproject.AppColors
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse

@OptIn(ExperimentalComposeUiApi::class, ExperimentalForeignApi::class)
@Composable
actual fun NativeMap(
    modifier: Modifier,
    locations: List<TTClub>,
    selectedClub: TTClub?,
    userLocationTrigger: Int,
    bottomPadding: Dp,
    leftPadding: Dp,
    isDark: Boolean,
    onMarkerClick: (TTClub) -> Unit,
    onBoundsChanged: (MapBounds) -> Unit,
    onMapLoaded: () -> Unit
) {
    val currentLocations by rememberUpdatedState(locations)
    val currentOnMarkerClick by rememberUpdatedState(onMarkerClick)
    val currentOnBoundsChanged by rememberUpdatedState(onBoundsChanged)
    val currentOnMapLoaded by rememberUpdatedState(onMapLoaded)

    LaunchedEffect(Unit) {
        currentOnMapLoaded()
    }

    val currentAccentColor by rememberUpdatedState(AppColors.AccentOrange)
    var lastHandledTrigger by remember { mutableStateOf(0) }

    // 👇 1. Just initialize the manager, DO NOT request permission yet!
    val locationManager = remember { CLLocationManager() }

    val mapDelegate = remember {
        object : NSObject(), MKMapViewDelegateProtocol {

            override fun mapView(mapView: MKMapView, regionDidChangeAnimated: Boolean) {
                mapView.region.useContents {
                    val north = center.latitude + (span.latitudeDelta / 2.0)
                    val south = center.latitude - (span.latitudeDelta / 2.0)
                    val east = center.longitude + (span.longitudeDelta / 2.0)
                    val west = center.longitude - (span.longitudeDelta / 2.0)
                    currentOnBoundsChanged(MapBounds(north = north, south = south, east = east, west = west))
                }
            }

            override fun mapView(mapView: MKMapView, didSelectAnnotationView: MKAnnotationView) {
                val tappedTitle = didSelectAnnotationView.annotation?.title
                val clickedClub = currentLocations.find { it.id == tappedTitle }
                if (clickedClub != null) currentOnMarkerClick(clickedClub)
            }

            override fun mapView(mapView: MKMapView, viewForAnnotation: MKAnnotationProtocol): MKAnnotationView? {
                if (viewForAnnotation is MKUserLocation) return null

                val identifier = "CustomOrangePin"
                var annotationView = mapView.dequeueReusableAnnotationViewWithIdentifier(identifier) as? MKMarkerAnnotationView

                if (annotationView == null) {
                    annotationView = MKMarkerAnnotationView(annotation = viewForAnnotation, reuseIdentifier = identifier)
                    annotationView.canShowCallout = false
                } else {
                    annotationView.annotation = viewForAnnotation
                }

                // 👇 2. Read from our state variable, NOT directly from AppColors!
                annotationView.markerTintColor = UIColor.colorWithRed(
                    red = currentAccentColor.red.toDouble(),
                    green = currentAccentColor.green.toDouble(),
                    blue = currentAccentColor.blue.toDouble(),
                    alpha = currentAccentColor.alpha.toDouble()
                )

                annotationView.glyphText = "🏓"
                return annotationView
            }
        }
    }

    UIKitView(
        modifier = modifier,
        factory = {
            val mapView = MKMapView()
            mapView.delegate = mapDelegate
            mapView.showsUserLocation = true

            // 👇 1. Check if iOS has already granted us location access
            val isAuthorized = locationManager.authorizationStatus == kCLAuthorizationStatusAuthorizedWhenInUse ||
                    locationManager.authorizationStatus == kCLAuthorizationStatusAuthorizedAlways

            // 👇 2. Grab the last known iOS location synchronously!
            val userCoord = if (isAuthorized) locationManager.location?.coordinate else null

            // 👇 3. Use their location if we have it, otherwise fallback to Budapest
            // We also shrink the viewing distance to 2000m (tighter zoom) for a faster cold start!
            val startCoord = userCoord ?: CLLocationCoordinate2DMake(47.4979, 19.0402)
            val region = MKCoordinateRegionMakeWithDistance(startCoord, 2000.0, 2000.0)

            mapView.setRegion(region, animated = false)

//            val budapestCoord = CLLocationCoordinate2DMake(47.4979, 19.0402)
//            val region = MKCoordinateRegionMakeWithDistance(budapestCoord, 8000.0, 8000.0)
//            mapView.setRegion(region, animated = false)

            mapView
        },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative
        ),
        update = { mapView ->

            mapView.layoutMargins = UIEdgeInsetsMake(72.0, leftPadding.value.toDouble(), bottomPadding.value.toDouble(), 0.0)
            mapView.overrideUserInterfaceStyle = if (isDark) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight

            // 👇 1. GRAB THE LATEST COLOR
            val currentUIColor = UIColor.colorWithRed(
                red = currentAccentColor.red.toDouble(),
                green = currentAccentColor.green.toDouble(),
                blue = currentAccentColor.blue.toDouble(),
                alpha = currentAccentColor.alpha.toDouble()
            )

            val existingCustomPins = mapView.annotations.filter { it !is MKUserLocation }

            // 👇 2. FORCE COLOR UPDATE ON VISIBLE PINS
            // This actively re-paints the pins that are currently on the screen
            existingCustomPins.forEach { annotation ->
                val view = mapView.viewForAnnotation(annotation as MKAnnotationProtocol) as? MKMarkerAnnotationView
                if (view != null) {
                    view.markerTintColor = currentUIColor
                }
            }

            // 3. PLOT NEW MARKERS (Your existing logic)
            if (existingCustomPins.size != locations.size) {
                mapView.removeAnnotations(existingCustomPins)

                locations.forEach { club ->
                    val annotation = MKPointAnnotation().apply {
                        setCoordinate(CLLocationCoordinate2DMake(club.lat, club.lng))
                        setTitle(club.id)
                    }
                    mapView.addAnnotation(annotation)
                }
            }

            // 4. ZOOM TO SELECTED CLUB (Your existing logic)
            if (selectedClub != null) {
                val annotationToSelect = mapView.annotations.firstOrNull {
                    (it as? MKAnnotationProtocol)?.title == selectedClub.id
                } as? MKAnnotationProtocol

                if (annotationToSelect != null) {
                    mapView.selectAnnotation(annotationToSelect, animated = true)
                    var lat = selectedClub.lat
                    var lng = selectedClub.lng

                    if (leftPadding.value > 0.0) {
                        val screenWidthPoints = mapView.bounds.useContents { size.width }
                        if (screenWidthPoints > 0.0) {
                            // MKCoordinateRegion span estimation for 1000m
                            // 1 degree latitude is approx 111,000 meters.
                            // 1 degree longitude is approx 111,000 * cos(lat) meters.
                            // Zoom region is 1000m x 1000m.
                            val lngDelta = 1000.0 / (111000.0 * kotlin.math.cos(lat * kotlin.math.PI / 180.0))
                            // We shift center longitude to the left
                            lng -= (leftPadding.value / 2.0 / screenWidthPoints) * lngDelta
                        }
                    }

                    val centerCoord = CLLocationCoordinate2DMake(lat, lng)
                    val region = MKCoordinateRegionMakeWithDistance(centerCoord, 1000.0, 1000.0)
                    mapView.setRegion(region, animated = true)
                }
            } else {
                mapView.selectedAnnotations.forEach {
                    mapView.deselectAnnotation(it as MKAnnotationProtocol, animated = true)
                }
            }

            // 5. DEFERRED LOCATION PROMPT & ZOOM (Your existing logic)
            if (userLocationTrigger > lastHandledTrigger) {
                lastHandledTrigger = userLocationTrigger

                if (locationManager.authorizationStatus == kCLAuthorizationStatusNotDetermined) {
                    locationManager.requestWhenInUseAuthorization()
                } else {
                    val userLoc = mapView.userLocation.location
                    if (userLoc != null) {
                        val region = MKCoordinateRegionMakeWithDistance(userLoc.coordinate, 1000.0, 1000.0)
                        mapView.setRegion(region, animated = true)
                    }
                }
            }
        }
    )
}