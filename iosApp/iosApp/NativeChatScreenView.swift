import SwiftUI
import shared
import Combine
import PhotosUI

class NativeChatViewModel: ObservableObject {
    @Published var messages: [MessageDto] = []
    @Published var isLoading: Bool = true
    @Published var messageText: String = ""
    @Published var isUploadingMedia: Bool = false
    
    private var helper: IosChatViewModelHelper?
    private let connectionId: String
    let currentUserId: String
    
    init(connectionId: String) {
        self.connectionId = connectionId
        self.currentUserId = KoinHelper.shared.getTokenStorage().getUserId() ?? ""
        let koinVm = KoinHelper.shared.getChatViewModel(connectionId: connectionId)
        self.helper = IosChatViewModelHelper(viewModel: koinVm)
        
        self.helper?.subscribeMessages { [weak self] msgs in
            DispatchQueue.main.async {
                self?.messages = msgs
            }
        }
        self.helper?.subscribeIsLoading { [weak self] loading in
            DispatchQueue.main.async {
                self?.isLoading = loading.boolValue
            }
        }
    }
    
    func sendMessage() {
        guard !messageText.isEmpty else { return }
        helper?.sendMessage(text: messageText, replyToMessageId: nil)
        messageText = ""
    }
    
    func sendReaction(messageId: String, emoji: String) {
        helper?.sendReaction(messageId: messageId, emoji: emoji)
    }
    
    func removeReaction(messageId: String) {
        helper?.removeReaction(messageId: messageId)
    }
    
    func markAsRead() {
        helper?.markMessagesAsRead()
    }
    
    func sendImage(data: Data) {
        let nsData = NSData(data: data)
        let byteArray = KotlinByteArray(size: Int32(nsData.length))
        for i in 0..<nsData.length {
            var byte: Int8 = 0
            nsData.getBytes(&byte, range: NSRange(location: i, length: 1))
            byteArray.set(index: Int32(i), value: byte)
        }
        helper?.sendImagesMessage(connectionId: connectionId, mediaBytes: [byteArray], replyToMessageId: nil)
    }
    
    func deinitHelper() {
        helper?.clear()
    }
}

struct NativeChatScreenView: View {
    let connectionId: String
    let otherUserName: String
    let otherUserImage: String?
    
    @StateObject private var vm: NativeChatViewModel
    @Environment(\.presentationMode) var presentationMode
    @EnvironmentObject var appState: AppState
    @Namespace private var glassNamespace
    
    @State private var selectedItem: PhotosPickerItem? = nil
    
    init(connectionId: String, otherUserName: String, otherUserImage: String?) {
        self.connectionId = connectionId
        self.otherUserName = otherUserName
        self.otherUserImage = otherUserImage
        _vm = StateObject(wrappedValue: NativeChatViewModel(connectionId: connectionId))
    }
    
    var body: some View {
        ZStack(alignment: .top) {
            Color(hex: "#0F172A").ignoresSafeArea() // Deep theme background
            
            VStack(spacing: 0) {
                // Messages List
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            if vm.isLoading {
                                ProgressView().padding()
                            }
                            
                            ForEach(vm.messages, id: \.id) { msg in
                                ChatBubbleView(msg: msg, currentUserId: vm.currentUserId, otherUserImage: otherUserImage) { clickedUrl in
                                    // Open NativeSwiftGalleryView
                                    let urls = extractUrls(from: msg.content)
                                    let index = urls.firstIndex(of: clickedUrl) ?? 0
                                    appState.galleryData = GalleryData(
                                        images: urls,
                                        initialIndex: index,
                                        isMineList: urls.map { _ in msg.senderId == vm.currentUserId },
                                        onDelete: { _ in },
                                        onReport: { _, _ in }
                                    )
                                } onReact: { emoji in
                                    vm.sendReaction(messageId: msg.id, emoji: emoji)
                                }
                                .id(msg.id)
                            }
                        }
                        .padding(.horizontal)
                        .padding(.top, 20)
                        .padding(.bottom, 10)
                    }
                    .onChange(of: vm.messages.count) { _ in
                        if let last = vm.messages.last {
                            withAnimation {
                                proxy.scrollTo(last.id, anchor: .bottom)
                            }
                        }
                    }
                    .onAppear {
                        if let last = vm.messages.last {
                            proxy.scrollTo(last.id, anchor: .bottom)
                        }
                    }
                }
                
                // Input Bar (Liquid Glass)
                HStack(spacing: 12) {
                    PhotosPicker(selection: $selectedItem, matching: .images, photoLibrary: .shared()) {
                        Image(systemName: "plus.circle.fill")
                            .font(.system(size: 24))
                            .foregroundColor(.secondary)
                    }
                    .onChange(of: selectedItem) { newItem in
                        Task {
                            if let data = try? await newItem?.loadTransferable(type: Data.self) {
                                vm.sendImage(data: data)
                            }
                        }
                    }
                    
                    TextField("Message...", text: $vm.messageText)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Color.black.opacity(0.15))
                        .clipShape(Capsule())
                        .foregroundColor(.primary)
                    
