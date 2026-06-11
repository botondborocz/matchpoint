import SwiftUI
import MapKit
import shared
import PhotosUI

// MARK: - Location Identifiable Conformance for SwiftUI sheets
extension Location: @retroactive Identifiable {
}

// MARK: - Native Annotation
class ClubAnnotation: NSObject, MKAnnotation {
    let id: String
    let coordinate: CLLocationCoordinate2D
    let title: String?
    let subtitle: String?
    let location: Location
    
    init(location: Location) {
        self.id = location.id ?? "\(location.latitude),\(location.longitude)"
        self.coordinate = CLLocationCoordinate2D(latitude: location.latitude, longitude: location.longitude)
        self.title = location.name
        self.subtitle = "\(location.tableCount) tables"
        self.location = location
    }
}

// MARK: - MKMapView Representable
struct MKMapViewRepresentable: UIViewRepresentable {
    let locations: [Location]
    @Binding var selectedLocation: Location?
    @Binding var centerCoordinate: CLLocationCoordinate2D
    @Binding var trackingUserLocation: Bool
    let onRegionChanged: (MKCoordinateRegion) -> Void
    let onAnnotationSelected: (Location) -> Void
    
    class Coordinator: NSObject, MKMapViewDelegate {
        var parent: MKMapViewRepresentable
        var lastSelectedId: String? = nil
        
        init(_ parent: MKMapViewRepresentable) {
            self.parent = parent
        }
        
        func mapView(_ mapView: MKMapView, regionDidChangeAnimated: Bool) {
            parent.centerCoordinate = mapView.centerCoordinate
            parent.onRegionChanged(mapView.region)
        }
        
        func mapView(_ mapView: MKMapView, didSelect view: MKAnnotationView) {
            guard let annotation = view.annotation as? ClubAnnotation else { return }
            lastSelectedId = annotation.id
            parent.onAnnotationSelected(annotation.location)
        }
        
        func mapView(_ mapView: MKMapView, didDeselect view: MKAnnotationView) {
            guard let annotation = view.annotation as? ClubAnnotation else { return }
            if lastSelectedId == annotation.id {
                lastSelectedId = nil
                parent.selectedLocation = nil
            }
        }
        
        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            if annotation is MKUserLocation { return nil }
            
            let identifier = "TableTennisPin"
            var view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier) as? MKMarkerAnnotationView
            
            if view == nil {
                view = MKMarkerAnnotationView(annotation: annotation, reuseIdentifier: identifier)
                view?.canShowCallout = false
            } else {
                view?.annotation = annotation
            }
            
            view?.markerTintColor = UIColor(red: 255/255, green: 107/255, blue: 53/255, alpha: 1.0) // Branded Orange
            view?.glyphText = "🏓"
            
            return view
        }
    }
    
    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.delegate = context.coordinator
        mapView.showsUserLocation = true
        
        // Initial zoom to Budapest or user location
        let initialRegion = MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 47.4979, longitude: 19.0402),
            span: MKCoordinateSpan(latitudeDelta: 0.1, longitudeDelta: 0.1)
        )
        mapView.setRegion(initialRegion, animated: false)
        return mapView
    }
    
    func updateUIView(_ uiView: MKMapView, context: Context) {
        // Handle annotations difference
        let currentAnnotations = uiView.annotations.compactMap { $0 as? ClubAnnotation }
        let currentIds = Set(currentAnnotations.map { $0.id })
        let newIds = Set(locations.map { $0.id ?? "\($0.latitude),\($0.longitude)" })
        
        if currentIds != newIds {
            uiView.removeAnnotations(currentAnnotations)
            let newAnnotations = locations.map { ClubAnnotation(location: $0) }
            uiView.addAnnotations(newAnnotations)
        }
        
        // Handle selection
        if let selected = selectedLocation {
            let selectedId = selected.id ?? "\(selected.latitude),\(selected.longitude)"
            let found = uiView.annotations.compactMap { $0 as? ClubAnnotation }.first { $0.id == selectedId }
            
            if let annotation = found {
                if !uiView.selectedAnnotations.contains(where: { ($0 as? ClubAnnotation)?.id == selectedId }) {
                    uiView.selectAnnotation(annotation, animated: true)
                }
                
                // Zoom to selection
                let region = MKCoordinateRegion(
                    center: annotation.coordinate,
                    span: MKCoordinateSpan(latitudeDelta: 0.008, longitudeDelta: 0.008)
                )
                uiView.setRegion(region, animated: true)
            }
        } else {
            uiView.selectedAnnotations.forEach { uiView.deselectAnnotation($0, animated: true) }
        }
        
        // Handle User Location Zoom
        if trackingUserLocation {
            DispatchQueue.main.async {
                trackingUserLocation = false
                if let userLoc = uiView.userLocation.location {
                    let region = MKCoordinateRegion(
                        center: userLoc.coordinate,
                        span: MKCoordinateSpan(latitudeDelta: 0.015, longitudeDelta: 0.015)
                    )
                    uiView.setRegion(region, animated: true)
                }
            }
        }
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
}

