package com.example.tagger.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.tagger.core.video.*
import com.example.tagger.ui.theme.AppPrimaryColor
import com.example.tagger.ui.theme.AudioTaggerTheme

/**
 * Bottom sheet for selecting audio output format and track.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatSelectorSheet(
    metadata: VideoMetadata,
    config: ExtractionConfig,
    onFormatSelected: (AudioFormat) -> Unit,
    onTrackSelected: (Int) -> Unit,
    onStartExtraction: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Title
            Text(
                text = "提取音轨",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Video info card
            VideoInfoCard(metadata)

            Spacer(modifier = Modifier.height(24.dp))

            // Audio track selection (if multiple tracks)
            if (metadata.audioTracks.size > 1) {
                Text(
                    text = "选择音轨",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                AudioTrackSelector(
                    tracks = metadata.audioTracks,
                    selectedIndex = config.trackIndex,
                    onTrackSelected = onTrackSelected
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Format selection
            Text(
                text = "输出格式",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            FormatSelector(
                selectedFormat = config.format,
                originalCodec = metadata.audioTracks.getOrNull(config.trackIndex)?.codec,
                onFormatSelected = onFormatSelected
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Extract button
            Button(
                onClick = onStartExtraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppPrimaryColor
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCut,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "开始提取",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun VideoInfoCard(metadata: VideoMetadata) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VideoFile,
                    contentDescription = null,
                    tint = AppPrimaryColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = metadata.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                InfoChip(
                    icon = Icons.Default.Schedule,
                    text = metadata.formattedDuration
                )
                InfoChip(
                    icon = Icons.Default.Storage,
                    text = metadata.formattedFileSize
                )
                InfoChip(
                    icon = Icons.Default.AudioFile,
                    text = "${metadata.audioTracks.size} 音轨"
                )
            }

            // Show default audio track info
            metadata.defaultAudioTrack?.let { track ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = track.codec.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = track.channelInfo,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = track.formattedSampleRate,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    track.bitrate?.let {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = track.formattedBitrate,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AudioTrackSelector(
    tracks: List<AudioTrackInfo>,
    selectedIndex: Int,
    onTrackSelected: (Int) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tracks.forEach { track ->
            val isSelected = track.index == selectedIndex
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTrackSelected(track.index) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        AppPrimaryColor.copy(alpha = 0.1f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                border = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(2.dp, AppPrimaryColor)
                } else null,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onTrackSelected(track.index) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = AppPrimaryColor
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = track.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Text(
                            text = "${track.formattedSampleRate} • ${track.formattedBitrate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatSelector(
    selectedFormat: AudioFormat,
    originalCodec: String?,
    onFormatSelected: (AudioFormat) -> Unit
) {
    val formats = AudioFormat.entries

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(formats) { format ->
            FormatChip(
                format = format,
                isSelected = format == selectedFormat,
                isRecommended = format == AudioFormat.ORIGINAL,
                originalCodec = if (format == AudioFormat.ORIGINAL) originalCodec else null,
                onClick = { onFormatSelected(format) }
            )
        }
    }
}

@Composable
private fun FormatChip(
    format: AudioFormat,
    isSelected: Boolean,
    isRecommended: Boolean,
    originalCodec: String?,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .clip(shape)
            .background(
                if (isSelected) AppPrimaryColor.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .then(
                if (isSelected) Modifier.border(2.dp, AppPrimaryColor, shape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = format.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) AppPrimaryColor else MaterialTheme.colorScheme.onSurface
        )

        if (format == AudioFormat.ORIGINAL && originalCodec != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "(${originalCodec.uppercase()})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isRecommended) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "推荐",
                style = MaterialTheme.typography.labelSmall,
                color = AppPrimaryColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ==================== Previews ====================

@Preview(showBackground = true)
@Composable
private fun VideoInfoCardPreview() {
    val metadata = VideoMetadata(
        uri = android.net.Uri.EMPTY,
        displayName = "演唱会现场 - 周杰伦.mp4",
        durationMs = 3600000L,  // 1小时
        fileSize = 1024L * 1024 * 500,  // 500MB
        containerFormat = "mp4",
        audioTracks = listOf(
            AudioTrackInfo(
                index = 0,
                codec = "aac",
                sampleRate = 48000,
                channels = 2,
                bitrate = 192000,
                language = "chi"
            )
        )
    )
    AudioTaggerTheme {
        VideoInfoCard(metadata)
    }
}

@Preview(showBackground = true)
@Composable
private fun FormatSelectorPreview() {
    AudioTaggerTheme {
        FormatSelector(
            selectedFormat = AudioFormat.ORIGINAL,
            originalCodec = "aac",
            onFormatSelected = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AudioTrackSelectorPreview() {
    val tracks = listOf(
        AudioTrackInfo(0, "aac", 48000, 2, 192000, "chi"),
        AudioTrackInfo(1, "ac3", 48000, 6, 384000, "eng")
    )
    AudioTaggerTheme {
        AudioTrackSelector(
            tracks = tracks,
            selectedIndex = 0,
            onTrackSelected = {}
        )
    }
}
