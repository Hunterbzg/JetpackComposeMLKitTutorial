package de.yanneckreiss.mlkittutorial.ui.camera

import androidx.compose.ui.geometry.Rect

data class RecognizedText(
    val text: String,
    val boundingBox: Rect?,
)