// MARK: - Custom Sheet State Enum
enum SheetState {
    case collapsed, medium, large
    
    func height(in max: CGFloat) -> CGFloat {
        switch self {
        case .collapsed: return 260
        case .medium: return max * 0.5
        case .large: return max
        }
    }
}

// MARK: - Native SwiftUI Map Screen
struct NativeMapScreenView: View {
    @ObservedObject var appState: AppState
    
    // Core KMP Adapters
    @State private var helper: IosLocationViewModelHelper? = nil
    @State private var tokenStorage: TokenStorage? = nil
    
    // UI states
    @State private var locations: [Location] = []
    @State private var reviews: [TTReview] = []
    @State private var isUiLoading = true
    @State private var errorMessage: String? = nil
    
    // Selection and sheets
    @State private var selectedLocation: Location? = nil
    @State private var showNearbySheet = true
    
    // Map control
    @State private var centerCoordinate = CLLocationCoordinate2D(latitude: 47.4979, longitude: 19.0402)
    @State private var mapRegion = MKCoordinateRegion()
    @State private var trackingUserLocation = true
    @State private var userCoordinate: CLLocationCoordinate2D? = nil
    
    // Filters and Search
    @State private var searchQuery = ""
    @State private var selectedTags: Set<String> = []
    @State private var isFilterPanelExpanded = false
    
    // Creation Modals
    @State private var isPickingLocation = false
    @State private var isAddingTable = false
    @State private var isAddingReview = false
    
    // Navigation
    @State private var showMapSelectionActionSheet = false
    @State private var navigationTargetClub: Location? = nil
    
    private let locationManager = CLLocationManager()
    
    // Color Palette
    private var colorAccent: Color { Color(hex: "#FF6B35") }
    
    // Custom Sheet Tracking
    @State private var nearbySheetState: SheetState = .collapsed
    @State private var nearbyDragOffset: CGFloat = 0
    
    // Modal detents
    @State private var selectedDetailsDetent: PresentationDetent = .medium
    @State private var selectedAddTableDetent: PresentationDetent = .medium
    
