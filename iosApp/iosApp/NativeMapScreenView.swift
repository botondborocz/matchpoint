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
        .sheet(isPresented: $isAddingReview) {
            if let club = selectedLocation {
                AddReviewModalView(
                    locationId: club.id ?? "",
                    clubName: club.name,
                    colorAccent: colorAccent,
                    colorSurface: Color(hex: "#162032"),
                    helper: helper,
                    onDismiss: {
                        isAddingReview = false
                        if let id = club.id {
                            helper?.loadReviewsForClub(locationId: id)
                        }
                    }
                )
            }
        }
        .onAppear {
            initializeKMPComponents()
            setupLocationUpdates()
            updateTabBarVisibility()
        }
        .onChange(of: nearbySheetState) { _ in updateTabBarVisibility() }
        .onChange(of: selectedLocation) { _ in updateTabBarVisibility() }
        .onChange(of: selectedDetailsDetent) { _ in updateTabBarVisibility() }
        .onChange(of: isAddingTable) { _ in updateTabBarVisibility() }
        .onChange(of: selectedAddTableDetent) { _ in updateTabBarVisibility() }
        .onChange(of: isPickingLocation) { _ in updateTabBarVisibility() }
    }
    
    // MARK: - Nearby Sheet Content
    
    private var nearbySheetContent: some View {
        VStack(spacing: 0) {
            // Active filter tokens
            if !selectedTags.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(Array(selectedTags), id: \.self) { tag in
                            Button(action: {
                                withAnimation { 
                                    _ = selectedTags.remove(tag) 
                                }
                            }) {
                                HStack(spacing: 4) {
                                    Text(tag).font(.caption)
                                    Image(systemName: "xmark.circle.fill").font(.caption2)
                                }
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(colorAccent)
                            .clipShape(Capsule())
                        }
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 8)
                }
            }
            
            // Inline expanded filter panel
            if isFilterPanelExpanded {
                VStack(alignment: .leading, spacing: 12) {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 14) {
                            filterSection(title: "Type & Access", options: ["Indoor", "Outdoor", "Free", "Paid"])
                            filterSection(title: "Table Quality", options: ["Perfect surface", "Sturdy net", "Worn out / Damaged", "Torn net", "Slippery surface"])
                            filterSection(title: "Environment", options: ["Spacious", "Wind-protected", "Good lighting", "Cramped space", "Glaring sun", "Poor lighting"])
                            filterSection(title: "Amenities", options: ["Drinking fountain", "Restroom available", "Usually crowded", "Quiet & Chill"])
                        }
                        .padding(.bottom, 8)
                    }
                    .frame(maxHeight: 180)
                    
                    Divider()
                }
                .padding(.horizontal)
                .padding(.bottom, 8)
            }
            
            // Clubs list
            List {
                Section {
                    if isUiLoading && locations.isEmpty {
                        HStack {
                            Spacer()
                            ProgressView()
                            Spacer()
                        }
                        .listRowBackground(Color.clear)
                    } else if filteredLocations.isEmpty {
                        Text("No table tennis clubs found.")
                            .foregroundColor(.secondary)
                            .font(.subheadline)
                            .frame(maxWidth: .infinity)
                            .listRowBackground(Color.clear)
                    } else {
                        ForEach(filteredLocations, id: \.id) { loc in
                            Button(action: {
                                withAnimation(.spring()) {
                                    selectedLocation = loc
                                    if let id = loc.id {
                                        helper?.loadReviewsForClub(locationId: id)
                                    }
                                }
                            }) {
                                HStack(spacing: 14) {
                                    RoundedRectangle(cornerRadius: 8)
                                        .fill(Color.secondary.opacity(0.15))
                                        .frame(width: 44, height: 44)
                                        .overlay(Text("🏓").font(.title2))
                                    
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(loc.name)
                                            .font(.subheadline.weight(.semibold))
                                            .foregroundColor(.primary)
                                        
                                        Text("\(formattedDistance(lat: loc.latitude, lng: loc.longitude)) • \(loc.tableCount) Tables")
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .font(.caption.weight(.semibold))
                                        .foregroundColor(.secondary)
                                }
                            }
                        }
                    }
                } header: {
                    Text("Nearby Clubs")
                        .font(.headline)
                        .foregroundColor(.primary)
                        .textCase(nil)
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
        }
    }
    
    // MARK: - Details Sheet Content
    
    private func detailsSheetContent(club: Location) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                // Info bar
                HStack {
                    Label(
                        club.type == LocationType.indoor ? "Indoor Arena" : "Outdoor Space",
                        systemImage: club.type == LocationType.indoor ? "house.fill" : "leaf.fill"
                    )
                    Spacer()
                    Text(club.isFree ? "Free Access" : "Paid Arena")
                        .fontWeight(.bold)
                        .foregroundColor(colorAccent)
                }
                .font(.subheadline)
                .foregroundColor(.primary)
                
                HStack {
                    Label(
                        "\(formattedDistance(lat: club.latitude, lng: club.longitude))",
                        systemImage: "location.fill"
                    )
                    Text("•")
                    Label("\(club.tableCount) Tables", systemImage: "tablecells")
                }
                .font(.caption)
                .foregroundColor(.secondary)
                
                if let created = club.createdBy {
                    Label("Added by: \(created)", systemImage: "person.fill")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                Divider()
                
                // Image gallery
                if !club.imageUrls.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 12) {
                            ForEach(club.imageUrls, id: \.self) { url in
                                AsyncImage(url: URL(string: url)) { phase in
                                    if let image = phase.image {
                                        image
                                            .resizable()
                                            .aspectRatio(contentMode: .fill)
                                            .frame(width: 140, height: 140)
                                            .cornerRadius(12)
                                            .onTapGesture {
                                                let isMines = club.imageUrls.map { _ in false }
                                                appState.galleryData = GalleryData(
                                                    images: club.imageUrls,
                                                    initialIndex: club.imageUrls.firstIndex(of: url) ?? 0,
                                                    isMineList: isMines,
                                                    onDelete: { _ in },
                                                    onReport: { _, _ in }
                                                )
                                            }
                                    } else {
                                        RoundedRectangle(cornerRadius: 12)
                                            .fill(Color.secondary.opacity(0.15))
                                            .frame(width: 140, height: 140)
                                            .overlay(ProgressView().tint(.primary))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    PhotosPickerBox(clubId: club.id ?? "", helper: helper)
                }
                
                Divider()
                
                // Reviews Section
                HStack {
                    Text("Player Reviews")
                        .font(.headline)
                    Spacer()
                    Button(action: {
                        isAddingReview = true
                    }) {
                        Label("Add Review", systemImage: "plus.bubble.fill")
                            .font(.subheadline.weight(.semibold))
                    }
                    .tint(colorAccent)
                }
                
                if reviews.isEmpty {
                    Text("No reviews yet. Be the first to share details!")
                        .foregroundColor(.secondary)
                        .font(.subheadline)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.vertical, 20)
                } else {
                    ForEach(reviews, id: \.id) { review in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Circle()
                                    .fill(colorAccent.opacity(0.2))
                                    .frame(width: 32, height: 32)
                                    .overlay(
                                        Text(String((review.username).prefix(2)).uppercased())
                                            .font(.caption2.weight(.bold))
                                            .foregroundColor(colorAccent)
                                    )
                                
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(review.username)
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundColor(.primary)
                                    Text(timeAgo(from: review.createdAt))
                                        .font(.caption2)
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                            }
                            
                            if let textContent = review.textContent, !textContent.isEmpty {
                                Text(textContent)
                                    .font(.subheadline)
                                    .foregroundColor(.primary)
                            }
                            
                            // Review Tags
                            if !review.tags.isEmpty {
                                FlowLayout(spacing: 6) {
                                    ForEach(review.tags, id: \.self) { tag in
                                        Text(tag)
                                            .font(.caption2)
                                            .foregroundColor(.primary.opacity(0.8))
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 4)
                                            .background(Color.primary.opacity(0.1), in: Capsule())
                                    }
                                }
                            }
                        }
                        .padding()
                        .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
                    }
                }
            }
            .padding()
        }
    }
    
    // MARK: - Helpers & Subviews
    
    private func initializeKMPComponents() {
        KoinHelper.shared.safeInitKoin()
        let vm = KoinHelper.shared.getLocationViewModel()
        self.tokenStorage = KoinHelper.shared.getTokenStorage()
        self.helper = IosLocationViewModelHelper(viewModel: vm)
        
        _ = helper?.subscribeUiState(
            onLoading: {
                isUiLoading = true
            },
            onSuccess: { locList in
                isUiLoading = false
                locations = locList
            },
            onError: { err in
                isUiLoading = false
                errorMessage = err
            }
        )
        
        _ = helper?.subscribeReviews(onCollect: { reviewList in
            reviews = reviewList
        })
        
        helper?.fetchNearbyLocations()
    }
    
    private func setupLocationUpdates() {
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        if CLLocationManager.locationServicesEnabled() {
            if locationManager.authorizationStatus == .notDetermined {
                locationManager.requestWhenInUseAuthorization()
            }
            userCoordinate = locationManager.location?.coordinate
        }
    }
    
    private func formattedDistance(lat: Double, lng: Double) -> String {
        guard let userCoord = userCoordinate else { return "N/A" }
        let userLoc = CLLocation(latitude: userCoord.latitude, longitude: userCoord.longitude)
        let clubLoc = CLLocation(latitude: lat, longitude: lng)
        let meters = userLoc.distance(from: clubLoc)
        
        if meters < 1000 {
            return "\(Int(meters)) m"
        } else {
            return String(format: "%.1f km", meters / 1000.0)
        }
    }
    
    private var filteredLocations: [Location] {
        locations.filter { loc in
            if !searchQuery.isEmpty {
                if !loc.name.localizedCaseInsensitiveContains(searchQuery) {
                    return false
                }
            }
            
            if !selectedTags.isEmpty {
                for filter in selectedTags {
                    if filter == "Indoor" && loc.type != LocationType.indoor { return false }
                    if filter == "Outdoor" && loc.type != LocationType.outdoor { return false }
                    if filter == "Free" && !loc.isFree { return false }
                    if filter == "Paid" && loc.isFree { return false }
                    
                    if filter != "Indoor" && filter != "Outdoor" && filter != "Free" && filter != "Paid" {
                        if !loc.tags.contains(where: { $0.localizedCaseInsensitiveCompare(filter) == .orderedSame }) {
                            return false
                        }
                    }
                }
            }
            return true
        }
    }
    
    private func filterSection(title: String, options: [String]) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.caption.weight(.bold))
                .foregroundColor(.secondary)
                .padding(.leading, 4)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(options, id: \.self) { (opt: String) in
                        let isSelected = selectedTags.contains(opt)
                        
                        if isSelected {
                            Button(action: {
                                withAnimation(.spring(duration: 0.25, bounce: 0.175)) {
                                    _ = selectedTags.remove(opt) // Explicitly discard the return value
                                }
                            }) {
                                Text(opt).font(.subheadline)
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(colorAccent)
                            .clipShape(Capsule())
                        } else {
                            Button(action: {
                                withAnimation(.spring(duration: 0.25, bounce: 0.175)) {
                                    _ = selectedTags.insert(opt) // Explicitly discard the return value
                                }
                            }) {
                                Text(opt).font(.subheadline)
                            }
                            .buttonStyle(.bordered)
                            .tint(.secondary)
                            .clipShape(Capsule())
                        }
                    }
                }
            }
        }
    }
    
    private func handleNavigationClick(club: Location) {
        if let choice = tokenStorage?.getMapChoice() {
            openMapsApp(choice: choice, club: club)
        } else {
            navigationTargetClub = club
            showMapSelectionActionSheet = true
        }
    }
    
    private func openMapsApp(choice: String, club: Location) {
        tokenStorage?.saveMapChoice(choice: choice)
        let latStr = String(format: "%f", club.latitude)
        let lngStr = String(format: "%f", club.longitude)
        
        let urlStr = choice == "apple" ?
            "https://maps.apple.com/?q=\(latStr),\(lngStr)" :
            "https://maps.google.com/?q=\(latStr),\(lngStr)"
        
        if let url = URL(string: urlStr) {
            UIApplication.shared.open(url)
        }
    }
    
    private func timeAgo(from millisecondTimestamp: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(millisecondTimestamp) / 1000.0)
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .full
        return formatter.localizedString(for: date, relativeTo: Date())
    }
    
    private func updateTabBarVisibility() {
        let isNearbyLarge = showNearbySheet && nearbySheetState == .large
        let isDetailsLarge = (selectedLocation != nil) && selectedDetailsDetent == .large
        let isAddTableLarge = isAddingTable && selectedAddTableDetent == .large
        
        let shouldHide = isNearbyLarge || isDetailsLarge || isAddTableLarge || isPickingLocation
        if appState.isTabBarHidden != shouldHide {
            withAnimation(.easeInOut(duration: 0.25)) {
                appState.isTabBarHidden = shouldHide
            }
        }
    }
    
    private var combinedToolbar: some View {
        HStack(spacing: 0) {
            Button(action: {
                trackingUserLocation = true
                locationManager.requestWhenInUseAuthorization()
            }) {
                Image(systemName: "location.fill")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(colorAccent)
                    .frame(width: 44, height: 44)
            }
            
            Divider()
                .frame(height: 24)
                .background(Color.secondary.opacity(0.3))
            
            Button(action: {
                withAnimation {
                    isPickingLocation = true
                }
            }) {
                Image(systemName: "plus")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(colorAccent)
                    .frame(width: 44, height: 44)
            }
        }
        .padding(.horizontal, 8)
        .background(.regularMaterial)
        .cornerRadius(10)
        .shadow(color: Color.black.opacity(0.15), radius: 4, x: 0, y: 2)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color.secondary.opacity(0.2), lineWidth: 0.5)
        )
    }
}

