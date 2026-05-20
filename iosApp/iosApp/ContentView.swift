import UIKit
import SwiftUI
import ComposeApp

// 1. Implement the Kotlin Interface in Swift
class IOSGalleryLauncher: NativeGalleryLauncher {
    // We use an ObservableObject to trigger SwiftUI presentation
    var appState: AppState

    init(appState: AppState) {
        self.appState = appState
    }

    func openGallery(images: [String], initialIndex: Int32, isMine: Bool, onDelete: @escaping (String) -> Void, onReport: @escaping (String, String) -> Void) {
        // Update the state on the main thread to trigger the SwiftUI sheet
        DispatchQueue.main.async {
            self.appState.galleryData = GalleryData(
                images: images,
                initialIndex: Int(initialIndex),
                isMine: isMine,
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
    let isMine: Bool
    let onDelete: (String) -> Void
    let onReport: (String, String) -> Void
}

// 3. Your Main SwiftUI View
struct ContentView: View {
    @StateObject var appState = AppState()

    var body: some View {
        // Initialize your Compose app, passing in the Swift implementation
        ComposeView(launcher: IOSGalleryLauncher(appState: appState))
            .ignoresSafeArea()
            .fullScreenCover(item: $appState.galleryData) { data in
                // Present the purely native Swift Gallery!
                NativeSwiftGalleryView(data: data)
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

struct NativeSwiftGalleryView: View {
    let data: GalleryData
    @Environment(\.dismiss) var dismiss

    @State private var currentIndex: Int
    @State private var isUiVisible: Bool = true

    init(data: GalleryData) {
        self.data = data
        _currentIndex = State(initialValue: data.initialIndex)
    }

    var body: some View {
        ZStack(alignment: .top) {
            Color.black.ignoresSafeArea()

            // Native Paging
            TabView(selection: $currentIndex) {
                ForEach(0..<data.images.count, id: \.self) { index in
                    ZoomableImageView(
                        url: data.images[index],
                        isUiVisible: $isUiVisible
                    )
                    .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            // Only hide the status bar when the UI is hidden
            .statusBarHidden(!isUiVisible)

            // Your Custom Top Bar
            HStack {
                Button(action: { dismiss() }) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 28))
                        .symbolRenderingMode(.palette)
                        .foregroundStyle(.white, Color(white: 0.2))
                }

                Spacer()

                Text("\(currentIndex + 1) of \(data.images.count)")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 6)
                    .background(Color(white: 0.2))
                    .cornerRadius(16)

                Spacer()

                // Pure Native iOS Menu
                Menu {
                    if data.isMine {
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
                        .font(.system(size: 28))
                        .symbolRenderingMode(.palette)
                        .foregroundStyle(.white, Color(white: 0.2))
                }
            }
            .padding()
            // Gracefully fade the top bar in and out
            .opacity(isUiVisible ? 1.0 : 0.0)
            .animation(.easeInOut(duration: 0.2), value: isUiVisible)
        }
    }
}

// MARK: - Zoomable Image Component
struct ZoomableImageView: View {
    let url: String
    @Binding var isUiVisible: Bool

    // Zoom & Pan State
    @State private var scale: CGFloat = 1.0
    @State private var lastScale: CGFloat = 1.0
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero

    var body: some View {
        GeometryReader { geometry in
            AsyncImage(url: URL(string: url)) { image in
                image
                    .resizable()
                    .scaledToFit()
            } placeholder: {
                ProgressView().tint(.white)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            // Apply pan and zoom transformations
            .offset(offset)
            .scaleEffect(scale)
            // 1. Prioritize double tap over single tap
            .onTapGesture(count: 2) {
                withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                    if scale > 1.0 {
                        // Reset Zoom
                        resetImageState()
                        isUiVisible = true
                    } else {
                        // Zoom in
                        scale = 2.5
                        lastScale = 2.5
                        isUiVisible = false
                    }
                }
            }
            // 2. Single tap toggles the UI
            .onTapGesture(count: 1) {
                withAnimation {
                    isUiVisible.toggle()
                }
            }
            // 3. Pinch to zoom (Magnification)
            .simultaneousGesture(
                MagnificationGesture()
                    .onChanged { value in
                        let newScale = lastScale * value
                        // Prevent shrinking smaller than the original size
                        scale = max(newScale, 1.0)

                        // Hide UI immediately if user starts zooming
                        if scale > 1.0 && isUiVisible {
                            withAnimation { isUiVisible = false }
                        }
                    }
                    .onEnded { value in
                        lastScale = scale
                        if scale <= 1.0 {
                            resetImageState()
                        }
                    }
            )
            // 4. Drag to pan (Only enabled when zoomed in)
            .simultaneousGesture(
                DragGesture()
                    .onChanged { value in
                        if scale > 1.0 {
                            offset = CGSize(
                                width: lastOffset.width + value.translation.width,
                                height: lastOffset.height + value.translation.height
                            )
                        }
                    }
                    .onEnded { value in
                        if scale > 1.0 {
                            lastOffset = offset
                        }
                    }
            )
        }
    }

    private func resetImageState() {
        withAnimation(.spring()) {
            scale = 1.0
            lastScale = 1.0
            offset = .zero
            lastOffset = .zero
        }
    }
}
