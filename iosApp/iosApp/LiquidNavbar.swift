import SwiftUI
import UIKit

struct LiquidNavbar: View {
    @Binding var currentTab: String
    @Namespace private var navbarTransitionNamespace // 👈 FIX: Namespace handles fluid capsule sliding transitions

    let tabs = [
        ("map", "map.fill", "Map"),
        ("match", "sportscourt.fill", "Match"),
        ("messages", "bubble.left.and.bubble.right.fill", "Messages"),
        ("profile", "person.crop.circle.fill", "Profile")
    ]

    var body: some View {
        AdaptiveGlassBaseContainer {
            HStack(spacing: 0) {
                ForEach(tabs, id: \.0) { id, icon, label in
                    Button(action: {
                        // 🌟 Standard spring curves match native iOS 26 response thresholds
                        withAnimation(.spring(response: 0.36, dampingFraction: 0.74)) {
                            currentTab = id
                        }
                    }) {
                        VStack(spacing: 4) {
                            Image(systemName: icon)
                                .font(.system(size: 20, weight: currentTab == id ? .bold : .medium))
                            Text(label)
                                .font(.system(size: 10, weight: currentTab == id ? .bold : .semibold))
                        }
                        .foregroundColor(currentTab == id ? .orange : .secondary.opacity(0.8))
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .padding(.vertical, 6)
                        // 👈 FIX: High-vibrancy single layer flat capsule overlay moving fluidly via matched geometry
                        .background(
                            ZStack {
                                if currentTab == id {
                                    Capsule()
                                        .fill(Color.white.opacity(0.14))
                                        .overlay(Capsule().stroke(Color.white.opacity(0.22), lineWidth: 0.5))
                                        .matchedGeometryEffect(id: "active_liquid_pill", in: navbarTransitionNamespace)
                                        .padding(.horizontal, 4)
                                }
                            }
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 8)
            .frame(height: 74)
        }
        .padding(.horizontal, 16)
    }
}

// MARK: - Adopting Liquid Glass Layer Components
struct AdaptiveGlassBaseContainer<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        if #available(iOS 26.0, *) {
            // 👈 FIX: Glass effect applied straight to the structural bounding capsule container
            GlassEffectContainer {
                content
                    .background(Capsule().glassEffect(.regular))
            }
            .shadow(color: Color.black.opacity(0.12), radius: 12, x: 0, y: 8)
        } else {
            content
                .background(.ultraThinMaterial)
                .background(
                    LinearGradient(
                        colors: [.white.opacity(0.18), .clear, .black.opacity(0.06)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .clipShape(Capsule())
                .overlay(
                    Capsule()
                        .stroke(
                            LinearGradient(
                                colors: [.white.opacity(0.4), .white.opacity(0.1), .clear, .white.opacity(0.2)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1.0
                        )
                )
                .shadow(color: Color.black.opacity(0.12), radius: 12, x: 0, y: 8)
        }
    }
}