// MARK: - Photo Picking Drag-and-Drop Box for Club Expanded Card
struct PhotosPickerBox: View {
    let clubId: String
    let helper: IosLocationViewModelHelper?
    @State private var pickerPresented = false
    @State private var isUploading = false
    
    var body: some View {
        Button(action: { pickerPresented = true }) {
            HStack(spacing: 8) {
                Image(systemName: "camera.fill")
                    .foregroundColor(Color(hex: "#FF6B35"))
                if isUploading {
                    Text("Uploading photos...")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.secondary)
                } else {
                    Text("Add Club Photos")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(hex: "#FF6B35"))
                }
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(Color.black.opacity(0.15), in: RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color(hex: "#FF6B35").opacity(0.3), style: StrokeStyle(lineWidth: 1, dash: [4]))
            )
        }
        .disabled(isUploading)
        .sheet(isPresented: $pickerPresented) {
            PHPickerRepresentable(maxItems: 5) { imageList in
                guard !imageList.isEmpty else { return }
                isUploading = true
                
                // Call Kotlin bridge function directly
                helper?.addLocationImages(
                    locationId: clubId,
                    imagesData: imageList.map { $0 as Data },
                    onSuccess: {
                        isUploading = false
                    }
                )
            }
        }
    }
}

// MARK: - Modals for Submitting Tables & Reviews

