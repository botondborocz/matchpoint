import SwiftUI
import UIKit // 👈 Added: Needed for UIRectCorner and UIBezierPath mechanics

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
                Button(action: {
                    withAnimation(.spring(response: 0.28, dampingFraction: 0.7)) {
                        currentTab = id
                    }
                }) {
                    VStack(spacing: 4) {
                        Image(systemName: icon)
                            .font(.system(size: 20, weight: currentTab == id ? .bold : .medium))
                            .scaleEffect(currentTab == id ? 1.12 : 1.0)
                        Text(label)
                            .font(.system(size: 10, weight: currentTab == id ? .bold : .semibold))
                    }
                    .foregroundColor(currentTab == id ? .orange : .secondary.opacity(0.8))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                .buttonStyle(.plain)
            }
        }
        .frame(height: 74)
        .background(.ultraThinMaterial)
        .background(
            LinearGradient(
                colors: [.white.opacity(0.18), .clear, .black.opacity(0.06)],
                startPoint: .top,
                endPoint: .bottom
            )
        )
        .clipShape(RoundedCornerShape(radius: 37, corners: .allCorners))
        .overlay(
            RoundedCornerShape(radius: 37, corners: .allCorners)
                .stroke(
                    LinearGradient(
                        colors: [
                            .white.opacity(0.65),
                            .white.opacity(0.20),
                            .clear,
                            .black.opacity(0.12),
                            .white.opacity(0.30)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: 1.5
                )
        )
        .shadow(color: Color.black.opacity(0.06), radius: 3, x: 0, y: 2)
        .shadow(color: Color.black.opacity(0.14), radius: 14, x: 0, y: 10)
        .padding(.horizontal, 16)
    }
}

// 👇 FIXED: Added the missing custom container clip shape back to the local target workspace
struct RoundedCornerShape: Shape {
    var radius: CGFloat = .infinity
    var corners: UIRectCorner = .allCorners

    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(roundedRect: rect, byRoundingCorners: corners, cornerRadii: CGSize(width: radius, height: radius))
        return Path(path.cgPath)
    }
}