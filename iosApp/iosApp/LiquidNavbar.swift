import SwiftUI
import UIKit

struct LiquidNavbar: View {
    @Binding var currentTab: String
    @Namespace private var liquidNamespace // 👈 The magic wand for structural animations

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
                        // 🌟 An ultra-responsive fluid spring curve that allows for slight overshooting/stretching
                        withAnimation(.spring(response: 0.38, dampingFraction: 0.68, blendDuration: 0.1)) {
                            currentTab = id
                        }
                    }) {
                        VStack(spacing: 5) {
                            Image(systemName: icon)
                                .font(.system(size: 19, weight: currentTab == id ? .bold : .medium))
                                .scaleEffect(currentTab == id ? 1.15 : 1.0) // Subtle bouncy expansion

                            Text(label)
                                .font(.system(size: 10, weight: currentTab == id ? .bold : .medium))
                        }
                        .foregroundColor(currentTab == id ? .orange : .secondary.opacity(0.75))
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .padding(.vertical, 8)
                        .contentShape(Rectangle()) // Ensures the entire tab cell area remains touch-responsive
                    }
                    .buttonStyle(NoHighlightButtonStyle())
                    // 👈 The tabs declare their shared geometry namespace *without* rendering an active view inside the loop
                    .matchedGeometryEffect(id: id, in: liquidNamespace, isSource: true)
                }
            }
            .padding(.horizontal, 6)
            .frame(height: 72)
            .background(
                // 👈 FIX: The active bubble lives completely outside the loop as a single view instance.
                // It morphs, stretches, and glides smoothly across the source coordinate spaces declared above.
                Capsule()
                    .fill(Color.white.opacity(0.12))
                    .overlay(Capsule().stroke(Color.white.opacity(0.18), lineWidth: 0.5))
                    .padding(.horizontal, 4)
                    .padding(.vertical, 6)
                    .matchedGeometryEffect(id: currentTab, in: liquidNamespace, properties: .frame, isSource: false)
            )
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
                    .background(Capsule().glassEffect(.regular))
            }
            .shadow(color: Color.black.opacity(0.08), radius: 16, x: 0, y: 10)
        } else {
            content
                .background(.ultraThinMaterial)
                .background(
                    LinearGradient(
                        colors: [.white.opacity(0.15), .clear, .black.opacity(0.04)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .clipShape(Capsule())
                .overlay(
                    Capsule()
                        .stroke(
                            LinearGradient(
                                colors: [.white.opacity(0.35), .white.opacity(0.1), .clear, .white.opacity(0.15)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1.0
                        )
                )
                .shadow(color: Color.black.opacity(0.08), radius: 16, x: 0, y: 10)
        }
    }
}

// MARK: - Interaction Style Fixes
struct NoHighlightButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> View {
        // Prevents the default iOS dark grey flash overlay from interrupting our custom bubble animation on press
        configuration.label
            .opacity(configuration.isPressed ? 0.88 : 1.0)
            .scaleEffect(configuration.isPressed ? 0.96 : 1.0)
    }
}