struct AddTableModalView: View {
    let lat: Double
    let lng: Double
    let colorAccent: Color
    let colorSurface: Color
    let helper: IosLocationViewModelHelper?
    let onDismiss: () -> Void
    
    @State private var isIndoor = false
    @State private var tableCount = 1
    @State private var isFree = true
    @State private var selectedImages: [Data] = []
    @State private var showPhotosPicker = false
    @State private var isSubmitting = false
    
    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Pinned Location")) {
                    HStack {
                        Image(systemName: "mappin.circle.fill")
                            .foregroundColor(colorAccent)
                        VStack(alignment: .leading) {
                            Text("Latitude: \(String(format: "%.6f", lat))")
                            Text("Longitude: \(String(format: "%.6f", lng))")
                        }
                        .font(.system(size: 14))
                        .foregroundColor(.gray)
                    }
                }
                
                Section(header: Text("Table Details")) {
                    Picker("Arena Type", selection: $isIndoor) {
                        Text("Outdoor").tag(false)
                        Text("Indoor").tag(true)
                    }
                    .pickerStyle(.segmented)
                    
                    Stepper("Number of Tables: \(tableCount)", value: $tableCount, in: 1...20)
                    
                    Picker("Access Pricing", selection: $isFree) {
                        Text("Free to play").tag(true)
                        Text("Paid venue").tag(false)
                    }
                    .pickerStyle(.segmented)
                }
                
                Section(header: Text("Photos (Up to 5)")) {
                    Button(action: { showPhotosPicker = true }) {
                        HStack {
                            Image(systemName: "camera.fill")
                            Text("Select Photos")
                        }
                    }
                    
                    if !selectedImages.isEmpty {
                        ScrollView(.horizontal) {
                            HStack(spacing: 8) {
                                ForEach(0..<selectedImages.count, id: \.self) { idx in
                                    if let uiImage = UIImage(data: selectedImages[idx]) {
                                        Image(uiImage: uiImage)
                                            .resizable()
                                            .aspectRatio(contentMode: .fill)
                                            .frame(width: 80, height: 80)
                                            .cornerRadius(8)
                                    }
                                }
                            }
                        }
                        .frame(height: 80)
                    }
                }
                
                Section {
                    Button(action: submitTable) {
                        HStack {
                            Spacer()
                            if isSubmitting {
                                ProgressView().tint(.white)
                            } else {
                                Text("Submit Table")
                                    .fontWeight(.bold)
                            }
                            Spacer()
                        }
                    }
                    .foregroundColor(.white)
                    .listRowBackground(colorAccent)
                    .disabled(isSubmitting)
                }
            }
            .navigationTitle("Add New Table")
            .navigationBarItems(leading: Button("Cancel") { onDismiss() })
            .sheet(isPresented: $showPhotosPicker) {
                PHPickerRepresentable(maxItems: 5 - selectedImages.count) { imageList in
                    selectedImages.append(contentsOf: imageList)
                }
            }
        }
    }
    
    private func submitTable() {
        isSubmitting = true
        helper?.submitNewTable(
            lat: lat,
            lng: lng,
            isIndoor: isIndoor,
            count: Int32(tableCount),
            isFree: isFree,
            imagesData: selectedImages.map { $0 as Data },
            onSuccess: {
                isSubmitting = false
                onDismiss()
            }
        )
    }
}

