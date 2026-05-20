import UIKit
import SwiftUI
import ComposeApp

// 1. Implement the Kotlin Interface in Swift
class IOSGalleryLauncher: NativeGalleryLauncher {
    var appState: AppState

    init(appState: AppState) {
        self.appState = appState
    }

    func openGallery(images: [String], initialIndex: Int32, isMineList: [KotlinBoolean], onDelete: @escaping (String) -> Void, onReport: @escaping (String, String) -> Void) {
        DispatchQueue.main.async {
            self.appState.galleryData = GalleryData(
                images: images,
                initialIndex: Int(initialIndex),
                isMineList: isMineList.map { $0.boolValue },
                onDelete: onDelete,
                onReport: onReport
            )
        }
    }
}

// 2. State object to hold the gallery data
class AppState: ObservableObject {
    @Published var galleryData: GalleryData? = nil
}

struct GalleryData: Identifiable {
    let id = UUID()
    let images: [String]
    let initialIndex: Int
    let isMineList: [Bool]
    let onDelete: (String) -> Void
    let onReport: (String, String) -> Void
}

// 3. Your Main SwiftUI View
struct ContentView: View {
    @StateObject var appState = AppState()

    var body: some View {
        ComposeView(launcher: IOSGalleryLauncher(appState: appState))
            .ignoresSafeArea()
            .fullScreenCover(item: $appState.galleryData) { data in
                NativeSwiftGalleryView(data: data)
                    .background(TransparentBackground())
            }
    }
}

// Wrapper for your Compose entry point
struct ComposeView: UIViewControllerRepresentable {
    let launcher: NativeGalleryLauncher

    func makeUIViewController(context: Context) -> UIViewController {
        return MainViewControllerKt.MainViewController(galleryLauncher: launcher)
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// Helper to ensure transparent background on fullScreenCover
struct TransparentBackground: UIViewRepresentable {
    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        DispatchQueue.main.async {
            view.superview?.superview?.backgroundColor = .clear
        }
        return view
    }
    func updateUIView(_ uiView: UIView, context: Context) {}
}

// MARK: - Native Gallery View
struct NativeSwiftGalleryView: View {
    let data: GalleryData
    @Environment(\.dismiss) var dismiss

    @State private var currentIndex: Int
    @State private var isUiVisible: Bool = true

    // Swipe-to-Dismiss State
    @State private var isZoomed: Bool = false
    @State private var bgOpacity: Double = 1.0
    @State private var viewOffset: CGSize = .zero
    @State private var isDraggingVertically = false

    init(data: GalleryData) {
        self.data = data
        _currentIndex = State(initialValue: data.initialIndex)
    }

    var body: some View {
        ZStack(alignment: .top) {
            Color.black.opacity(bgOpacity).ignoresSafeArea()

            // Native Paging
            TabView(selection: $currentIndex) {
                ForEach(0..<data.images.count, id: \.self) { index in
                    ZoomableImageView(
                        url: data.images[index],
                        isUiVisible: $isUiVisible,
                        isZoomed: $isZoomed
                    )
                    .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .ignoresSafeArea()
            .offset(y: viewOffset.height)

            // Swipe-to-dismiss physics
            .simultaneousGesture(
                DragGesture()
                    .onChanged { value in
                        guard !isZoomed else { return }

                        let isHorizontal = abs(value.translation.width) > abs(value.translation.height)
                        if !isDraggingVertically && isHorizontal && abs(value.translation.width) > 5 {
                            return
                        }

                        isDraggingVertically = true
                        viewOffset.height = value.translation.height

                        let progress = min(abs(value.translation.height) / 300, 1.0)
                        bgOpacity = 1.0 - (progress * 0.4)

                        if isUiVisible {
                            withAnimation { isUiVisible = false }
                        }
                    }
                    .onEnded { value in
                        guard !isZoomed && isDraggingVertically else { return }
                        isDraggingVertically = false

                        let velocity = value.predictedEndLocation.y - value.location.y
                        if abs(viewOffset.height) + abs(velocity) > 150 {
                            dismiss()
                        } else {
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                                viewOffset = .zero
                                bgOpacity = 1.0
                            }
                        }
                    }
            )

            // Your Custom Top Bar
            HStack {
                Button(action: { dismiss() }) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 34))
                        .symbolRenderingMode(.palette)
                        .foregroundStyle(.white, Color(white: 0.2))
                }

                Spacer()

                Text("\(currentIndex + 1) of \(data.images.count)")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color(white: 0.2))
                    .cornerRadius(16)

                Spacer()

