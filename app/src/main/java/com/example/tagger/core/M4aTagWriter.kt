package com.example.tagger.core

import android.util.Log
import org.mp4parser.Box
import org.mp4parser.Container
import org.mp4parser.IsoFile
import org.mp4parser.PropertyBoxParserImpl
import org.mp4parser.boxes.apple.*
import org.mp4parser.boxes.iso14496.part12.*
import org.mp4parser.tools.Path
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.Properties
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel

/**
 * M4A/MP4 metadata writer using mp4parser library.
 *
 * Supports writing iTunes-style metadata (MP4 atoms) to M4A files:
 * - Title (©nam) ✓
 * - Artist (©ART) ✓
 * - Album (©alb) ✓
 * - Comment (©cmt) ✓
 *
 * Not yet supported (mp4parser API limitations):
 * - Year (©day) - AppleRecordingYearBox has different API
 * - Genre (©gen) - only numeric genre codes supported
 * - Cover art (covr) - requires complex data structure
 *
 * Note: Raw AAC files (.aac) without MP4 container do NOT support metadata.
 * They must be wrapped in M4A container first.
 */
object M4aTagWriter {

    private const val TAG = "M4aTagWriter"

    /**
     * Result of write operation
     */
    sealed class Result {
        object Success : Result()
        data class Error(val message: String) : Result()
    }

    /**
     * Metadata to write to M4A file
     */
    data class M4aMetadata(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val year: String? = null,
        val genre: String? = null,
        val comment: String? = null,
        val coverArt: ByteArray? = null
    )

