package de.yanneckreiss.mlkittutorial.ui.camera

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.camera.core.AspectRatio
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

@Composable
fun CameraScreen() {
    CameraContent()
}

@Composable
private fun CameraContent() {

    val context: Context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val cameraController: LifecycleCameraController = remember { LifecycleCameraController(context) }
    var detectedText: String by remember { mutableStateOf("No text detected yet..") }
    val recognizedTextList: SnapshotStateList<RecognizedText> =
        remember { mutableStateListOf() }


    fun onTextUpdated(updatedText: String) {
        detectedText = updatedText
        cameraController.unbind()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Text Scanner") }) },
    ) { paddingValues: PaddingValues ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.BottomCenter
        ) {
            Box {
                val textMeasurer = rememberTextMeasurer()
                Box {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                        ,
                        factory = { context ->
                            PreviewView(context).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setBackgroundColor(Color.Black.toArgb())
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                scaleType = PreviewView.ScaleType.FILL_START
                            }.also { previewView ->
                                startTextRecognition(
                                    context = context,
                                    cameraController = cameraController,
                                    lifecycleOwner = lifecycleOwner,
                                    previewView = previewView,
                                    onDetectedTextUpdated = ::onTextUpdated,
                                    recognizedTextList
                                )
                            }
                        }
                    )
                }
                Canvas(modifier = Modifier.matchParentSize()
                    .drawWithContent {
                    drawContent()
                    recognizedTextList.forEach {
                        val composeRect = it.boundingBox
                        drawRect(
                            color = Color.White,
                            topLeft = composeRect?.topLeft!!,
                            size = Size(
                                composeRect.width,
                                composeRect.height
                            ),
                            style = Stroke(width = 4f)
                        )

                        val measureResult = textMeasurer.measure(
                            it.text,
                            TextStyle(Color.Black, fontSize = 28.sp),
                            constraints = Constraints.fixedWidth(composeRect.width.toInt())
                        )

                        drawRect(
                            color = Color.White,
                            topLeft = Offset(
                                composeRect.left,
                                composeRect.top + composeRect.height
                            ),
                            size = measureResult.size.toSize()
                        )

                        drawText(
                            textLayoutResult = measureResult,
                            topLeft = Offset(
                                composeRect.left + 4f,
                                composeRect.top + composeRect.height
                            )
                        )
                    }
                }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { tapOffset ->
                                recognizedTextList.forEach { recognizedText ->
                                    if (recognizedText.boundingBox?.contains(tapOffset) == true) {
                                        detectedText = recognizedText.text
                                    }
                                }
                            }
                        )
                    }
                ) { }
            }

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp),
                text = detectedText,
            )
        }
    }


}

private fun startTextRecognition(
    context: Context,
    cameraController: LifecycleCameraController,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    onDetectedTextUpdated: (String) -> Unit,
    recognizedTextList: SnapshotStateList<RecognizedText>
) {

    cameraController.imageAnalysisTargetSize = CameraController.OutputSize(AspectRatio.RATIO_16_9)
    val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    cameraController.setImageAnalysisAnalyzer(
        ContextCompat.getMainExecutor(context),
        MlKitAnalyzer(
            listOf(textRecognizer),
            COORDINATE_SYSTEM_VIEW_REFERENCED,
            ContextCompat.getMainExecutor(context)
    ) { result ->
            result.getValue(textRecognizer)?.let { text ->
                recognizedTextList.clear()
                val recognizedText = text.textBlocks
                    .flatMap { block -> block.lines}
                    .mapNotNull { lines ->
                        if("\\bPROMESA\\b".toRegex().matches(lines.text))
                            RecognizedText(lines.text, lines.boundingBox?.toComposeRect())
                        else
                            null
                    }
                recognizedTextList.addAll(recognizedText)
            }
        }
    )
    cameraController.bindToLifecycle(lifecycleOwner)
    previewView.controller = cameraController
}
