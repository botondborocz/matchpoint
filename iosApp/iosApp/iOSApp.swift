import SwiftUI
import GoogleSignIn
import ComposeApp

@main
struct iOSApp: App {
    init() {
        MainViewControllerKt.setNotificationListener { message, type in
            let systemIcon: String
            let tint: Color?
            
            switch type {
            case "wifi_on":
                systemIcon = "wifi"
                tint = Color.green.opacity(0.15)
            case "wifi_off":
                systemIcon = "wifi.slash"
                tint = Color.red.opacity(0.15)
            case "success":
                systemIcon = "checkmark.circle.fill"
                tint = Color.green.opacity(0.15)
            case "error":
                systemIcon = "exclamationmark.triangle.fill"
                tint = Color.red.opacity(0.15)
            default:
                systemIcon = "info.circle.fill"
                tint = nil
            }
            
            LiquidDrops.show(
                LiquidDrop(
                    title: message,
                    icon: UIImage(systemName: systemIcon),
                    position: .top,
                    duration: .recommended,
                    animationStyle: .init(coming: .bouncy, going: .snappy),
                    glassTint: tint
                )
            )
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .liquidDropsHost()
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}