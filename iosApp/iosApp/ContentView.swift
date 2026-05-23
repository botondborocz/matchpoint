import UIKit
import SwiftUI
import ComposeApp

// MARK: - Native Gallery Structs & Bridges
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

class AppState: ObservableObject {
    @Published var galleryData: GalleryData? = nil
    @Published var currentTab: String = "map"
}

// MARK: - Core Tab Link to Kotlin Multiplatform
struct ComposeTabViewControllerRepresentable: UIViewControllerRepresentable {
    let tabName: String
    let launcher: NativeGalleryLauncher
    @Binding var currentTab: String

    func makeUIViewController(context: Context) -> UIViewController {
        return MainViewControllerKt.TabViewController(
            tabName: tabName,
            galleryLauncher: launcher,
            onTabChangedByCompose: { newTab in
                DispatchQueue.main.async {
                    self.currentTab = newTab
                }
            }
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// MARK: - Main Native Tab Layout View
struct ContentView: View {
    @StateObject var appState = AppState()

    var body: some View {
        TabView(selection: $appState.currentTab) {

            ComposeTabViewControllerRepresentable(
                tabName: "map",
                launcher: IOSGalleryLauncher(appState: appState),
                currentTab: $appState.currentTab
            )
            .tabItem {
                Label("Map", systemImage: "map.fill")
            }
            .tag("map")

            ComposeTabViewControllerRepresentable(
                tabName: "match",
                launcher: IOSGalleryLauncher(appState: appState),
                currentTab: $appState.currentTab
            )
            .tabItem {
                Label("Match", systemImage: "sportscourt.fill")
            }
            .tag("match")

            ComposeTabViewControllerRepresentable(
                tabName: "messages",
                launcher: IOSGalleryLauncher(appState: appState),
                currentTab: $appState.currentTab
            )
            .tabItem {
                Label("Messages", systemImage: "bubble.left.and.bubble.right.fill")
            }
            .tag("messages")

            ComposeTabViewControllerRepresentable(
                tabName: "profile",
                launcher: IOSGalleryLauncher(appState: appState),
                currentTab: $appState.currentTab
            )
            .tabItem {
                Label("Profile", systemImage: "person.crop.circle.fill")
            }
            .tag("profile")
        }
        .fullScreenCover(item: $appState.galleryData) { data in
            NativeSwiftGalleryView(data: data)
                .background(TransparentBackground())
        }
    }
}