    var body: some View {
        NavigationStack {
            GeometryReader { geo in
                ZStack(alignment: .bottom) {
                    // MARK: 1. Native Map
                    MKMapViewRepresentable(
                        locations: filteredLocations,
                        selectedLocation: $selectedLocation,
                        centerCoordinate: $centerCoordinate,
                        trackingUserLocation: $trackingUserLocation,
                        onRegionChanged: { region in
                            mapRegion = region
                        },
                        onAnnotationSelected: { loc in
                            withAnimation(.spring()) {
                                selectedLocation = loc
                                if let id = loc.id {
                                    helper?.loadReviewsForClub(locationId: id)
                                }
                            }
                        }
                    )
                    .ignoresSafeArea()
                    
                    // MARK: 2. Crosshair for Picking Table Location
                    if isPickingLocation {
                        GeometryReader { pickGeo in
                            Image(systemName: "plus")
                                .font(.system(size: 36, weight: .thin))
                                .foregroundColor(colorAccent)
                                .background(
                                    Circle()
                                        .fill(Color.black.opacity(0.2))
                                        .frame(width: 44, height: 44)
                                )
                                .position(x: pickGeo.size.width / 2, y: pickGeo.size.height / 2)
                                .transition(.scale)
                        }
                        
                        // Instruction overlay at bottom
                        VStack(spacing: 12) {
                            Text("Drag the map to place the crosshair exactly over the table.")
                                .font(.subheadline.weight(.medium))
                                .foregroundColor(.primary)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal)
                            
                            HStack(spacing: 20) {
                                Button("Cancel", role: .cancel) {
                                    withAnimation { isPickingLocation = false }
                                }
                                
                                Button("Confirm") {
                                    withAnimation {
                                        isPickingLocation = false
                                        isAddingTable = true
                                        appState.isTabBarHidden = true
                                    }
                                }
                                .buttonStyle(.borderedProminent)
                                .tint(colorAccent)
                            }
                        }
                        .padding()
                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
                        .padding()
                        .padding(.bottom, 40)
                    }
                    
                    // MARK: 3. Floating Combined Toolbar (Center Me & Add)
                    if !isPickingLocation {
                        HStack {
                            Spacer()
                            combinedToolbar
                                .padding(.trailing, 16)
                                .padding(.bottom, nearbySheetState == .large ? 30 : nearbySheetState.height(in: geo.size.height) - nearbyDragOffset + 20)
                        }
                    }
                    
                    // MARK: 4. Custom Overlay (Nearby Clubs)
                    // (This effectively replaces the native .sheet and sits perfectly above the TabBar)
                    if showNearbySheet {
                        VStack(spacing: 0) {
                            // Header Drag Handle
                            VStack {
                                Capsule()
                                    .fill(Color.secondary.opacity(0.4))
                                    .frame(width: 40, height: 5)
                                    .padding(.top, 10)
                                    .padding(.bottom, 10)
                            }
                            .frame(maxWidth: .infinity)
                            .contentShape(Rectangle()) // Ensures the whole header is draggable
                            .gesture(
                                DragGesture()
                                    .onChanged { value in
                                        let newOffset = value.translation.height
                                        // Add drag resistance if trying to pull above .large
                                        if nearbySheetState == .large && newOffset < 0 {
                                            nearbyDragOffset = newOffset * 0.2
                                        } else {
                                            nearbyDragOffset = newOffset
                                        }
                                    }
                                    .onEnded { value in
                                        let predictedEnd = value.predictedEndTranslation.height
                                        withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                                            nearbyDragOffset = 0
                                            
                                            if predictedEnd > 100 { // Dragged down
                                                if nearbySheetState == .large { nearbySheetState = .medium }
                                                else { nearbySheetState = .collapsed }
                                            } else if predictedEnd < -100 { // Dragged up
                                                if nearbySheetState == .collapsed { nearbySheetState = .medium }
                                                else { nearbySheetState = .large }
                                            }
                                        }
                                    }
                            )
                            
                            // Content
                            nearbySheetContent
                        }
                        .frame(height: max(0, nearbySheetState.height(in: geo.size.height) - nearbyDragOffset))
                        .background(nearbySheetState == .large ? Color(.systemBackground) : Color(.systemBackground).opacity(0.95))
                        .cornerRadius(nearbySheetState == .large ? 0 : 20, corners: [.topLeft, .topRight])
                        .shadow(color: Color.black.opacity(nearbySheetState == .large ? 0 : 0.15), radius: 10, x: 0, y: -3)
                        .ignoresSafeArea(edges: nearbySheetState == .large ? .top : [])
                    }
                }
            }
            .navigationTitle(showNearbySheet ? "Nearby Clubs" : "Map")
            .navigationBarTitleDisplayMode(.inline)
            .searchable(text: $searchQuery, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search venues...")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: {
                        withAnimation {
                            isFilterPanelExpanded.toggle()
                        }
                    }) {
                        Image(systemName: "line.3.horizontal.decrease.circle\(isFilterPanelExpanded || !selectedTags.isEmpty ? ".fill" : "")")
                    }
                    .tint(!selectedTags.isEmpty ? colorAccent : .primary)
                }
            }
            // MARK: 5. Selected Club Details Native Sheet
            .sheet(item: $selectedLocation) { club in
                NavigationStack {
                    detailsSheetContent(club: club)
                        .navigationTitle(club.name)
                        .navigationBarTitleDisplayMode(.inline)
                        .toolbar {
                            ToolbarItem(placement: .cancellationAction) {
                                Button("Close") {
                                    selectedLocation = nil
                                    appState.isTabBarHidden = false
                                }
                            }
                            ToolbarItem(placement: .primaryAction) {
                                Button(action: {
                                    navigationTargetClub = club
                                    handleNavigationClick(club: club)
                                }) {
                                    Label("Navigate", systemImage: "arrow.triangle.turn.up.right.diamond.fill")
                                }
                                .tint(colorAccent)
                            }
                        }
                }
                .presentationDetents([.medium, .large], selection: $selectedDetailsDetent)
                .presentationDragIndicator(.visible)
                .presentationBackgroundInteraction(.enabled(upThrough: .medium))
                .presentationBackground(selectedDetailsDetent == .large ? Color(.systemBackground) : .clear)
                .confirmationDialog("Navigate via", isPresented: $showMapSelectionActionSheet, titleVisibility: .visible) {
                    Button("Apple Maps") {
                        if let club = navigationTargetClub { openMapsApp(choice: "apple", club: club) }
                    }
                    Button("Google Maps") {
                        if let club = navigationTargetClub { openMapsApp(choice: "google", club: club) }
                    }
                    Button("Cancel", role: .cancel) {}
                } message: {
                    Text("Select your preferred navigation app.")
                }
            }
        }
        .tint(colorAccent)
        .sheet(isPresented: $isAddingTable) {
            VStack(spacing: 0) {
                AddTableModalView(
                    lat: centerCoordinate.latitude,
                    lng: centerCoordinate.longitude,
                    colorAccent: colorAccent,
                    colorSurface: Color(hex: "#162032"),
                    helper: helper,
                    onDismiss: {
                        isAddingTable = false
                        appState.isTabBarHidden = false
                    }
                )
                .background(selectedAddTableDetent == .large ? AnyShapeStyle(Color(.systemBackground)) : AnyShapeStyle(.ultraThinMaterial))
                .cornerRadius(selectedAddTableDetent == .large ? 0 : 20)
                .shadow(color: Color.black.opacity(selectedAddTableDetent == .large ? 0 : 0.15), radius: 10, x: 0, y: -3)
                
                if selectedAddTableDetent != .large {
                    Color.clear.frame(height: 100)
                }
            }
            .ignoresSafeArea(edges: selectedAddTableDetent == .large ? [] : [.bottom])
            .presentationDetents([.medium, .large], selection: $selectedAddTableDetent)
            .presentationDragIndicator(.visible)
            .presentationBackgroundInteraction(.enabled(upThrough: .medium))
            .presentationBackground(selectedAddTableDetent == .large ? Color(.systemBackground) : .clear)
        }
        .sheet