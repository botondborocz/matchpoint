import SwiftUI
import PhotosUI

struct PHPickerRepresentable: UIViewControllerRepresentable {
    let maxItems: Int
    let onImagesSelected: ([Data]) -> Void
    @Environment(\.dismiss) var dismiss

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var config = PHPickerConfiguration()
        config.filter = .images
        config.selectionLimit = maxItems
        
        let picker = PHPickerViewController(configuration: config)
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: PHPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    class Coordinator: NSObject, PHPickerViewControllerDelegate {
        let parent: PHPickerRepresentable

        init(_ parent: PHPickerRepresentable) {
            self.parent = parent
        }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            parent.dismiss()

            let itemProviders = results.map { $0.itemProvider }
            var selectedDataList: [Data] = []
            let group = DispatchGroup()

            for provider in itemProviders {
                if provider.canLoadObject(ofClass: UIImage.self) {
                    group.enter()
                    provider.loadObject(ofClass: UIImage.self) { image, error in
                        DispatchQueue.main.async {
                            if let uiImage = image as? UIImage,
                               let data = uiImage.jpegData(compressionQuality: 0.8) {
                                selectedDataList.append(data)
                            }
                            group.leave()
                        }
                    }
                }
            }

            group.notify(queue: .main) {
                self.parent.onImagesSelected(selectedDataList)
            }
        }
    }
}