                    if !vm.messageText.isEmpty {
                        Button(action: { vm.sendMessage() }) {
                            Image(systemName: "arrow.up.circle.fill")
                                .font(.system(size: 28))
                                .foregroundColor(Color(hex: "#FF6B35"))
                        }
                    }
                }
                .padding(.horizontal)
                .padding(.vertical, 12)
                .glassEffect(.regular.interactive(), in: Capsule())
                .glassEffectID("chatInput", in: glassNamespace)
                .padding(.horizontal, 8)
                .padding(.bottom, 8) // Floating above the keyboard/bezel
            }
        }
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button(action: {
                    presentationMode.wrappedValue.dismiss()
                }) {
                    HStack(spacing: 8) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 16, weight: .bold))
                        
                        if let img = otherUserImage, !img.isEmpty {
                            AsyncImage(url: URL(string: img)) { phase in
                                if let image = phase.image {
                                    image.resizable().aspectRatio(contentMode: .fill)
                                } else {
                                    Color.black.opacity(0.2)
                                }
                            }
                            .frame(width: 32, height: 32)
                            .clipShape(Circle())
                        } else {
                            Circle()
                                .fill(Color(hex: "#FF6B35").opacity(0.2))
                                .frame(width: 32, height: 32)
                                .overlay(
                                    Text(String(otherUserName.prefix(2)).uppercased())
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundColor(Color(hex: "#FF6B35"))
                                )
                        }
                        
                        Text(otherUserName).font(.system(size: 16, weight: .bold))
                    }
                    .foregroundColor(.primary)
                }
            }
        }
        .toolbarBackground(.hidden, for: .navigationBar)
        .onAppear {
            vm.markAsRead()
            appState.isTabBarHidden = true // hide tab bar when entering chat
        }
        .onDisappear {
            vm.deinitHelper()
            appState.isTabBarHidden = false // show tab bar when leaving chat
        }
    }
    
    private func extractUrls(from content: String) -> [String] {
        if content.starts(with: "[IMAGE]") || content.starts(with: "[IMAGES]") {
            let stripped = content.replacingOccurrences(of: "[IMAGE]", with: "").replacingOccurrences(of: "[IMAGES]", with: "")
            return stripped.components(separatedBy: ",")
        }
        return []
    }
}

struct ChatBubbleView: View {
    let msg: MessageDto
    let currentUserId: String
    let otherUserImage: String?
    let onImageClick: (String) -> Void
    let onReact: (String) -> Void
    
    var isMine: Bool {
        msg.senderId == currentUserId
    }
    
    var isImageMessage: Bool {
        msg.content.starts(with: "[IMAGE]") || msg.content.starts(with: "[IMAGES]")
    }
    
    var urls: [String] {
        if isImageMessage {
            let stripped = msg.content.replacingOccurrences(of: "[IMAGE]", with: "").replacingOccurrences(of: "[IMAGES]", with: "")
            return stripped.components(separatedBy: ",")
        }
        return []
    }
    
    var body: some View {
        HStack(alignment: .bottom, spacing: 8) {
            if !isMine {
                if let img = otherUserImage, !img.isEmpty {
                    AsyncImage(url: URL(string: img)) { phase in
                        if let image = phase.image {
                            image.resizable().aspectRatio(contentMode: .fill)
                        } else {
                            Color.black.opacity(0.2)
                        }
                    }
                    .frame(width: 28, height: 28)
                    .clipShape(Circle())
                } else {
                    Circle()
                        .fill(Color(hex: "#FF6B35").opacity(0.2))
                        .frame(width: 28, height: 28)
                }
            } else {
                Spacer(minLength: 40)
            }
            
            VStack(alignment: isMine ? .trailing : .leading, spacing: 4) {
                if isImageMessage {
                    // Gallery Grid
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4), count: max(1, min(urls.count, 2))), spacing: 4) {
                        ForEach(urls, id: \.self) { url in
                            AsyncImage(url: URL(string: url)) { phase in
                                if let image = phase.image {
                                    image.resizable()
                                        .aspectRatio(contentMode: .fill)
                                        .frame(height: 150)
                                        .clipShape(RoundedRectangle(cornerRadius: 16))
                                        .onTapGesture {
                                            onImageClick(url)
                                        }
                                } else {
                                    RoundedRectangle(cornerRadius: 16)
                                        .fill(Color.black.opacity(0.15))
                                        .frame(height: 150)
                                        .overlay(ProgressView())
                                }
                            }
                        }
                    }
                    .frame(maxWidth: 240)
                } else {
                    Text(msg.content)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                        .background(isMine ? Color(hex: "#FF6B35") : Color.clear)
                        .foregroundColor(isMine ? .white : .primary)
                        .glassEffect(isMine ? .clear : .regular, in: RoundedRectangle(cornerRadius: 20))
                }
                
                // Reactions
                if !msg.reactions.isEmpty {
                    HStack(spacing: 4) {
                        ForEach(msg.reactions, id: \.userId) { reaction in
                            Text(reaction.emoji)
                                .font(.system(size: 14))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 4)
                                .background(Color.black.opacity(0.25), in: Capsule())
                        }
                    }
                    .padding(isMine ? .trailing : .leading, 12)
                    .offset(y: -10)
                }
            }
            .contextMenu {
                Button { onReact("👍") } label: { Label("Like", systemImage: "hand.thumbsup") }
                Button { onReact("❤️") } label: { Label("Love", systemImage: "heart") }
                Button { onReact("😂") } label: { Label("Haha", systemImage: "face.smiling") }
            }
            
            if !isMine {
                Spacer(minLength: 40)
            }
        }
    }
}
