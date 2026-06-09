import SwiftUI

// MARK: - iOS 26 Liquid Glass Core Implementations
// Centralized modifiers and styles for the Liquid Glass design language.

struct GlassEffectType {
    let material: Material
    let isInteractive: Bool
    
    static var regular: GlassEffectType {
        GlassEffectType(material: .regular, isInteractive: false)
    }
    
    static var clear: GlassEffectType {
        GlassEffectType(material: .ultraThin, isInteractive: false)
    }
    
    func interactive() -> GlassEffectType {
        GlassEffectType(material: self.material, isInteractive: true)
    }
}

extension View {
    func glassEffect(_ type: GlassEffectType, in shape: some Shape = Rectangle()) -> some View {
        self.background(type.material, in: shape)
    }
    
    func glassEffectID(_ id: String, in namespace: Namespace.ID) -> some View {
        self.matchedGeometryEffect(id: id, in: namespace)
    }
}

struct GlassEffectContainer<Content: View>: View {
    let spacing: CGFloat
    let content: Content
    
    init(spacing: CGFloat = 8, @ViewBuilder content: () -> Content) {
        self.spacing = spacing
        self.content = content()
    }
    
    var body: some View {
        VStack(spacing: spacing) {
            content
        }
    }
}

struct GlassButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(.regularMaterial, in: Capsule())
            .scaleEffect(configuration.isPressed ? 0.95 : 1.0)
            .animation(.spring(response: 0.3, dampingFraction: 0.6), value: configuration.isPressed)
    }
}

struct GlassProminentButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(.thickMaterial, in: Capsule()) // Simulating a prominent glass layer
            .overlay(Capsule().stroke(Color.primary.opacity(0.1), lineWidth: 1))
            .scaleEffect(configuration.isPressed ? 0.95 : 1.0)
            .animation(.spring(response: 0.3, dampingFraction: 0.6), value: configuration.isPressed)
    }
}

extension ButtonStyle where Self == GlassButtonStyle {
    static var glass: GlassButtonStyle { GlassButtonStyle() }
}

extension ButtonStyle where Self == GlassProminentButtonStyle {
    static var glassProminent: GlassProminentButtonStyle { GlassProminentButtonStyle() }
}
