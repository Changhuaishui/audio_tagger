package com.example.tagger.ui.player

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ShuffleOn
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.example.tagger.model.AudioMetadata
import com.example.tagger.model.CoverArt

/**
 * 获取用于显示的 Bitmap（优先 coverArt，其次 cover 模型）
 */
private fun resolveCoverBitmap(coverArt: Bitmap?, cover: CoverArt?): Bitmap? {
    return coverArt ?: cover?.toBitmap()
}

/**
 * 获取 Compose Blur RenderEffect
 */
private fun blurRenderEffect(radius: Float = 80f): androidx.compose.ui.graphics.RenderEffect? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP).asComposeRenderEffect()
    } else null
}

/**
 * Apple Music 风格沉浸式全屏播放详情页（Blur 背景版）
 *
 * @param playerState 播放器 UI 状态
 * @param onClose 关闭全屏页回调
 * @param onTogglePlayPause 播放/暂停切换
 * @param onPlayNext 下一首
 * @param onPlayPrevious 上一首
 * @param onSeekTo 拖动 seek 回调
 * @param onToggleShuffle 切换随机
 * @param onCycleRepeat 切换循环
 * @param onOpenQueue 打开播放队列
 */
@Composable
fun FullPlayerScreen(
    playerState: PlayerUiState,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit = {},
    onSeekToItem: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentItem = playerState.currentItem
    val playlist = playerState.playlist
    val currentIndex = playerState.currentIndex

    // 封面轮播状态
    val pagerState = rememberPagerState(
        initialPage = currentIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0)),
        pageCount = { playlist.size }
    )

    // 当外部切歌时同步 pager
    LaunchedEffect(currentIndex) {
        if (pagerState.currentPage != currentIndex && currentIndex in playlist.indices) {
            pagerState.animateScrollToPage(currentIndex)
        }
    }

    // 滑动 pager 后切歌（手指释放后）
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != currentIndex && pagerState.settledPage in playlist.indices) {
            onSeekToItem(pagerState.settledPage)
        }
    }

    // 提取封面和主题色
    val coverBitmap = remember(currentItem?.uri) {
        currentItem?.let { resolveCoverBitmap(it.coverArt, it.cover) }
    }

    val playerColors = remember(coverBitmap) {
        CoverArtPaletteExtractor.extract(coverBitmap)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 模糊背景层（原图 + blur renderEffect）
        coverBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val effect = blurRenderEffect()
                        if (effect != null) {
                            @Suppress("DEPRECATION")
                            renderEffect = effect
                        }
                    },
                contentScale = ContentScale.Crop
            )
        }

        // 暗色遮罩
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
        )

        // 内容区
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部关闭按钮 + 队列按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(onClick = onOpenQueue) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "播放队列",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // 封面轮播
            if (playlist.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    pageSize = PageSize.Fixed(280.dp),
                    contentPadding = PaddingValues(horizontal = 32.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(vertical = 16.dp)
                ) { page ->
                    val item = playlist[page]
                    val bitmap = resolveCoverBitmap(item.coverArt, item.cover)
                    val pageOffset = (
                        (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    ).coerceIn(-1f, 1f)
                    val scale by animateFloatAsState(
                        targetValue = if (page == pagerState.settledPage) 1f else 0.85f,
                        label = "coverScale"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                alpha = 1f - kotlin.math.abs(pageOffset) * 0.3f
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(280.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "封面",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = Color.Gray
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // 歌曲信息区
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 标题（跑马灯）
                Text(
                    text = currentItem?.displayTitle ?: "未知歌曲",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(iterations = Int.MAX_VALUE)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 艺术家 / 专辑
                Text(
                    text = currentItem?.let {
                        listOf(it.artist, it.album)
                            .filter { s -> s.isNotEmpty() }
                            .joinToString(" — ")
                            .ifEmpty { "未知艺术家" }
                    } ?: "未知艺术家",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 歌曲信息（格式/码率/采样率）
                currentItem?.let { item ->
                    val infoParts = buildList {
                        if (item.format.isNotEmpty()) add(item.format)
                        if (item.bitrate > 0) add("${item.bitrate}k")
                        if (item.sampleRate > 0) add("${item.sampleRate}Hz")
                    }
                    if (infoParts.isNotEmpty()) {
                        Text(
                            text = infoParts.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 进度条
            PlayerProgressBar(
                currentPosition = playerState.currentPosition,
                duration = playerState.duration,
                onSeekTo = onSeekTo,
                trackColor = Color.White.copy(alpha = 0.3f),
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                timeColor = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 播放控制区
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 随机
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = if (playerState.shuffleMode) Icons.Default.ShuffleOn else Icons.Default.Shuffle,
                        contentDescription = "随机播放",
                        tint = if (playerState.shuffleMode) playerColors.accentColor else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 上一首
                IconButton(
                    onClick = onPlayPrevious,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "上一首",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // 播放/暂停 FAB
                FloatingActionButton(
                    onClick = onTogglePlayPause,
                    shape = CircleShape,
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(36.dp)
                    )
                }

                // 下一首
                IconButton(
                    onClick = onPlayNext,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "下一首",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // 循环
                IconButton(onClick = onCycleRepeat) {
                    val repeatIcon = when (playerState.repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    }
                    val isActive = playerState.repeatMode != Player.REPEAT_MODE_OFF
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = "循环模式",
                        tint = if (isActive) playerColors.accentColor else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
