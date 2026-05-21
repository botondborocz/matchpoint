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

// MARK: - Native Gallery View (Fixed Layout & Interaction)
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
            // 1. Background expands truly edge-to-edge
            Color.black
                .opacity(bgOpacity)
                .ignoresSafeArea()

            // 2. TabView ignores safe area so the underlying native scroll view is truly full-screen
            TabView(selection: $currentIndex) {
                ForEach(0..<data.images.count, id: \.self) { index in
                    UIKitZoomableImageView(
                        url: data.images[index],
                        isUiVisible: $isUiVisible,
                        isZoomed: $isZoomed
                    )
                    .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .ignoresSafeArea() // 👈 Crucial: forces UIKitZoomableImageView to adopt absolute hardware bounds
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

            // 3. Custom Top Bar — automatically drops perfectly below the status bar notch
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
            .padding(.top, 10) // Clean aesthetic buffer spacing directly beneath the status bar
            .opacity(isUiVisible && viewOffset == .zero ? 1.0 : 0.0)
            .animation(.easeInOut(duration: 0.2), value: isUiVisible)
        }
        // Controls status bar visibility dynamically during zoom/UI toggles
        .statusBarHidden(!isUiVisible)
    }
}

// MARK: - Core 120Hz Native Zoom Engine
fileprivate let ImageMemoryCache = NSCache<NSURL, UIImage>()

struct UIKitZoomableImageView: UIViewRepresentable {
    let url: String
    @Binding var isUiVisible: Bool
    @Binding var isZoomed: Bool

    func makeUIView(context: Context) -> ZoomScrollView {
        let scrollView = ZoomScrollView()
        scrollView.parent = self

        let doubleTap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleDoubleTap(_:)))
        doubleTap.numberOfTapsRequired = 2
        scrollView.imageView.addGestureRecognizer(doubleTap)

        let singleTap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleSingleTap(_:)))
        singleTap.numberOfTapsRequired = 1
        singleTap.require(toFail: doubleTap)
        scrollView.imageView.addGestureRecognizer(singleTap)

        context.coordinator.scrollView = scrollView
        scrollView.loadImage(from: url)

        return scrollView
    }

    func updateUIView(_ uiView: ZoomScrollView, context: Context) {
        uiView.parent = self
        if !isZoomed && uiView.zoomScale != 1.0 {
            uiView.setZoomScale(1.0, animated: false)
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    class Coordinator: NSObject {
        var parent: UIKitZoomableImageView
        weak var scrollView: ZoomScrollView?

        init(_ parent: UIKitZoomableImageView) {
            self.parent = parent
        }

        @objc func handleSingleTap(_ gesture: UITapGestureRecognizer) {
            guard let scrollView = scrollView, scrollView.zoomScale <= 1.0 else { return }
            withAnimation {
                parent.isUiVisible.toggle()
            }
        }

        @objc func handleDoubleTap(_ gesture: UITapGestureRecognizer) {
            guard let scrollView = scrollView else { return }

            if scrollView.zoomScale > 1.0 {
                scrollView.setZoomScale(1.0, animated: true)
                withAnimation { parent.isUiVisible = true }
            } else {
                let point = gesture.location(in: scrollView.imageView)
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

// MARK: - Native UIKit Custom Scroll Container
class ZoomScrollView: UIScrollView, UIScrollViewDelegate {
    let imageView = UIImageView()
    let spinner = UIActivityIndicatorView(style: .medium)
    var parent: UIKitZoomableImageView?
    private var lastBoundsSize: CGSize = .zero

    override init(frame: CGRect) {
        super.init(frame: frame)
        setupEngine()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupEngine()
    }

    private func setupEngine() {
        self.minimumZoomScale = 1.0
        self.maximumZoomScale = 4.0
        self.showsVerticalScrollIndicator = false
        self.showsHorizontalScrollIndicator = false
        self.delegate = self
        self.backgroundColor = .clear
        self.contentInsetAdjustmentBehavior = .never

        imageView.contentMode = .scaleAspectFit
        imageView.clipsToBounds = true
        imageView.isUserInteractionEnabled = true
        self.addSubview(imageView)

        spinner.color = .white
        self.addSubview(spinner)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        spinner.center = CGPoint(x: self.bounds.midX, y: self.bounds.midY)

        if self.bounds.size != lastBoundsSize {
            lastBoundsSize = self.bounds.size
            if let image = imageView.image {
                configureImageSize(for: image)
            }
        }
        centerImageView()
    }

    func loadImage(from urlString: String) {
        guard let url = URL(string: urlString) else { return }
        let cacheKey = url as NSURL

        if let cachedImage = ImageMemoryCache.object(forKey: cacheKey) {
            self.display(image: cachedImage)
        } else {
            spinner.startAnimating()
            imageView.alpha = 0.0

            URLSession.shared.dataTask(with: url) { [weak self] data, _, _ in
                guard let self = self, let data = data, let loadedImage = UIImage(data: data) else {
                    DispatchQueue.main.async { self?.spinner.stopAnimating() }
                    return
                }

                ImageMemoryCache.setObject(loadedImage, forKey: cacheKey)
                DispatchQueue.main.async { self.display(image: loadedImage) }
            }
            .resume()
        }
    }

    private func display(image: UIImage) {
        spinner.stopAnimating()
        imageView.image = image
        configureImageSize(for: image)
        UIView.animate(withDuration: 0.23) {
            self.imageView.alpha = 1.0
        }
    }

    private func configureImageSize(for image: UIImage) {
        let boundsSize = self.bounds.size
        if boundsSize.width == 0 || boundsSize.height == 0 { return }

        let imageSize = image.size
        let xScale = boundsSize.width / imageSize.width
        let yScale = boundsSize.height / imageSize.height
        let minScale = min(xScale, yScale)

        let width = imageSize.width * minScale
        let height = imageSize.height * minScale

        self.setZoomScale(1.0, animated: false)
        imageView.frame = CGRect(x: 0, y: 0, width: width, height: height)

        self.contentSize = imageView.frame.size
        centerImageView()
    }

    private func centerImageView() {
        let boundsSize = self.bounds.size
        var contentsFrame = imageView.frame

        if contentsFrame.size.width < boundsSize.width {
            contentsFrame.origin.x = (boundsSize.width - contentsFrame.size.width) / 2
        } else {
            contentsFrame.origin.x = 0
        }

        if contentsFrame.size.height < boundsSize.height {
            contentsFrame.origin.y = (boundsSize.height - contentsFrame.size.height) / 2
        } else {
            contentsFrame.origin.y = 0
        }

        imageView.frame = contentsFrame
    }

    // MARK: - UIScrollViewDelegate Implementation
    func viewForZooming(in scrollView: UIScrollView) -> UIView? {
        return imageView
    }

    func scrollViewDidZoom(_ scrollView: UIScrollView) {
        centerImageView()

        let zoomed = scrollView.zoomScale > 1.0
        if parent?.isZoomed != zoomed {
            parent?.isZoomed = zoomed
        }
        if scrollView.zoomScale > 1.01, let uiVisible = parent?.isUiVisible, uiVisible {
            withAnimation { parent?.isUiVisible = false }
        }
    }

    func scrollViewDidEndZooming(_ scrollView: UIScrollView, with view: UIView?, atScale scale: CGFloat) {
        if scrollView.zoomScale <= 1.0 {
            withAnimation { parent?.isUiVisible = true }
        }
    }
}