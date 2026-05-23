import SwiftUI
import UIKit

struct LiquidNavbar: View {
    @Binding var currentTab: String
    @Namespace private var fallbackNamespace

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
                        withAnimation(.spring(response: 0.35, dampingFraction: 0.76)) {
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
                        .adaptivePillHighlight(isActive: currentTab == id, namespace: fallbackNamespace)
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
            GlassEffectContainer {
                content
                    .glassEffect(.regular)
                    .clipShape(Capsule())
            }
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

// MARK: - Progressive Selection Modifiers
extension View {
    @ViewBuilder
    func adaptivePillHighlight(isActive: Bool, namespace: Namespace.ID) -> some View {
        if #available(iOS 26.0, *) {
            // 👇 FIXED: Swapped out invalid property value for .regular + opacity interpolation
            self.background(
                Capsule()
                    .glassEffect(.regular)
                    .opacity(isActive ? 1.0 : 0.0)
                    .padding(.horizontal, 4)
                    .padding(.vertical, 6)
            )
        } else {
            self.background(
                ZStack {
                    if isActive {
                        Capsule()
                            .fill(Color.white.opacity(0.12))
                            .overlay(Capsule().stroke(Color.white.opacity(0.25), lineWidth: 0.5))
                            .matchedGeometryEffect(id: "fallback_pill_geometry", in: namespace)
                            .padding(.horizontal, 4)
                            .padding(.vertical, 6)
                    }
                }
            )
        }
    }
}

// MARK: - Core Shapes
struct RoundedCornerShape: Shape {
    var radius: CGFloat = .infinity
    var corners: UIRectCorner = .allCorners

    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(roundedRect: rect, byRoundingCorners: corners, cornerRadii: CGSize(width: radius, height: radius))
        return Path(path.cgPath)
    }
}