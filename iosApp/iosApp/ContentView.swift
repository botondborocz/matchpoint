import UIKit
import SwiftUI
import ComposeApp
import shared

// MARK: - Native Gallery Bridge Models
// ... (previous contents unmodified)


// MARK: - Native Gallery Bridge Models
struct GalleryData: Identifiable {
    let id = UUID()
    let images: [String]
    let initialIndex: Int
    let isMineList: [Bool]
    let onDelete: (String) -> Void
    let onReport: (String, String) -> Void
}

class IOSGalleryLauncher: NativeGalleryLauncher {
    var appState: AppState

    init(appState: AppState) {
        self.appState = appState
    }

    func openGallery(images: [String], initialIndex: Int32, isMineList: [ComposeAppBoolean], onDelete: @escaping (String) -> Void, onReport: @escaping (String, String) -> Void) {
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

// MARK: - Reactive Application State
class AppState: ObservableObject {
    @Published var galleryData: GalleryData? = nil
    @Published var currentTab: String = "map"
    @Published var tabTintColor: Color = .orange // Default initial fallback
    @Published var isTabBarHidden: Bool = false // 👈 NEW: Controls tab visibility state

    func updateThemeColor(from hexString: String) {
        withAnimation(.easeInOut(duration: 0.2)) {
            self.tabTintColor = Color(hex: hexString)
        }
    }
}

// MARK: - Core Tab Link to Kotlin Multiplatform
struct ComposeTabViewControllerRepresentable: UIViewControllerRepresentable {
    let tabName: String
    let launcher: NativeGalleryLauncher
    @ObservedObject var appState: AppState

    func makeUIViewController(context: Context) -> UIViewController {
        return MainViewControllerKt.TabViewController(
            tabName: tabName,
            galleryLauncher: launcher,
            onTabChangedByCompose: { newTab in
                DispatchQueue.main.async {
                    self.appState.currentTab = newTab
                }
            },
            onThemeChangedByCompose: { hexStr in
                DispatchQueue.main.async {
                    self.appState.updateThemeColor(from: hexStr)
                }
            },
            // Change this:
            onSubScreenVisibilityChanged: { isSubScreen -> Void in
                DispatchQueue.main.async {
                    withAnimation(.easeInOut(duration: 0.25)) {
                        // 👇 FIX: Use .boolValue to extract the primitive from KotlinBoolean
                        self.appState.isTabBarHidden = isSubScreen.boolValue
                    }
                }
            }
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    @StateObject var appState = AppState()

    init() {
        KoinHelper.shared.safeInitKoin()
    }

    var body: some View {
        GeometryReader { geometry in
            TabView(selection: $appState.currentTab) {
                NativeMapScreenView(appState: appState)
                    .ignoresSafeArea()
                    .toolbar(appState.isTabBarHidden ? .hidden : .visible, for: .tabBar)
                    .tabItem { Label("Map", systemImage: "map.fill") }
                    .tag("map")

                ComposeTabViewControllerRepresentable(tabName: "match", launcher: IOSGalleryLauncher(appState: appState), appState: appState)
                    .ignoresSafeArea()
                    .toolbar(appState.isTabBarHidden ? .hidden : .visible, for: .tabBar)
                    .tabItem { Label("Match", systemImage: "bolt.fill") }
                    .tag("match")

                ComposeTabViewControllerRepresentable(tabName: "messages", launcher: IOSGalleryLauncher(appState: appState), appState: appState)
                    .ignoresSafeArea()
                    .toolbar(appState.isTabBarHidden ? .hidden : .visible, for: .tabBar)
                    .tabItem { Label("Messages", systemImage: "bubble.left.and.bubble.right.fill") }
                    .tag("messages")

                ComposeTabViewControllerRepresentable(tabName: "profile", launcher: IOSGalleryLauncher(appState: appState), appState: appState)
                    .ignoresSafeArea()
                    .toolbar(appState.isTabBarHidden ? .hidden : .visible, for: .tabBar)
                    .tabItem { Label("Profile", systemImage: "person.crop.circle.fill") }
                    .tag("profile")
            }
            .tint(appState.tabTintColor)
            .ignoresSafeArea(.keyboard)
        }
        .fullScreenCover(item: $appState.galleryData) { data in
            NativeSwiftGalleryView(data: data)
                .background(TransparentBackground())
        }
    }
}


// MARK: - UIKIT Bridge Components
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
            Color.black
                .opacity(bgOpacity)
                .ignoresSafeArea()

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

            HStack {
                Button(action: { dismiss() }) {
                    Image(systemName: "xmark")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 40, height: 40)
                        .background(.ultraThinMaterial, in: Circle())
                        .overlay(Circle().stroke(.white.opacity(0.25), lineWidth: 0.5))
                }
                .buttonStyle(.plain)

                Spacer()

                Text("\(currentIndex + 1) of \(data.images.count)")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(.ultraThinMaterial, in: Capsule())
                    .overlay(Capsule().stroke(.white.opacity(0.25), lineWidth: 0.5))

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
                    Image(systemName: "ellipsis")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 40, height: 40)
                        .background(.ultraThinMaterial, in: Circle())
                        .overlay(Circle().stroke(.white.opacity(0.25), lineWidth: 0.5))
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 16)
            .padding(.top, 10)
            .opacity(isUiVisible && viewOffset == .zero ? 1.0 : 0.0)
            .animation(.easeInOut(duration: 0.2), value: isUiVisible)
        }
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

// MARK: - Swift Hex Parser Utility
extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3:
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6:
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8:
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 1, 1, 1)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}