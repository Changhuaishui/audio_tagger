package com.example.tagger.model

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * 统一的封面数据模型
 * 业务层以原始封面数据为准，UI 层按需解码为 Bitmap
 */
data class CoverArt(
    val bytes: ByteArray,
    val mimeType: String,
    val pictureType: Int = FRONT_COVER,
    val description: String = "",
    val width: Int = 0,
    val height: Int = 0
) {
    companion object {
        const val FRONT_COVER = 3
    }

    val isEmpty: Boolean
        get() = bytes.isEmpty()

    /**
     * 将封面字节解码为 Bitmap（用于 UI 展示）
     */
    fun toBitmap(): Bitmap? {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CoverArt
        return mimeType == other.mimeType &&
                pictureType == other.pictureType &&
                description == other.description &&
                width == other.width &&
                height == other.height &&
                bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + pictureType
        result = 31 * result + description.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}

/**
 * 从字节数组和 MIME 类型构造 CoverArt
 */
fun coverArtFromBytes(bytes: ByteArray?, mimeType: String?): CoverArt? {
    if (bytes == null || bytes.isEmpty()) return null
    return CoverArt(
        bytes = bytes,
        mimeType = mimeType ?: "image/jpeg"
    )
}
