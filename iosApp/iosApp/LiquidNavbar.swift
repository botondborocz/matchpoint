import SwiftUI

struct LiquidNavbar: View {
    @Binding var currentTab: String

    let tabs = [
        ("map", "map.fill", "Map"),
        ("match", "sportscourt.fill", "Match"),
        ("coach", "cpu", "AI Coach"),
        ("messages", "bubble.left.and.bubble.right.fill", "Messages"),
        ("profile", "person.crop.circle.fill", "Profile")
    ]

    var body: some View {
        HStack(spacing: 0) {
            ForEach(tabs, id: \.0) { id, icon, label in
                Button(action: { currentTab = id }) {
                    VStack(spacing: 4) {
                        Image(systemName: icon)
                            .font(.system(size: 22, weight: .medium))
                        Text(label)
                            .font(.system(size: 10, weight: .bold))
                    }
                    .foregroundColor(currentTab == id ? .orange : .secondary)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
        }
        .frame(height: 72)
        .background(.ultraThinMaterial) // 👈 Core Liquid Glass Material
        .clipShape(RoundedCornerShape(radius: 36, corners: .allCorners))
        .overlay(
            RoundedCornerShape(radius: 36, corners: .allCorners)
                .stroke(
                    LinearGradient(
                        colors: [.white.opacity(0.35), .clear, .black.opacity(0.1)],
                        startPoint: .top,
                        endPoint: .bottom
                    ),
                    lineWidth: 1
                )
        )
        .shadow(color: Color.black.opacity(0.15), radius: 15, x: 0, y: 10)
        .padding(.horizontal, 16)
    }
}

// Simple helper for rounding specific corners
struct RoundedCornerShape: Shape {
    var radius: CGFloat = .infinity
    var corners: UIRectCorner = .allCorners

    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(roundedRect: rect, byRoundingCorners: corners, cornerRadii: CGSize(width: radius, height: radius))
        return Path(path.cgPath)
    }
}