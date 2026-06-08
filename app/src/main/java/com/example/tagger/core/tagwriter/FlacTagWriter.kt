package com.example.tagger.core.tagwriter

import android.graphics.BitmapFactory
import android.util.Log
import com.example.tagger.model.CoverArt
import org.jaudiotagger.audio.flac.metadatablock.MetadataBlockDataPicture
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag

private const val TAG = "FlacTagWriter"

class FlacTagWriter : JAudioTagWriter() {
    override fun writeCover(tag: Tag, imageData: ByteArray, mimeType: String) {
        val flacTag = tag as? FlacTag ?: return super.writeCover(tag, imageData, mimeType)

        flacTag.deleteArtworkField()

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageData, 0, imageData.size, options)

        val picture = MetadataBlockDataPicture(
            imageData,
            CoverArt.FRONT_COVER,
            mimeType,
            "",
            options.outWidth,
            options.outHeight,
            0,
            0
        )

        flacTag.setField(picture)
        Log.d(TAG, "FLAC cover written: ${imageData.size} bytes, ${options.outWidth}x${options.outHeight}, $mimeType")
    }
}
