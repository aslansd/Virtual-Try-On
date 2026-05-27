package com.example.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.roundToInt

@Composable
fun TryOnCanvas(
    viewModel: TryOnViewModel,
    modifier: Modifier = Modifier
) {
    val personType by viewModel.personType.collectAsState()
    val presetModel by viewModel.selectedPresetModel.collectAsState()
    val galleryUri by viewModel.galleryPersonUri.collectAsState()

    val garmentUrl by viewModel.garmentUrl.collectAsState()
    val garmentImageUrl by viewModel.garmentImageUrl.collectAsState()

    val scale by viewModel.canvasScale.collectAsState()
    val offsetX by viewModel.canvasOffsetX.collectAsState()
    val offsetY by viewModel.canvasOffsetY.collectAsState()
    val rotation by viewModel.canvasRotation.collectAsState()
    val alpha by viewModel.canvasAlpha.collectAsState()

    val context = LocalContext.current

    val personImageModel = if (personType == "GALLERY" && galleryUri != null) {
        galleryUri
    } else {
        presetModel.imageUrl
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(380.dp)
            .testTag("try_on_canvas_container"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val canvasWidth = maxWidth
            val canvasHeight = maxHeight

            // 1. Base Layer: The Person/Model Photo
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(personImageModel)
                    .crossfade(true)
                    .build(),
                contentDescription = "Base Person photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // 2. Interactive Overlay Layer: The Clothing Item
            val currentGarmentUrl = garmentImageUrl
            if (!currentGarmentUrl.isNullOrBlank()) {
                val garmentModel = if (currentGarmentUrl.startsWith("content://") || currentGarmentUrl.startsWith("file://")) {
                    Uri.parse(currentGarmentUrl)
                } else {
                    currentGarmentUrl
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Capture Transform Gestures safely preventing NaN input crashes
                        .pointerInput(scale, offsetX, offsetY, rotation) {
                            detectTransformGestures { _, pan, zoom, rotate ->
                                if (!zoom.isNaN() && !zoom.isInfinite()) {
                                    val nextScale = (scale * zoom).coerceIn(0.2f, 4.0f)
                                    if (!nextScale.isNaN() && !nextScale.isInfinite()) {
                                        viewModel.canvasScale.value = nextScale
                                    }
                                }
                                if (!pan.x.isNaN() && !pan.x.isInfinite()) {
                                    viewModel.canvasOffsetX.value = offsetX + pan.x
                                }
                                if (!pan.y.isNaN() && !pan.y.isInfinite()) {
                                    viewModel.canvasOffsetY.value = offsetY + pan.y
                                }
                                if (!rotate.isNaN() && !rotate.isInfinite()) {
                                    viewModel.canvasRotation.value = (rotation + rotate) % 360f
                                }
                            }
                        }
                ) {
                    val safeOffsetX = if (offsetX.isNaN() || offsetX.isInfinite()) 0f else offsetX
                    val safeOffsetY = if (offsetY.isNaN() || offsetY.isInfinite()) 0f else offsetY
                    val safeScale = if (scale.isNaN() || scale.isInfinite() || scale < 0.1f) 1f else scale
                    val safeRotation = if (rotation.isNaN() || rotation.isInfinite()) 0f else rotation
                    val safeAlpha = if (alpha.isNaN() || alpha.isInfinite()) 0.85f else alpha

                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(garmentModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Overlaid Tryon Garment",
                        modifier = Modifier
                            .size(180.dp)
                            .offset {
                                IntOffset(
                                    safeOffsetX.roundToInt(),
                                    safeOffsetY.roundToInt()
                                )
                            }
                            .graphicsLayer {
                                scaleX = safeScale
                                scaleY = safeScale
                                this.rotationZ = safeRotation
                                this.alpha = safeAlpha
                            },
                        contentScale = ContentScale.Fit
                    )
                }
            } else {
                // Empty clothes prompt
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Enter a shop link or tap an item inside the closets below to try on clothes!",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // 3. Tactile Precise Align Panel (Overlay Controls)
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Zoom In
                    IconButton(
                        onClick = {
                            val currentScale = if (scale.isNaN() || scale.isInfinite()) 1f else scale
                            viewModel.canvasScale.value = (currentScale + 0.15f).coerceIn(0.2f, 4.0f)
                        },
                        modifier = Modifier.size(32.dp).testTag("precision_zoom_in")
                    ) {
                        Icon(Icons.Filled.ZoomIn, "Zoom In", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    // Zoom Out
                    IconButton(
                        onClick = {
                            val currentScale = if (scale.isNaN() || scale.isInfinite()) 1f else scale
                            viewModel.canvasScale.value = (currentScale - 0.15f).coerceIn(0.2f, 4.0f)
                        },
                        modifier = Modifier.size(32.dp).testTag("precision_zoom_out")
                    ) {
                        Icon(Icons.Filled.ZoomOut, "Zoom Out", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // Rotate Left
                    IconButton(
                        onClick = {
                            val currentRotation = if (rotation.isNaN() || rotation.isInfinite()) 0f else rotation
                            viewModel.canvasRotation.value = (currentRotation - 10f) % 360f
                        },
                        modifier = Modifier.size(32.dp).testTag("precision_rotate_left")
                    ) {
                        Icon(Icons.Filled.RotateLeft, "Rotate Left", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    // Rotate Right
                    IconButton(
                        onClick = {
                            val currentRotation = if (rotation.isNaN() || rotation.isInfinite()) 0f else rotation
                            viewModel.canvasRotation.value = (currentRotation + 10f) % 360f
                        },
                        modifier = Modifier.size(32.dp).testTag("precision_rotate_right")
                    ) {
                        Icon(Icons.Filled.RotateRight, "Rotate Right", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // Resetter
                    IconButton(
                        onClick = { viewModel.resetCanvas() },
                        modifier = Modifier.size(32.dp).testTag("precision_reset")
                    ) {
                        Icon(Icons.Filled.Cached, "Reset Position", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // 4. Alpha Opacity Slider (Bottom overlay)
            if (!garmentImageUrl.isNullOrBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                        .width(220.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Opacity, "Transparency", tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Slider(
                            value = alpha,
                            onValueChange = { viewModel.canvasAlpha.value = it },
                            valueRange = 0.1f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.LightGray.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${(alpha * 100).roundToInt()}%",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
