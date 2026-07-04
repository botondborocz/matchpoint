import SwiftUI
import ComposeApp
import Combine

class NativeMessagesViewModel: ObservableObject {
    @Published var threads: [ChatThreadDto] = []
    @Published var isLoading: Bool = true
    @Published var searchQuery: String = ""

    private var helper: IosMessagesViewModelHelper?

    init() {
        let koinVm = KoinHelper.shared.getMessagesViewModel()
        self.helper = IosMessagesViewModelHelper(viewModel: koinVm)

        self.helper?.subscribeThreads { [weak self] newThreads in
            DispatchQueue.main.async {
                self?.threads = newThreads
            }
        }
        self.helper?.subscribeIsLoading { [weak self] loading in
            DispatchQueue.main.async {
                self?.isLoading = loading.boolValue
            }
        }
        self.helper?.subscribeSearchQuery { [weak self] query in
            DispatchQueue.main.async {
                self?.searchQuery = query
            }
        }
    }

    func updateSearch(query: String) {
        helper?.updateSearchQuery(query: query)
    }

    func load() {
        helper?.loadConnections(isBackgroundRefresh: false)
    }

    func deinitHelper() {
        helper?.clear()
    }
}

struct NativeMessagesScreenView: View {
    @StateObject private var vm = NativeMessagesViewModel()
    @Namespace private var glassNamespace

    var body: some View {
        ZStack(alignment: .top) {
            Color(hex: "#0F172A").ignoresSafeArea() // Deep background to make glass pop

            ScrollView {
                VStack(spacing: 16) {
                    GlassEffectContainer(spacing: 12) {
                        HStack(spacing: 8) {
                            Image(systemName: "magnifyingglass")
                                .foregroundColor(.secondary)
                            
                            TextField("Search users...", text: Binding(
                                get: { vm.searchQuery },
                                set: { vm.updateSearch(query: $0) }
                            ))
                            .foregroundColor(.primary)
                            .accentColor(Color(hex: "#FF6B35"))
                            .font(.system(size: 15))
                            
                            if !vm.searchQuery.isEmpty {
                                Button(action: { vm.updateSearch(query: "") }) {
                                    Image(systemName: "xmark.circle.fill")
                                        .foregroundColor(.secondary)
                                }
                            }
                            
                            Button(action: {
                                // Dictation action
                            }) {
                                Image(systemName: "mic.fill")
                                    .foregroundColor(.secondary)
                            }
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .glassEffect(.regular.interactive(), in: Capsule())
                        .glassEffectID("searchBar", in: glassNamespace)
                        .overlay(Capsule().stroke(Color.primary.opacity(0.1), lineWidth: 1))
                        .padding(.horizontal)
                        .padding(.top, 16) // Padding below safe area
                    }

                    // List of Threads
                    if vm.isLoading && vm.threads.isEmpty {
                        ProgressView().padding(.top, 40)
                    } else if vm.threads.isEmpty {
                        Text("No messages found.")
                            .foregroundColor(.secondary)
                            .padding(.top, 40)
                    } else {
                        VStack(spacing: 12) {
                            ForEach(vm.threads, id: \.id) { thread in
                                NavigationLink(destination: NativeChatScreenView(connectionId: thread.id, otherUserName: thread.otherUserName, otherUserImage: thread.otherUserImageUrl)) {
                                    ThreadRowView(thread: thread)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal)
                        .padding(.bottom, 100) // Padding for tab bar
                    }
                }
            }
        }
        .onAppear {
            vm.load()
        }
        .navigationBarTitleDisplayMode(.inline)
        .navigationTitle("Messages")
        // Toolbar background transparency for Liquid Glass style
        .toolbarBackground(.hidden, for: .navigationBar)
    }
}

struct ThreadRowView: View {
    let thread: ChatThreadDto

    var body: some View {
        HStack(spacing: 16) {
            // Avatar
            ZStack(alignment: .bottomTrailing) {
                if let imageUrl = thread.otherUserImageUrl, !imageUrl.isEmpty {
                    AsyncImage(url: URL(string: imageUrl)) { phase in
                        if let image = phase.image {
                            image.resizable().aspectRatio(contentMode: .fill)
                        } else {
                            Color.black.opacity(0.2)
                        }
                    }
                    .frame(width: 50, height: 50)
                    .clipShape(Circle())
                } else {
                    Circle()
                        .fill(Color(hex: "#FF6B35").opacity(0.2))
                        .frame(width: 50, height: 50)
                        .overlay(
                            Text(String(thread.otherUserName.prefix(2)).uppercased())
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(Color(hex: "#FF6B35"))
                        )
                }

                // Online indicator
                if thread.isOnline {
                    Circle()
                        .fill(Color.green)
                        .frame(width: 14, height: 14)
                        .overlay(Circle().stroke(Color.black, lineWidth: 2))
                        .offset(x: 0, y: 0)
                }
            }

            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(thread.otherUserName)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.primary)
                    Spacer()
                    Text(formatShortDate(thread.timestamp))
                        .font(.system(size: 12))
                        .foregroundColor(thread.unreadCount > 0 ? Color(hex: "#FF6B35") : .secondary)
                }
                
                HStack {
                    Text(formatPreview(thread.lastMessage))
                        .font(.system(size: 14))
                        .foregroundColor(thread.unreadCount > 0 ? .primary : .secondary)
                        .lineLimit(1)
                    Spacer()
                    if thread.unreadCount > 0 {
                        Circle()
                            .fill(Color(hex: "#FF6B35"))
                            .frame(width: 20, height: 20)
                            .overlay(
                                Text("\(thread.unreadCount)")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(.white)
                            )
                    }
                }
            }
        }
        .padding()
        .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.primary.opacity(0.05), lineWidth: 1))
    }
    
    private func formatShortDate(_ isoString: String) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: isoString) ?? ISO8601DateFormatter().date(from: isoString) {
            let df = DateFormatter()
            df.dateStyle = .short
            df.timeStyle = .short
            return df.string(from: date)
        }
        return isoString
    }
    
    private func formatPreview(_ msg: String) -> String {
        if msg.starts(with: "[IMAGE]") || msg.starts(with: "[IMAGES]") { return "📷 Photo" }
        if msg.starts(with: "[VIDEO]") { return "🎥 Video" }
        if msg.starts(with: "[VOICE]") { return "🎤 Voice Message" }
        return msg
    }
}
