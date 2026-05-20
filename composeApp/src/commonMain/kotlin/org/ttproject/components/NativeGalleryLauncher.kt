package org.ttproject.components

// In commonMain
interface NativeGalleryLauncher {
    fun openGallery(
        images: List<String>,
        initialIndex: Int,
        isMine: Boolean,
        onDelete: (String) -> Unit,
        onReport: (String, String) -> Unit
    )
}