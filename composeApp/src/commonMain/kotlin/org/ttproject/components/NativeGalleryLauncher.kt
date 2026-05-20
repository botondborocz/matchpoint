package org.ttproject.components

interface NativeGalleryLauncher {
    fun openGallery(
        images: List<String>,
        initialIndex: Int,
        isMineList: List<Boolean>, // 👈 CHANGED
        onDelete: (String) -> Unit,
        onReport: (String, String) -> Unit
    )
}