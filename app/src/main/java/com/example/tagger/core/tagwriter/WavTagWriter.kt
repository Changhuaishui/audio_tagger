package com.example.tagger.core.tagwriter

import android.util.Log
import com.example.tagger.model.CoverArt
import org.jaudiotagger.audio.wav.WavOptions
import org.jaudiotagger.audio.wav.WavSaveOptions
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.wav.WavTag

private const val TAG = "WavTagWriter"

class WavTagWriter : JAudioTagWriter() {
    override fun <T> withWriteOptions(block: () -> T): T = synchronized(wavOptionsLock) {
        val options = TagOptionSingleton.getInstance()
        val previousReadOptions = options.wavOptions
        val previousSaveOptions = options.wavSaveOptions

        try {
            options.wavOptions = WavOptions.READ_ID3_ONLY_AND_SYNC
            options.wavSaveOptions = WavSaveOptions.SAVE_BOTH
            block()
        } finally {
            options.wavOptions = previousReadOptions
            options.wavSaveOptions = previousSaveOptions
        }
    }

    override fun writeCover(tag: Tag, imageData: ByteArray, mimeType: String) {
        val wavTag = tag as? WavTag ?: return super.writeCover(tag, imageData, mimeType)

        val id3Tag = wavTag.iD3Tag ?: WavTag.createDefaultID3Tag()
        id3Tag.deleteArtworkField()

        val artwork = ArtworkFactory.getNew()
        artwork.binaryData = imageData
        artwork.mimeType = mimeType
        artwork.pictureType = CoverArt.FRONT_COVER
        id3Tag.setField(artwork)

        wavTag.setID3Tag(id3Tag)
        Log.d(TAG, "WAV cover art written to ID3 tag: ${imageData.size} bytes")
    }

    override fun beforeCommit(tag: Tag) {
        val wavTag = tag as? WavTag ?: return
        if (wavTag.isID3Tag) {
            wavTag.syncTagBeforeWrite()
        }
    }

    companion object {
        private val wavOptionsLock = Any()
    }
}