struct AddReviewModalView: View {
    let locationId: String
    let clubName: String
    let colorAccent: Color
    let colorSurface: Color
    let helper: IosLocationViewModelHelper?
    let onDismiss: () -> Void
    
    @State private var reviewText = ""
    @State private var selectedTags: Set<String> = []
    @State private var reviewImages: [Data] = []
    @State private var showPhotosPicker = false
    @State private var isSubmitting = false
    
    private let availableTags = [
        "Perfect surface", "Sturdy net", "Worn out / Damaged", "Torn net", "Slippery surface",
        "Spacious", "Wind-protected", "Good lighting", "Cramped space", "Glaring sun", "Poor lighting",
        "Drinking fountain", "Restroom available", "Usually crowded", "Quiet & Chill"
    ]
    
    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Review Text")) {
                    TextEditor(text: $reviewText)
                        .frame(height: 120)
                }
                
                Section(header: Text("Table Characteristics Tags")) {
                    FlowLayout(spacing: 8) {
                        ForEach(availableTags, id: \.self) { tag in
                            let isSelected = selectedTags.contains(tag)
                            Text(tag)
                                .font(.system(size: 13))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .foregroundColor(isSelected ? .white : .black)
                                .background(isSelected ? colorAccent : Color.gray.opacity(0.2), in: Capsule())
                                .onTapGesture {
                                    if isSelected {
                                        selectedTags.remove(tag)
                                    } else {
                                        selectedTags.insert(tag)
                                    }
                                }
                        }
                    }
                    .padding(.vertical, 4)
                }
                
                Section(header: Text("Review Photos")) {
                    Button(action: { showPhotosPicker = true }) {
                        HStack {
                            Image(systemName: "camera.fill")
                            Text("Select Photos")
                        }
                    }
                    
                    if !reviewImages.isEmpty {
                        ScrollView(.horizontal) {
                            HStack(spacing: 8) {
                                ForEach(0..<reviewImages.count, id: \.self) { idx in
                                    if let uiImage = UIImage(data: reviewImages[idx]) {
                                        Image(uiImage: uiImage)
                                            .resizable()
                                            .aspectRatio(contentMode: .fill)
                                            .frame(width: 80, height: 80)
                                            .cornerRadius(8)
                                    }
                                }
                            }
                        }
                        .frame(height: 80)
                    }
                }
                
                Section {
                    Button(action: submitReview) {
                        HStack {
                            Spacer()
                            if isSubmitting {
                                ProgressView().tint(.white)
                            } else {
                                Text("Submit Review")
                                    .fontWeight(.bold)
                            }
                            Spacer()
                        }
                    }
                    .foregroundColor(.white)
                    .listRowBackground(colorAccent)
                    .disabled(isSubmitting || reviewText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .navigationTitle("Review \(clubName)")
            .navigationBarItems(leading: Button("Cancel") { onDismiss() })
            .sheet(isPresented: $showPhotosPicker) {
                PHPickerRepresentable(maxItems: 5 - reviewImages.count) { imageList in
                    reviewImages.append(contentsOf: imageList)
                }
            }
        }
    }
    
    private func submitReview() {
        isSubmitting = true
        helper?.submitReview(
            locationId: locationId,
            tags: Array(selectedTags),
            text: reviewText,
            imagesData: reviewImages.map { $0 as Data },
            onSuccess: {
                isSubmitting = false
                onDismiss()
            }
        )
    }
}