    /**
     * Check if the file is a valid M4A/MP4 container (not raw AAC)
     */
    fun isValidM4aFile(file: File): Boolean {
        if (!file.exists() || !file.canRead()) return false

        return try {
            // Check for MP4/M4A magic bytes (ftyp box)
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 8) return false

                // Skip first 4 bytes (box size), read box type
                raf.seek(4)
                val boxType = ByteArray(4)
                raf.read(boxType)
                val type = String(boxType)

                // Valid MP4/M4A files start with 'ftyp' box
                type == "ftyp"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking M4A file validity", e)
            false
        }
    }

    /**
     * Write metadata to M4A file
     *
     * @param file The M4A file to modify
     * @param metadata The metadata to write
     * @return Result indicating success or error
     */
    fun writeMetadata(file: File, metadata: M4aMetadata): Result {
        if (!file.exists()) {
            return Result.Error("文件不存在")
        }

        if (!file.canWrite()) {
            return Result.Error("没有写入权限")
        }

        if (!isValidM4aFile(file)) {
            return Result.Error("不是有效的 M4A 文件（可能是裸 AAC 流，不支持元数据）")
        }

        return try {
            writeMetadataInternal(file, metadata)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write M4A metadata", e)
            Result.Error("写入失败: ${e.message}")
        }
    }

    private fun writeMetadataInternal(file: File, metadata: M4aMetadata): Result {
        val isoFile = createIsoFile(file)

        try {
            val moov = isoFile.getBoxes(MovieBox::class.java).firstOrNull()
                ?: return Result.Error("无法找到 moov box")

            val freeBox = findFreeBox(moov)
            val correctOffset = needsOffsetCorrection(isoFile)
            val sizeBefore = moov.size

            // Calculate moov offset
            var moovOffset: Long = 0
            for (box in isoFile.boxes) {
                if (box.type == "moov") break
                moovOffset += box.size
            }

            // Ensure udta/meta/ilst structure exists
            val ilst = ensureAppleItemListBox(moov, freeBox)

            // Write metadata fields
            // Using individual setters to handle different box types
            metadata.title?.let { setTextBox(ilst, "©nam", it) }
            metadata.artist?.let { setTextBox(ilst, "©ART", it) }
            metadata.album?.let { setTextBox(ilst, "©alb", it) }
            metadata.year?.let { setTextBox(ilst, "©day", it) }
            // Genre (©gen) requires custom handling - skipped for now
            metadata.comment?.let { setTextBox(ilst, "©cmt", it) }
            metadata.coverArt?.let { setCoverArt(ilst, it) }

            val sizeAfter = moov.size
            var diff = sizeAfter - sizeBefore

            // Compensate size difference using FreeBox
            val currentFreeBox = findFreeBox(moov)
            if (currentFreeBox != null && currentFreeBox.data.limit() > diff) {
                val newSize = (currentFreeBox.data.limit() - diff).toInt()
                if (newSize > 8) { // Minimum FreeBox size
                    currentFreeBox.data = ByteBuffer.allocate(newSize)
                    diff = moov.size - sizeBefore
                }
            }

            // Correct chunk offsets if needed
            if (correctOffset && diff != 0L) {
                correctChunkOffsets(moov, diff)
            }

            // Serialize moov box
            val baos = ByteArrayOutputStream()
            moov.getBox(Channels.newChannel(baos))
            isoFile.close()

            // Write back to file
            val fc: FileChannel = if (diff != 0L) {
                splitFileAndInsert(file, moovOffset, sizeAfter - sizeBefore)
            } else {
                RandomAccessFile(file, "rw").channel
            }

            fc.position(moovOffset)
            fc.write(ByteBuffer.wrap(baos.toByteArray()))
            fc.close()

            Log.d(TAG, "M4A metadata written successfully: ${file.name}")
            return Result.Success

        } finally {
            try {
                isoFile.close()
            } catch (e: Exception) {
                // Ignore close errors
            }
        }
    }

    /**
     * Ensure the Apple Item List Box hierarchy exists: moov/udta/meta/ilst
     */
    private fun ensureAppleItemListBox(moov: MovieBox, existingFreeBox: FreeBox?): AppleItemListBox {
        // Get or create UserDataBox (udta)
        var udta: UserDataBox? = Path.getPath(moov, "udta")
        if (udta == null) {
            udta = UserDataBox()
            moov.addBox(udta)
        }

        // Get or create MetaBox (meta)
        var meta: MetaBox? = Path.getPath(udta, "meta")
        if (meta == null) {
            meta = MetaBox()
            val hdlr = HandlerBox()
            hdlr.handlerType = "mdir"
            meta.addBox(hdlr)
            udta.addBox(meta)
        }

        // Get or create AppleItemListBox (ilst)
        var ilst: AppleItemListBox? = Path.getPath(meta, "ilst")
        if (ilst == null) {
            ilst = AppleItemListBox()
            meta.addBox(ilst)
        }

        // Add FreeBox if not exists (for future metadata changes)
        if (existingFreeBox == null && findFreeBox(meta) == null) {
            val freeBox = FreeBox(32 * 1024) // 32KB buffer for future changes
            meta.addBox(freeBox)
        }

        return ilst
    }

    /**
     * Set a text-based Apple metadata field by type string
     * Creates the appropriate box type based on the fourCC code
     */
    private fun setTextBox(ilst: AppleItemListBox, type: String, value: String) {
        if (value.isBlank()) return

        try {
            // Remove existing box of this type
            val existing = ilst.boxes.firstOrNull { it.type == type }
            if (existing != null) {
                ilst.boxes.remove(existing)
            }

            // Create and configure appropriate box type
            when (type) {
                "©nam" -> {
                    val box = AppleNameBox()
                    box.dataCountry = 0
                    box.dataLanguage = 0
                    box.value = value
                    ilst.addBox(box)
                }
                "©ART" -> {
                    val box = AppleArtistBox()
                    box.dataCountry = 0
                    box.dataLanguage = 0
                    box.value = value
                    ilst.addBox(box)
                }
                "©alb" -> {
                    val box = AppleAlbumBox()
                    box.dataCountry = 0
                    box.dataLanguage = 0
                    box.value = value
                    ilst.addBox(box)
                }
                "©cmt" -> {
                    val box = AppleCommentBox()
                    box.dataCountry = 0
                    box.dataLanguage = 0
                    box.value = value
                    ilst.addBox(box)
                }
                "©day" -> {
                    // Note: AppleRecordingYearBox API differs from other text boxes
                    // Year metadata writing is skipped for now
                    Log.d(TAG, "Skipping year field for M4A (value: $value)")
                }
                else -> {
                    Log.w(TAG, "Unknown box type: $type")
                    return
                }
            }

            Log.d(TAG, "Set $type: $value")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set $type", e)
        }
    }

    /**
     * Set cover art for M4A file.
     * Uses AppleCoverBox with setJpg()/setPng() API.
     */
    private fun setCoverArt(ilst: AppleItemListBox, imageData: ByteArray) {
        try {
            // Remove existing cover art
            val existing = ilst.boxes.firstOrNull { it.type == "covr" }
            if (existing != null) {
                ilst.boxes.remove(existing)
            }

            val coverBox = AppleCoverBox()
            if (isPng(imageData)) {
                coverBox.setPng(imageData)
                Log.d(TAG, "Set PNG cover art: ${imageData.size} bytes")
            } else {
                coverBox.setJpg(imageData)
                Log.d(TAG, "Set JPEG cover art: ${imageData.size} bytes")
            }
            ilst.addBox(coverBox)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set cover art", e)
        }
    }

    /**
     * Check if image data is PNG
     */
    private fun isPng(data: ByteArray): Boolean {
        return data.size >= 8 &&
                data[0] == 0x89.toByte() &&
                data[1] == 0x50.toByte() && // P
                data[2] == 0x4E.toByte() && // N
                data[3] == 0x47.toByte()    // G
    }

    /**
     * Create IsoFile for Android.
     *
     * Note: isoparser-default.properties must be present in src/main/resources/
     * for PropertyBoxParserImpl to work on Android. The file provides box type
     * to class mappings that are loaded via getResourceAsStream().
     */
    private fun createIsoFile(file: File): IsoFile {
        val properties = loadIsoParserProperties()
        val boxParser = PropertyBoxParserImpl(properties)
        val randomAccessFile = RandomAccessFile(file.absolutePath, "r")
        return IsoFile(randomAccessFile.channel, boxParser)
    }

    /**
     * 加载 mp4parser 的 properties 映射文件。
     * Android 上 ClassLoader.getSystemResourceAsStream() 返回 null，
     * 需要尝试多个 ClassLoader 来找到 APK 内打包的 properties。
     */
    private fun loadIsoParserProperties(): Properties {
        val properties = Properties()
        val loaders = listOf(
            Thread.currentThread().contextClassLoader,
            M4aTagWriter::class.java.classLoader,
            ClassLoader.getSystemClassLoader()
        )
        for (loader in loaders) {
            loader?.getResourceAsStream("isoparser-default.properties")?.use { stream ->
                properties.load(stream)
                Log.d(TAG, "Loaded isoparser-default.properties from ${loader::class.java.simpleName}")
                return properties
            }
        }
        throw IllegalStateException("isoparser-default.properties not found in classpath")
    }

    /**
     * Check if offset correction is needed (moov before mdat)
     */
    private fun needsOffsetCorrection(isoFile: IsoFile): Boolean {
        // Fragmented files don't need correction
        if (Path.getPath<Box>(isoFile, "moov[0]/mvex[0]") != null) {
            return false
        }

        // Check order of moov and mdat
        for (box in isoFile.boxes) {
            if (box.type == "moov") return true
            if (box.type == "mdat") return false
        }

        return false
    }

    /**
     * Find FreeBox in container (recursively)
     */
    private fun findFreeBox(container: Container): FreeBox? {
        for (box in container.boxes) {
            if (box is FreeBox) return box
            if (box is Container) {
                val found = findFreeBox(box)
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * Correct chunk offsets after moov size change
     */
    private fun correctChunkOffsets(moov: MovieBox, correction: Long) {
        // Cast to Box to avoid overload ambiguity
        val moovAsBox: Box = moov

        // Try stco (32-bit offsets)
        var chunkOffsetBoxes: List<ChunkOffsetBox> =
            Path.getPaths(moovAsBox, "trak/mdia[0]/minf[0]/stbl[0]/stco[0]")

        // Try co64 (64-bit offsets) if stco not found
        if (chunkOffsetBoxes.isEmpty()) {
            chunkOffsetBoxes = Path.getPaths(moovAsBox, "trak/mdia[0]/minf[0]/stbl[0]/co64[0]")
        }

        for (chunkOffsetBox in chunkOffsetBoxes) {
            val offsets = chunkOffsetBox.chunkOffsets
            for (i in offsets.indices) {
                offsets[i] += correction
            }
        }

        Log.d(TAG, "Corrected ${chunkOffsetBoxes.size} chunk offset boxes by $correction bytes")
    }

    /**
     * Split file and insert space for expanded moov box
     */
    private fun splitFileAndInsert(file: File, pos: Long, length: Long): FileChannel {
        val read = RandomAccessFile(file, "r").channel
        val tmp = File.createTempFile("M4aTagWriter", ".tmp")

        try {
            val tmpWrite = RandomAccessFile(tmp, "rw").channel
            read.position(pos)
            tmpWrite.transferFrom(read, 0, read.size() - pos)
            read.close()

            val write = RandomAccessFile(file, "rw").channel
            write.position(pos + length)
            tmpWrite.position(0)

            var transferred: Long = 0
            while (transferred < tmpWrite.size()) {
                transferred += tmpWrite.transferTo(transferred, tmpWrite.size() - transferred, write)
            }

            tmpWrite.close()
            return write
        } finally {
            tmp.delete()
        }
    }
}
