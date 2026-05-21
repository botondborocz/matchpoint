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

struct ComposeView: UIViewControllerRepresentable {
    let launcher: NativeGalleryLauncher

    func makeUIViewController(context: Context) -> UIViewController {
        return MainViewControllerKt.MainViewController(galleryLauncher: launcher)
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

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
                    // 👇 Uses the lightning fast native UIKit engine wrapper
                    UIKitZoomableImageView(
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

            // Custom Top Bar
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
        .statusBarHidden(!isUiVisible)
    }
}

// 👇 A fast, global memory cache to keep swiping instant
fileprivate let ImageMemoryCache = NSCache<NSURL, UIImage>()

struct UIKitZoomableImageView: UIViewRepresentable {
    let url: String
    @Binding var isUiVisible: Bool
    @Binding var isZoomed: Bool

    func makeUIView(context: Context) -> UIScrollView {
        let scrollView = UIScrollView()
        scrollView.minimumZoomScale = 1.0
        scrollView.maximumZoomScale = 4.0
        scrollView.showsVerticalScrollIndicator = false
        scrollView.showsHorizontalScrollIndicator = false
        scrollView.delegate = context.coordinator
        scrollView.backgroundColor = .clear
        scrollView.contentInsetAdjustmentBehavior = .never

        let imageView = UIImageView()
        imageView.contentMode = .scaleAspectFit
        imageView.clipsToBounds = true
        imageView.isUserInteractionEnabled = true
        imageView.translatesAutoresizingMaskIntoConstraints = false

        scrollView.addSubview(imageView)
        context.coordinator.imageView = imageView

        // Setup absolute edge-to-edge layout anchors
        NSLayoutConstraint.activate([
            imageView.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
            imageView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            imageView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
            imageView.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor),
            imageView.heightAnchor.constraint(equalTo: scrollView.frameLayoutGuide.heightAnchor)
        ])

        // Native loading spinner centered on screen
        let spinner = UIActivityIndicatorView(style: .medium)
        spinner.color = .white
        spinner.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(spinner)

        NSLayoutConstraint.activate([
            spinner.centerXAnchor.constraint(equalTo: scrollView.frameLayoutGuide.centerXAnchor),
            spinner.centerYAnchor.constraint(equalTo: scrollView.frameLayoutGuide.centerYAnchor)
        ])

        // Add native Tap Gestures
        let doubleTap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleDoubleTap(_:)))
        doubleTap.numberOfTapsRequired = 2
        imageView.addGestureRecognizer(doubleTap)

        let singleTap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleSingleTap(_:)))
        singleTap.numberOfTapsRequired = 1
        singleTap.require(toFail: doubleTap)
        imageView.addGestureRecognizer(singleTap)

        // --- OPTIMIZED CACHED IMAGE LOADER ---
        if let imageURL = URL(string: url) {
            let cacheKey = imageURL as NSURL

            // 1. Check if the image is already cached in memory
            if let cachedImage = ImageMemoryCache.object(forKey: cacheKey) {
                imageView.image = cachedImage
                imageView.alpha = 1.0
            } else {
                // 2. Not cached: Show spinner and fetch over network
                spinner.startAnimating()
                imageView.alpha = 0.0 // Hide image completely until loaded

                URLSession.shared.dataTask(with: imageURL) { data, _, _ in
                    guard let data = data, let loadedImage = UIImage(data: data) else {
                        DispatchQueue.main.async { spinner.stopAnimating() }
                        return
                    }

                    // Save to global memory cache
                    ImageMemoryCache.setObject(loadedImage, forKey: cacheKey)

                    DispatchQueue.main.async {
                        spinner.stopAnimating()
                        imageView.image = loadedImage

                        // 3. Smooth fade-in transition
                        UIView.animate(withDuration: 0.25, delay: 0, options: .curveEaseInOut) {
                            imageView.alpha = 1.0
                        }
                    }
                }
                .resume()
            }
        }

        return scrollView
    }

    func updateUIView(_ uiView: UIScrollView, context: Context) {
        if !isZoomed && uiView.zoomScale != 1.0 {
            uiView.setZoomScale(1.0, animated: false)
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    class Coordinator: NSObject, UIScrollViewDelegate {
        var parent: UIKitZoomableImageView
        var imageView: UIImageView?

        init(_ parent: UIKitZoomableImageView) {
            self.parent = parent
        }

        func viewForZooming(in scrollView: UIScrollView) -> UIView? {
            return imageView
        }

        func scrollViewDidZoom(_ scrollView: UIScrollView) {
            let zoomed = scrollView.zoomScale > 1.0
            if parent.isZoomed != zoomed {
                parent.isZoomed = zoomed
            }
            if scrollView.zoomScale > 1.01 && parent.isUiVisible {
                withAnimation { parent.isUiVisible = false }
            }
        }

        func scrollViewDidEndZooming(_ scrollView: UIScrollView, with view: UIView?, atScale scale: CGFloat) {
            if scrollView.zoomScale <= 1.0 {
                withAnimation { parent.isUiVisible = true }
            }
        }

        @objc func handleSingleTap(_ gesture: UITapGestureRecognizer) {
            guard let scrollView = imageView?.superview as? UIScrollView, scrollView.zoomScale <= 1.0 else { return }
            withAnimation {
                parent.isUiVisible.toggle()
            }
        }

        @objc func handleDoubleTap(_ gesture: UITapGestureRecognizer) {
            guard let scrollView = imageView?.superview as? UIScrollView else { return }

            if scrollView.zoomScale > 1.0 {
                scrollView.setZoomScale(1.0, animated: true)
                withAnimation { parent.isUiVisible = true }
            } else {
                let point = gesture.location(in: imageView)
                let zoomRect = calculateZoomRect(for: scrollView, at: 2.5, with: point)
                scrollView.zoom(to: zoomRect, animated: true)
                withAnimation { parent.isUiVisible = false }
            }
        }

        private func calculateZoomRect(for scrollView: UIScrollView, at scale: CGFloat, with center: CGPoint) -> CGRect {
            var zoomRect = CGRect.zero
            zoomRect.size.height = scrollView.frame.size.height / scale
            zoomRect.size.width  = scrollView.frame.size.width  / scale
            zoomRect.origin.x    = center.x - (zoomRect.size.width  / 2.0)
            zoomRect.origin.y    = center.y - (zoomRect.size.height / 2.0)
            return zoomRect
        }
    }
}