                // Pure Native iOS Menu
                Menu {
                    if data.isMineList[currentIndex] {
                        Button(role: .destructive) {
                            data.onDelete(data.images[currentIndex])
                            dismiss()
                        } label: {
                            Label("Delete Photo", systemImage: "trash")
                        }
                    } else {
                        Button {
                            data.onReport(data.images[currentIndex], "Spam")
                            dismiss()
                        } label: {
                            Label("Report Photo", systemImage: "flag")
                        }
                    }
                } label: {
                    Image(systemName: "ellipsis.circle.fill")
                        .font(.system(size: 34))
                        .symbolRenderingMode(.palette)
                        .foregroundStyle(.white, Color(white: 0.2))
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .opacity(isUiVisible && viewOffset == .zero ? 1.0 : 0.0)
            .animation(.easeInOut(duration: 0.2), value: isUiVisible)
        }
        // 👈 FIX 4: Applied to root ZStack to properly hide Clock/Network
        .statusBarHidden(!isUiVisible)
    }
}

// MARK: - Zoomable Image Component
struct ZoomableImageView: View {
    let url: String
    @Binding var isUiVisible: Bool
    @Binding var isZoomed: Bool

    @State private var scale: CGFloat = 1.0
    @State private var lastScale: CGFloat = 1.0
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero

    // Tracks the exact rendered pixel size of the image to clamp panning
    @State private var imageSize: CGSize = .zero

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                AsyncImage(url: URL(string: url)) { phase in
                    if let image = phase.image {
                        image
                            .resizable()
                            .scaledToFit()
                            .background(
                                GeometryReader { imgGeo in
                                    Color.clear
                                        .onAppear { imageSize = imgGeo.size }
                                        .onChange(of: imgGeo.size) { newSize in imageSize = newSize }
                                }
                            )
                    } else if phase.error != nil {
                        Image(systemName: "photo")
                            .foregroundColor(.gray)
                    } else {
                        ProgressView().tint(.white)
                    }
                }
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
            .contentShape(Rectangle()) // Ensures the whole screen is draggable

            // 👈 FIX 1: Applied directly to the wrapper ZStack for buttery smooth performance
            .scaleEffect(scale)
            .offset(offset)

            .onTapGesture(count: 2, coordinateSpace: .local) { location in
                withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                    if scale > 1.0 {
                        resetImageState()
                        isUiVisible = true
                    } else {
                        scale = 2.5
                        lastScale = 2.5
                        isZoomed = true

                        let midX = geometry.size.width / 2
                        let midY = geometry.size.height / 2
                        let newX = (midX - location.x) * (scale - 1)
                        let newY = (midY - location.y) * (scale - 1)

                        // 👈 FIX 3: Clamp the offset so it never reveals the black background!
                        offset = clampOffset(CGSize(width: newX, height: newY), scale: scale, container: geometry.size)
                        lastOffset = offset
                        isUiVisible = false
                    }
                }
            }
            // 👈 FIX 2: Single tap toggles UI *only* when fully zoomed out
            .onTapGesture(count: 1) {
                if scale <= 1.0 {
                    withAnimation { isUiVisible.toggle() }
                }
            }
            // Pinch to zoom
            .simultaneousGesture(
                MagnificationGesture()
                    .onChanged { value in
                        let newScale = lastScale * value
                        scale = max(newScale, 1.0)
                        isZoomed = scale > 1.0

                        if scale > 1.0 && isUiVisible {
                            withAnimation { isUiVisible = false }
                        }

                        // Keeps image perfectly clamped while zooming out
                        offset = clampOffset(offset, scale: scale, container: geometry.size)
                    }
                    .onEnded { value in
                        lastScale = scale
                        if scale <= 1.0 {
                            resetImageState()
                        } else {
                            lastOffset = offset
                        }
                    }
            )
            // Drag to pan
            .gesture(
                DragGesture()
                    .onChanged { value in
                        let proposedOffset = CGSize(
                            width: lastOffset.width + value.translation.width,
                            height: lastOffset.height + value.translation.height
                        )
                        // 👈 FIX 3: Blocks dragging past the edges of the image
                        offset = clampOffset(proposedOffset, scale: scale, container: geometry.size)
                    }
                    .onEnded { value in
                        lastOffset = offset
                    },
                including: scale > 1.0 ? .all : .none
            )
        }
    }

    // 👈 FIX 3: Math algorithm to ensure edge-to-edge perfection
    private func clampOffset(_ proposed: CGSize, scale: CGFloat, container: CGSize) -> CGSize {
        let scaledWidth = imageSize.width * scale
        let scaledHeight = imageSize.height * scale

        let maxPanX = max(0, (scaledWidth - container.width) / 2)
        let maxPanY = max(0, (scaledHeight - container.height) / 2)

        let clampedX = min(max(proposed.width, -maxPanX), maxPanX)
        let clampedY = min(max(proposed.height, -maxPanY), maxPanY)

        return CGSize(width: clampedX, height: clampedY)
    }

    private func resetImageState() {
        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
            scale = 1.0
            lastScale = 1.0
            offset = .zero
            lastOffset = .zero
            isZoomed = false
        }
    }
}