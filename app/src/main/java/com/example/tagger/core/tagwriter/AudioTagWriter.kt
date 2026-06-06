package com.example.tagger.core.tagwriter

import com.example.tagger.core.WriteResult
import com.example.tagger.model.AudioMetadata
import java.io.File

interface AudioTagWriter {
    fun write(file: File, metadata: AudioMetadata, actualFormat: String? = null): WriteResult
}
