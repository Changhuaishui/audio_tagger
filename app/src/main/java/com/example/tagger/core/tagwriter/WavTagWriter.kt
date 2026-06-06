package com.example.tagger.core.tagwriter

import android.util.Log
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.wav.WavTag

private const val TAG = "WavTagWriter"

class WavTagWriter : JAudioTagWriter() {
    override fun writeCover(tag: Tag, imageData: ByteArray, mimeType: String) {
        val wavTag = tag as? WavTag ?: return super.writeCover(tag, imageData, mimeType)

        val id3Tag = wavTag.iD3Tag ?: WavTag.createDefaultID3Tag()
        id3Tag.deleteArtworkField()

        val artwork = ArtworkFactory.getNew()
        artwork.binaryData = imageData
        artwork.mimeType = mimeType
        artwork.pictureType = 3
        id3Tag.setField(artwork)

        wavTag.setID3Tag(id3Tag)
        Log.d(TAG, "WAV cover art written to ID3 tag: ${imageData.size} bytes")
    }

    override fun beforeCommit(tag: Tag) {
        val wavTag = tag as? WavTag ?: return
        if (wavTag.isExistingId3Tag) {
            wavTag.syncTagBeforeWrite()
        }
    }
}