// MARK: - FlowLayout Helper for SwiftUI tags grid
struct FlowLayout: Layout {
    var spacing: CGFloat
    
    init(spacing: CGFloat = 8) {
        self.spacing = spacing
    }
    
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let sizes = subviews.map { $0.sizeThatFits(.unspecified) }
        let width = proposal.width ?? 0
        var currentX: CGFloat = 0
        var currentY: CGFloat = 0
        var lineHeight: CGFloat = 0
        var maxX: CGFloat = 0
        
        for size in sizes {
            if currentX + size.width > width && currentX > 0 {
                currentX = 0
                currentY += lineHeight + spacing
                lineHeight = 0
            }
            
            lineHeight = max(lineHeight, size.height)
            currentX += size.width + spacing
            maxX = max(maxX, currentX)
        }
        
        return CGSize(width: maxX, height: currentY + lineHeight)
    }
    
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let sizes = subviews.map { $0.sizeThatFits(.unspecified) }
        var currentX: CGFloat = bounds.minX
        var currentY: CGFloat = bounds.minY
        var lineHeight: CGFloat = 0
        
        for index in subviews.indices {
            let size = sizes[index]
            if currentX + size.width > bounds.maxX && currentX > bounds.minX {
                currentX = bounds.minX
                currentY += lineHeight + spacing
                lineHeight = 0
            }
            
            subviews[index].place(at: CGPoint(x: currentX, y: currentY), proposal: .unspecified)
            lineHeight = max(lineHeight, size.height)
            currentX += size.width + spacing
        }
    }
}

// MARK: - Rounded Corner Helper extension for sheets
extension View {
    func cornerRadius(_ radius: CGFloat, corners: UIRectCorner) -> some View {
        clipShape(RoundedCornerShapeHelper(radius: radius, corners: corners))
    }
}

struct RoundedCornerShapeHelper: Shape {
    var radius: CGFloat = .infinity
    var corners: UIRectCorner = .allCorners
    
    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(roundedRect: rect, byRoundingCorners: corners, cornerRadii: CGSize(width: radius, height: radius))
        return Path(path.cgPath)
    }
}