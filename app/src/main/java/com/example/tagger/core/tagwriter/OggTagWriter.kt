package com.example.tagger.core.tagwriter

import android.graphics.BitmapFactory
import android.util.Log
import org.jaudiotagger.audio.flac.metadatablock.MetadataBlockDataPicture
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentFieldKey
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import java.util.Base64

private const val TAG = "OggTagWriter"

class OggTagWriter : JAudioTagWriter() {
    override fun writeCover(tag: Tag, imageData: ByteArray, mimeType: String) {
        val vorbisTag = tag as? VorbisCommentTag ?: return super.writeCover(tag, imageData, mimeType)

        vorbisTag.deleteArtworkField()

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageData, 0, imageData.size, options)

        val picture = MetadataBlockDataPicture(
            imageData,
            3,
            mimeType,
            "",
            options.outWidth,
            options.outHeight,
            0,
            0
        )
        val base64Picture = Base64.getEncoder().encodeToString(picture.rawContent)
        vorbisTag.setField(
            vorbisTag.createField(VorbisCommentFieldKey.METADATA_BLOCK_PICTURE, base64Picture)
        )

        Log.d(TAG, "OGG cover written as METADATA_BLOCK_PICTURE: ${imageData.size} bytes, $mimeType")
    }
}
