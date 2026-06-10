import SwiftUI
import MapKit
import shared
import PhotosUI

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
    @State private var isDetailsExpanded = false
    @State private var currentSheetState: SheetPosition = .collapsed
    
    // Map control
    @State private var centerCoordinate = CLLocationCoordinate2D(latitude: 47.4979, longitude: 19.0402)
    @State private var mapRegion = MKCoordinateRegion()
    @State private var trackingUserLocation = true
    @Namespace private var mapGlassNS
    @State private var userCoordinate: CLLocationCoordinate2D? = nil
    
    // Filters and Search
    @State private var searchQuery = ""
    @State private var selectedTags: Set<String> = []
    @State private var isFilterPanelExpanded = false
    @State private var searchFocused = false
    
    // Creation Modals
    @State private var isPickingLocation = false
    @State private var isAddingTable = false
    @State private var isAddingReview = false
    
    // Drag gestures & positions
    @State private var sheetOffset: CGFloat = 0
    private let locationManager = CLLocationManager()
    
    enum SheetPosition {
        case collapsed, half, expanded
        
        func height(screenHeight: CGFloat) -> CGFloat {
            switch self {
            case .collapsed: return 140
            case .half: return screenHeight * 0.4
            case .expanded: return screenHeight - 100
            }
        }
    }
    
    // Color Palette Tokens
    private var colorBackground: Color { Color(hex: "#0F172A") }
    private var colorSurface: Color { Color(hex: "#162032") }
    private var colorAccent: Color { Color(hex: "#FF6B35") }
    
    var body: some View {
        GeometryReader { geo in
            let screenHeight = geo.size.height
            let screenWidth = geo.size.width
            
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
                            isDetailsExpanded = false
                            currentSheetState = .collapsed
                        }
                    }
                )
                .ignoresSafeArea()
                
                // MARK: 2. Crosshair for Picking Table Location
                if isPickingLocation {
                    Image(systemName: "plus")
                        .font(.system(size: 36, weight: .thin))
                        .foregroundColor(colorAccent)
                        .background(
                            Circle()
                                .fill(Color.black.opacity(0.2))
                                .frame(width: 44, height: 44)
                        )
                        .position(x: screenWidth / 2, y: screenHeight / 2)
                        .transition(.scale)
                    
                    // Instruction overlay at bottom
                    VStack {
                        Spacer()
                        VStack(spacing: 12) {
                            Text("Drag the map to place the crosshair exactly over the table.")
                                .font(.system(size: 15, weight: .medium))
                                .foregroundColor(.white)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal)
                            
                            HStack(spacing: 20) {
                                Button("Cancel") {
                                    withAnimation { isPickingLocation = false }
                                }
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.gray)
                                
                                Button("Confirm") {
                                    withAnimation {
                                        isPickingLocation = false
                                        isAddingTable = true
                                        appState.isTabBarHidden = true
                                    }
                                }
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 20)
                                .padding(.vertical, 8)
                                .background(colorAccent, in: Capsule())
                            }
                        }
                        .padding()
                        .background(colorSurface.opacity(0.95), in: RoundedRectangle(cornerRadius: 16))
                        .padding()
                        .shadow(radius: 10)
                        .padding(.bottom, 20)
                    }
                    .ignoresSafeArea(.all)
                }
                
                // MARK: 3. Search and Filters Floating Capsule
                if !isPickingLocation && selectedLocation == nil {
                    VStack(spacing: 12) {
                        // Search bar
                        HStack(spacing: 8) {
                            Image(systemName: "magnifyingglass")
                                .foregroundColor(.secondary)
                            
                            TextField("Search venues...", text: $searchQuery)
                                .foregroundColor(.primary)
                                .accentColor(colorAccent)
                                .font(.system(size: 15))
                            
                            if !searchQuery.isEmpty {
                                Button(action: { searchQuery = "" }) {
                                    Image(systemName: "xmark.circle.fill")
                                        .foregroundColor(.secondary)
                                }
                            }
                            
                            Button(action: {
                                // Dictation action placeholder
                            }) {
                                Image(systemName: "mic.fill")
                                    .foregroundColor(.secondary)
                            }
                            
                            Button(action: {
                                withAnimation(.spring()) {
                                    isFilterPanelExpanded.toggle()
                                }
                            }) {
                                Image(systemName: "slider.horizontal.3")
                                    .foregroundColor(isFilterPanelExpanded || !selectedTags.isEmpty ? colorAccent : .secondary)
                                    .padding(8)
                                    .background(isFilterPanelExpanded || !selectedTags.isEmpty ? colorAccent.opacity(0.2) : Color.clear, in: Circle())
                            }
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(.regularMaterial, in: Capsule())
                        .matchedGeometryEffect(id: "mapSearchBar", in: mapGlassNS)
                        .overlay(Capsule().stroke(Color.primary.opacity(0.1), lineWidth: 1))
                        .shadow(radius: 6)
                        
                        // Filters drawer
                        if isFilterPanelExpanded {
                            VStack(alignment: .leading, spacing: 12) {
                                Divider().background(Color.primary.opacity(0.1))
                                
                                ScrollView {
                                    VStack(alignment: .leading, spacing: 14) {
                                        filterSection(title: "Type & Access Strategy", options: ["Indoor", "Outdoor", "Free", "Paid"])
                                        filterSection(title: "Table Properties", options: ["Perfect surface", "Sturdy net", "Worn out / Damaged", "Torn net", "Slippery surface"])
                                        filterSection(title: "Playing Arena Environment", options: ["Spacious", "Wind-protected", "Good lighting", "Cramped space", "Glaring sun", "Poor lighting"])
                                        filterSection(title: "Amenities & Settings Vibe", options: ["Drinking fountain", "Restroom available", "Usually crowded", "Quiet & Chill"])
                                    }
                                    .padding(.bottom, 8)
                                }
                                .frame(maxHeight: 220)
                            }
                            .padding([.horizontal, .bottom])
                            .padding(.top, 10)
                            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 24))
                            .shadow(radius: 6)
                        }
                    }
                    .padding(.horizontal)
                    .position(x: screenWidth / 2, y: isFilterPanelExpanded ? 200 : 70)
                    .zIndex(10)
                }
                
                // MARK: 4. Floating Action Buttons (FABs)
                if selectedLocation == nil && !isPickingLocation {
                    VStack(spacing: 16) {
                        Button(action: {
                            trackingUserLocation = true
                            locationManager.requestWhenInUseAuthorization()
                        }) {
                            Image(systemName: "location.fill")
                                .font(.system(size: 18))
                                .foregroundColor(colorAccent)
                                .frame(width: 48, height: 48)
                                .background(.thickMaterial, in: RoundedRectangle(cornerRadius: 16))
                        }
                        
                        Button(action: {
                            withAnimation {
                                isPickingLocation = true
                                currentSheetState = .collapsed
                            }
                        }) {
                            Image(systemName: "plus")
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(colorAccent)
                                .frame(width: 48, height: 48)
                                .background(.thickMaterial, in: RoundedRectangle(cornerRadius: 16))
                        }
                    }
                    .padding(.trailing, 16)
                    .padding(.bottom, currentSheetState.height(screenHeight: screenHeight) + 16)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .zIndex(5)
                }
                
                // MARK: 5. Draggable Nearby Clubs List Sheet
                if selectedLocation == nil && !isPickingLocation {
                    nearbySheetView(screenHeight: screenHeight)
                        .offset(y: sheetOffset)
                        .gesture(
                            DragGesture()
                                .onChanged { value in
                                    sheetOffset = value.translation.height
                                }
                                .onEnded { value in
                                    let threshold = screenHeight * 0.15
                                    withAnimation(Animation.spring(response: 0.35, dampingFraction: 0.8)) {
                                        if value.translation.height < -threshold {
                                            // Swipe up
                                            if currentSheetState == .collapsed {
                                                currentSheetState = .half
                                            } else if currentSheetState == .half {
                                                currentSheetState = .expanded
                                            }
                                        } else if value.translation.height > threshold {
                                            // Swipe down
                                            if currentSheetState == .expanded {
                                                currentSheetState = .half
                                            } else if currentSheetState == .half {
                                                currentSheetState = .collapsed
                                            }
                                        }
                                        sheetOffset = 0
                                    }
                                }
                        )
                        .zIndex(6)
                }
                
                // MARK: 6. Selected Club Details Overlay (Draggable)
                if let club = selectedLocation {
                    detailsCardView(club: club, screenHeight: screenHeight)
                        .zIndex(7)
                        .transition(.move(edge: .bottom))
                }
            }
            .background(colorBackground)
        }
        .sheet(isPresented: $isAddingTable) {
            AddTableModalView(
                lat: centerCoordinate.latitude,
                lng: centerCoordinate.longitude,
                colorAccent: colorAccent,
                colorSurface: colorSurface,
                helper: helper,
                onDismiss: {
                    isAddingTable = false
                    appState.isTabBarHidden = false
                }
            )
        }
        .sheet(isPresented: $isAddingReview) {
            if let club = selectedLocation {
                AddReviewModalView(
                    locationId: club.id ?? "",
                    clubName: club.name,
                    colorAccent: colorAccent,
                    colorSurface: colorSurface,
                    helper: helper,
                    onDismiss: {
                        isAddingReview = false
                        // Refresh reviews
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
        }
    }
    
    // MARK: - Helpers & Subviews
    
    private func initializeKMPComponents() {
        KoinHelper.shared.safeInitKoin()
        let vm = KoinHelper.shared.getLocationViewModel()
        self.tokenStorage = KoinHelper.shared.getTokenStorage()
        self.helper = IosLocationViewModelHelper(viewModel: vm)
        
        // Subscribe to UI state flow
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
        
        // Subscribe to reviews flow
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
    
    // Computes distance to user in meters or custom string representation
    private func formattedDistance(lat: Double, lng: Double) -> String {
        guard let userCoord = userCoordinate else { return "Distance: N/A" }
        let userLoc = CLLocation(latitude: userCoord.latitude, longitude: userCoord.longitude)
        let clubLoc = CLLocation(latitude: lat, longitude: lng)
        let meters = userLoc.distance(from: clubLoc)
        
        if meters < 1000 {
            return "\(Int(meters)) m"
        } else {
            return String(format: "%.1f km", meters / 1000.0)
        }
    }
    
    // Filtering Core logic
    private var filteredLocations: [Location] {
        locations.filter { loc in
            // Search query filter
            if !searchQuery.isEmpty {
                if !loc.name.localizedCaseInsensitiveContains(searchQuery) {
                    return false
                }
            }
            
            // Category/tag filter
            if !selectedTags.isEmpty {
                // Map indoor/outdoor options to loc.type
                for filter in selectedTags {
                    if filter == "Indoor" && loc.type != LocationType.indoor { return false }
                    if filter == "Outdoor" && loc.type != LocationType.outdoor { return false }
                    if filter == "Free" && !loc.isFree { return false }
                    if filter == "Paid" && loc.isFree { return false }
                    
                    // Arbitrary custom tags matching
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
    
    // Filter tags builder UI row
    private func filterSection(title: String, options: [String]) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(.secondary)
                .padding(.leading, 4)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(options, id: \.self) { opt in
                        let isSelected = selectedTags.contains(opt)
                        if isSelected {
                            Button(action: {
                                withAnimation(Animation.spring(duration: 0.25, bounce: 0.175)) {
                                    selectedTags.remove(opt)
                                }
                            }) {
                                Text(opt)
                                    .font(.system(size: 14))
                                    .foregroundColor(.white)
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(colorAccent)
                            .clipShape(Capsule())
                        } else {
                            Button(action: {
                                withAnimation(Animation.spring(duration: 0.25, bounce: 0.175)) {
                                    selectedTags.insert(opt)
                                }
                            }) {
                                Text(opt)
                                    .font(.system(size: 14))
                                    .foregroundColor(.primary)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background(.regularMaterial, in: Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
    }
    
    // Draggable bottom sheet representing nearby list
    private func nearbySheetView(screenHeight: CGFloat) -> some View {
        VStack(spacing: 0) {
            // Header handle
            Capsule()
                .fill(Color.secondary)
                .frame(width: 40, height: 4)
                .padding(.vertical, 12)
            
            Text("Nearby Clubs")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(.primary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal)
                .padding(.bottom, 8)
            
            ScrollView {
                VStack(spacing: 12) {
                    if isUiLoading && locations.isEmpty {
                        ProgressView().padding()
                    } else if filteredLocations.isEmpty {
                        Text("No table tennis clubs found.")
                            .foregroundColor(.secondary)
                            .font(.system(size: 14))
                            .padding()
                    } else {
                        ForEach(filteredLocations, id: \.id) { loc in
                            HStack(spacing: 16) {
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(Color.black.opacity(0.3))
                                    .frame(width: 50, height: 50)
                                    .overlay(
                                        Text("🏓").font(.title)
                                    )
                                
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(loc.name)
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(.primary)
                                    
                                    Text("\(formattedDistance(lat: loc.latitude, lng: loc.longitude)) • \(loc.tableCount) Tables")
                                        .font(.system(size: 14))
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                            }
                            .padding(.horizontal)
                            .padding(.vertical, 8)
                            .background(Color.black.opacity(0.15))
                            .cornerRadius(12)
                            .onTapGesture {
                                withAnimation(.spring()) {
                                    selectedLocation = loc
                                    currentSheetState = .collapsed
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal)
                .padding(.bottom, 20)
            }
        }
        .frame(height: currentSheetState.height(screenHeight: screenHeight))
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 24))
        .padding(.horizontal, 16)
        .padding(.bottom, 20)
        .shadow(color: Color.black.opacity(0.2), radius: 10, x: 0, y: 5)
    }
    
    // MARK: - Selected Club Card / Full Details
    @State private var detailsDragOffset: CGFloat = 0
    @State private var showMapSelectionActionSheet = false
    
    private func detailsCardView(club: Location, screenHeight: CGFloat) -> some View {
        VStack(spacing: 0) {
            // Drag handle at top if details is expanded
            if isDetailsExpanded {
                Capsule()
                    .fill(Color.secondary)
                    .frame(width: 40, height: 4)
                    .padding(.vertical, 12)
            }
            
            // Detail contents
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Header Row
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(club.name)
                                .font(.system(size: isDetailsExpanded ? 22 : 18, weight: .bold))
                                .foregroundColor(.primary)
                            
                            Text("\(formattedDistance(lat: club.latitude, lng: club.longitude)) • \(club.tableCount) Tables")
                                .font(.system(size: 14))
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        Button(action: {
                            withAnimation(.spring()) {
                                selectedLocation = nil
                                isDetailsExpanded = false
                            }
                        }) {
                            Image(systemName: "xmark.circle.fill")
                                .font(.title3)
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(.horizontal)
                    .padding(.top, isDetailsExpanded ? 0 : 16)
                    
                    // Compact buttons
                    if !isDetailsExpanded {
                        HStack(spacing: 12) {
                            Button("Details") {
                                withAnimation(.spring()) {
                                    isDetailsExpanded = true
                                    appState.isTabBarHidden = true
                                    if let id = club.id {
                                        helper?.loadReviewsForClub(locationId: id)
                                    }
                                }
                            }
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.primary)
                            .frame(maxWidth: .infinity)
                            .frame(height: 38)
                            .background(Color.black.opacity(0.15), in: RoundedRectangle(cornerRadius: 10))
                            
                            Button("Navigate") {
                                handleNavigationClick(club: club)
                            }
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 38)
                            .background(colorAccent, in: RoundedRectangle(cornerRadius: 10))
                        }
                        .padding(.horizontal)
                        .padding(.bottom, 20)
                    } else {
                        // Expanded view sections
                        
                        // Image gallery horizontal carousel
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
                                                        // Invoke the premium SwiftUI gallery representable cover in ContentView
                                                        let isMines = club.imageUrls.map { _ in false } // fallback
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
                                                    .fill(Color.black.opacity(0.15))
                                                    .frame(width: 140, height: 140)
                                                    .overlay(ProgressView().tint(.primary))
                                            }
                                        }
                                    }
                                }
                                .padding(.horizontal)
                            }
                        } else {
                            // Empty gallery picker trigger box
                            PhotosPickerBox(clubId: club.id ?? "", helper: helper)
                                .padding(.horizontal)
                        }
                        
                        // Amenities & Info
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Label(club.type == LocationType.indoor ? "Indoor Arena" : "Outdoor Space", systemImage: club.type == LocationType.indoor ? "house.fill" : "leaf.fill")
                                Spacer()
                                Text(club.isFree ? "Free Access" : "Paid Arena")
                                    .fontWeight(.bold)
                                    .foregroundColor(colorAccent)
                            }
                            .font(.system(size: 15))
                            .foregroundColor(.primary)
                            
                            if let created = club.createdBy {
                                Text("Added by: \(created)")
                                    .font(.system(size: 13))
                                    .foregroundColor(.secondary)
                            }
                        }
                        .padding()
                        .background(Color.black.opacity(0.15), in: RoundedRectangle(cornerRadius: 12))
                        .padding(.horizontal)
                        
                        // Navigation action button
                        Button(action: {
                            handleNavigationClick(club: club)
                        }) {
                            HStack {
                                Image(systemName: "arrow.triangle.turn.up.right.diamond.fill")
                                Text("Navigate to Table")
                            }
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(colorAccent, in: RoundedRectangle(cornerRadius: 12))
                        }
                        .padding(.horizontal)
                        
                        // Reviews Section Header
                        HStack {
                            Text("Player Reviews")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(.primary)
                            Spacer()
                            Button(action: {
                                isAddingReview = true
                            }) {
                                HStack(spacing: 4) {
                                    Image(systemName: "plus.bubble.fill")
                                    Text("Add Review")
                                }
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(colorAccent)
                            }
                        }
                        .padding(.horizontal)
                        .padding(.top, 8)
                        
                        // Reviews list
                        VStack(spacing: 12) {
                            if reviews.isEmpty {
                                Text("No reviews yet. Be the first to share details!")
                                    .foregroundColor(.secondary)
                                    .font(.system(size: 14))
                                    .padding()
                                    .frame(maxWidth: .infinity, alignment: .center)
                            } else {
                                ForEach(reviews, id: \.id) { review in
                                    VStack(alignment: .leading, spacing: 8) {
                                        HStack {
                                            Circle()
                                                .fill(colorAccent.opacity(0.2))
                                                .frame(width: 32, height: 32)
                                                .overlay(
                                                    Text(String((review.username).prefix(2)).uppercased())
                                                        .font(.system(size: 12, weight: .bold))
                                                        .foregroundColor(colorAccent)
                                                )
                                            
                                            VStack(alignment: .leading, spacing: 2) {
                                                Text(review.username)
                                                    .font(.system(size: 14, weight: .bold))
                                                    .foregroundColor(.primary)
                                                Text(timeAgo(from: review.createdAt))
                                                    .font(.system(size: 11))
                                                    .foregroundColor(.secondary)
                                            }
                                            Spacer()
                                        }
                                        
                                        if let textContent = review.textContent, !textContent.isEmpty {
                                            Text(textContent)
                                                .font(.system(size: 14))
                                                .foregroundColor(.primary)
                                        }
                                        
                                        // Review Tags
                                        if !review.tags.isEmpty {
                                            FlowLayout(spacing: 6) {
                                                ForEach(review.tags, id: \.self) { tag in
                                                    Text(tag)
                                                        .font(.system(size: 11))
                                                        .foregroundColor(.primary.opacity(0.8))
                                                        .padding(.horizontal, 8)
                                                        .padding(.vertical, 4)
                                                        .background(Color.primary.opacity(0.1), in: Capsule())
                                                }
                                            }
                                        }
                                    }
                                    .padding()
                                    .background(Color.black.opacity(0.15), in: RoundedRectangle(cornerRadius: 12))
                                    .padding(.horizontal)
                                }
                            }
                        }
                        .padding(.bottom, 40)
                    }
                }
            }
        }
        .frame(height: isDetailsExpanded ? screenHeight * 0.9 : 150)
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 24))
        .padding(.horizontal, 16)
        .padding(.bottom, 20)
        .shadow(color: Color.black.opacity(0.2), radius: 10, x: 0, y: 5)
        .offset(y: detailsDragOffset)
        .gesture(
            isDetailsExpanded ? DragGesture()
                .onChanged { value in
                    if value.translation.height > 0 {
                        detailsDragOffset = value.translation.height
                    }
                }
                .onEnded { value in
                    if value.translation.height > screenHeight / 3 || value.velocity.height > 300 {
                        withAnimation {
                            isDetailsExpanded = false
                            appState.isTabBarHidden = false
                        }
                    }
                    withAnimation {
                        detailsDragOffset = 0
                    }
                } : nil
        )
        .actionSheet(isPresented: $showMapSelectionActionSheet) {
            ActionSheet(
                title: Text("Navigate via"),
                message: Text("Select your preferred navigation mapping choice."),
                buttons: [
                    .default(Text("Apple Maps")) {
                        openMapsApp(choice: "apple", club: club)
                    },
                    .default(Text("Google Maps")) {
                        openMapsApp(choice: "google", club: club)
                    },
                    .cancel()
                ]
            )
        }
    }
    
    private func handleNavigationClick(club: Location) {
        if let choice = tokenStorage?.getMapChoice() {
            openMapsApp(choice: choice, club: club)
        } else {
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
        let width = bounds.width
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
