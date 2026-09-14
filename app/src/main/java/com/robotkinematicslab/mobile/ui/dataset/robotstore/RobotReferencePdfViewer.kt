package com.robotkinematicslab.mobile.ui.dataset.robotstore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.robotkinematicslab.mobile.storage.AtomicFilePublisher
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.robotkinematicslab.mobile.logging.AppLog
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.coroutines.coroutineContext

private const val PDF_RENDER_TAG = "RobotReferencePdf"
private const val PDF_RENDER_WIDTH_PX = 1_200
private const val PDF_RENDER_MAX_EDGE_PX = 4_096
private const val PDF_RENDER_MAX_PIXELS = 2_500_000L
private const val PDF_MAX_FILE_NAME_LENGTH = 128
private const val PDF_MAX_DOWNLOAD_BYTES = 40L * 1024L * 1024L
private const val PDF_NETWORK_TIMEOUT_MS = 15_000
private const val PDF_MAX_REDIRECTS = 5
private const val PDF_PROGRESS_INTERVAL_BYTES = 256L * 1024L
private val PDF_FILE_NAME_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.pdf")
private val PDF_SHA256_PATTERN = Regex("[A-Fa-f0-9]{64}")
private val PDF_CACHE_LOCK = Any()
private val PACKAGED_PDF_FINGERPRINTS = ConcurrentHashMap<Int, PdfFingerprint>()
private val VERIFIED_PDF_CACHE = ConcurrentHashMap<String, VerifiedPdfCacheStamp>()

internal data class PdfRenderDimensions(
    val width: Int,
    val height: Int
)

private data class PdfFingerprint(
    val byteCount: Long,
    val sha256: ByteArray
)

private data class VerifiedPdfCacheStamp(
    val byteCount: Long,
    val lastModified: Long,
    val sha256: ByteArray
)

private sealed interface PdfPageState {
    data object AwaitingOfficialDownload : PdfPageState

    data object Loading : PdfPageState

    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long?
    ) : PdfPageState

    data class Ready(
        val ownership: PdfBitmapOwnership,
        val pageCount: Int
    ) : PdfPageState

    data class Failed(
        val userMessage: String,
        val retryAllowed: Boolean = true
    ) : PdfPageState
}

/** In-app viewer backed by a verified durable private copy; no storage permission or external PDF app required. */
@Composable
internal fun RobotReferencePdfDialog(
    document: RobotReferenceDocument,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val importScope = rememberCoroutineScope()
    var importMessage by remember(document.localFileName) { mutableStateOf<String?>(null) }
    var importing by remember(document.localFileName) { mutableStateOf(false) }
    var pageIndex by rememberSaveable(document.localFileName) { mutableIntStateOf(0) }
    var pageInput by rememberSaveable(document.localFileName) { mutableStateOf("1") }
    var renderAttempt by rememberSaveable(document.localFileName) { mutableIntStateOf(0) }
    var remoteDownloadRequested by
        rememberSaveable(document.localFileName) { mutableStateOf(false) }
    val importPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importScope.launch {
            importing = true
            importMessage = null
            try {
                withContext(Dispatchers.IO) {
                    val importContext = coroutineContext
                    importContext.ensureActive()
                    requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                        importVerifiedOfficialPdf(context, document, input) { importContext.ensureActive() }
                    }
                }
                remoteDownloadRequested = false
                renderAttempt += 1
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                importMessage = "Import rejected: select the exact manufacturer revision. Its size, SHA-256 and page count must match. Existing documents are preserved."
            } finally {
                importing = false
            }
        }
    }

    var zoom by rememberSaveable(document.localFileName, pageIndex) { mutableFloatStateOf(1f) }
    var panX by rememberSaveable(document.localFileName, pageIndex) { mutableFloatStateOf(0f) }
    var panY by rememberSaveable(document.localFileName, pageIndex) { mutableFloatStateOf(0f) }
    var viewportSize by remember(document.localFileName) { mutableStateOf(IntSize.Zero) }
    val updateZoom: (Float) -> Unit = { requestedZoom ->
        val updatedZoom = requestedZoom.coerceIn(1f, 4f)
        zoom = updatedZoom
        if (updatedZoom == 1f) {
            panX = 0f
            panY = 0f
        } else {
            val maxPanX = viewportSize.width * (updatedZoom - 1f) / 2f
            val maxPanY = viewportSize.height * (updatedZoom - 1f) / 2f
            panX = panX.coerceIn(-maxPanX, maxPanX)
            panY = panY.coerceIn(-maxPanY, maxPanY)
        }
    }
    val transformState =
        rememberTransformableState { zoomChange, panChange, _ ->
            val updatedZoom = (zoom * zoomChange).coerceIn(1f, 4f)
            updateZoom(updatedZoom)
            if (updatedZoom == 1f) {
                panX = 0f
                panY = 0f
            } else {
                val maxPanX = viewportSize.width * (updatedZoom - 1f) / 2f
                val maxPanY = viewportSize.height * (updatedZoom - 1f) / 2f
                panX = (panX + panChange.x).coerceIn(-maxPanX, maxPanX)
                panY = (panY + panChange.y).coerceIn(-maxPanY, maxPanY)
            }
        }
    val renderState by
        produceState<PdfPageState>(
            // A missing private copy needs no I/O before consent can be shown. Publishing
            // this truthful initial state also avoids leaving the dialog on a false loading
            // screen when the shared I/O pool is busy with other scientific work.
            initialValue = initialPdfPageState(context, document),
            key1 = context,
            key2 = document.localFileName,
            key3 = Triple(pageIndex, renderAttempt, remoteDownloadRequested)
        ) {
            var unclaimedPage: PdfPageState.Ready? = null
            try {
                // Replace the previous page immediately so rapid taps cannot queue several large renders.
                value = PdfPageState.Loading
                val pdfFile =
                    withContext(Dispatchers.IO) {
                        resolvePdfFile(
                            context = context,
                            document = document,
                            allowOfficialDownload = remoteDownloadRequested,
                            onDownloadProgress = { downloaded, total ->
                                withContext(Dispatchers.Main.immediate) {
                                    value = PdfPageState.Downloading(downloaded, total)
                                }
                            }
                        )
                    }
                if (pdfFile == null) {
                    value =
                        if (document.officialDownloadUrl != null) {
                            PdfPageState.AwaitingOfficialDownload
                        } else {
                            PdfPageState.Failed(
                                userMessage =
                                    "No verified local PDF or official download address is configured for this robot.",
                                retryAllowed = false
                            )
                        }
                    return@produceState
                }
                value = PdfPageState.Loading
                value =
                    withContext(Dispatchers.IO) {
                        val renderContext = coroutineContext
                        renderPdfPage(
                            pdfFile = pdfFile,
                            document = document,
                            requestedPage = pageIndex,
                            checkCancellation = { renderContext.ensureActive() }
                        ).also { unclaimedPage = it }
                    }
                // Retain ownership until a display lease is remembered or this producer is cancelled.
                awaitDispose { }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                AppLog.e(PDF_RENDER_TAG) {
                    "PDF acquisition/render failed safely | file=${document.localFileName}, page=$pageIndex, error=${error.message}"
                }
                value =
                    PdfPageState.Failed(
                        "This document could not be verified or rendered. Tap Retry; the robot data remains unchanged."
                    )
            } finally {
                unclaimedPage?.ownership?.releaseIfUnclaimed()
            }
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.96f)
                    .padding(horizontal = 10.dp, vertical = 14.dp)
                    .testTag("RobotReferencePdfViewer")
                    .tutorialAnchor(TutorialTargets.RobotLibraryPdf),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = document.title,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${document.sourceLabel} · ${document.revision}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Relevant pages: ${document.relevantPages}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text =
                                if (document.resourceId == null) {
                                    "Integrity: manufacturer revision verified before display · saved for offline use"
                                } else {
                                    "Integrity: bundled copy verified before display"
                                },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("RobotReferencePdfClose")
                    ) {
                        Text("Close")
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .onSizeChanged { viewportSize = it },
                    contentAlignment = Alignment.Center
                ) {
                    when (val state = renderState) {
                        PdfPageState.AwaitingOfficialDownload -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text =
                                        "This manual is not included in the APK: ${document.redistributionStatus.explanation} " +
                                            "Download it once from the pinned manufacturer address, or import your local copy of this exact revision, to keep it available offline.",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Required space: ${formatPdfBytes(document.expectedByteCount ?: 0L)}. Stored privately until app data is cleared or the app is uninstalled. No storage permission is required.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Button(
                                    onClick = { remoteDownloadRequested = true },
                                    enabled = !importing,
                                    modifier = Modifier.fillMaxWidth().testTag("RobotReferencePdfDownload")
                                ) {
                                    Text("Download official PDF")
                                }
                                OutlinedButton(
                                    onClick = { importPdf.launch(arrayOf("application/pdf")) },
                                    enabled = !importing,
                                    modifier = Modifier.fillMaxWidth().testTag("RobotReferencePdfImport")
                                ) { Text("Import local manufacturer PDF") }
                                if (importing) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Text("Verifying and saving local PDF…")
                                }
                                importMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            }
                        }

                        PdfPageState.Loading -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text("Rendering local PDF page…")
                            }
                        }

                        is PdfPageState.Downloading -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val total = state.totalBytes
                                if (total != null && total > 0L) {
                                    LinearProgressIndicator(
                                        progress = {
                                            (state.downloadedBytes.toDouble() / total.toDouble())
                                                .toFloat()
                                                .coerceIn(0f, 1f)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                Text(
                                    text =
                                        if (total != null) {
                                            "Downloading ${formatPdfBytes(state.downloadedBytes)} of ${formatPdfBytes(total)}"
                                        } else {
                                            "Downloading ${formatPdfBytes(state.downloadedBytes)}"
                                        }
                                )
                                OutlinedButton(
                                    onClick = {
                                        remoteDownloadRequested = false
                                        renderAttempt += 1
                                    },
                                    modifier = Modifier.testTag("RobotReferencePdfCancelDownload")
                                ) {
                                    Text("Cancel download")
                                }
                            }
                        }

                        is PdfPageState.Failed -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = state.userMessage,
                                    color = MaterialTheme.colorScheme.error
                                )
                                if (state.retryAllowed) {
                                    Button(
                                        onClick = {
                                            if (document.resourceId == null) {
                                                remoteDownloadRequested = true
                                            }
                                            renderAttempt += 1
                                        },
                                        modifier = Modifier.testTag("RobotReferencePdfRetry")
                                    ) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }

                        is PdfPageState.Ready -> {
                            val display = remember(state) { PdfDisplayLease(state.ownership) }
                            val bitmap = display.bitmap
                            if (bitmap != null) {
                                val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                                Image(
                                    bitmap = imageBitmap,
                                    contentDescription =
                                        "${document.title}, page ${pageIndex + 1} of ${state.pageCount}",
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .graphicsLayer(
                                                scaleX = zoom,
                                                scaleY = zoom,
                                                translationX = panX,
                                                translationY = panY
                                            )
                                            .transformable(transformState)
                                            .testTag("RobotReferencePdfPage"),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }

                val readyState = renderState as? PdfPageState.Ready
                LaunchedEffect(pageIndex) {
                    pageInput = (pageIndex + 1).toString()
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                        enabled = readyState != null && pageIndex > 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Previous")
                    }
                    Text(
                        text =
                            readyState?.let { "${pageIndex + 1} / ${it.pageCount}" }
                                ?: "— / —",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Button(
                        onClick = {
                            val lastPage = (readyState?.pageCount ?: 1) - 1
                            pageIndex = (pageIndex + 1).coerceAtMost(lastPage)
                        },
                        enabled = readyState != null && pageIndex < readyState.pageCount - 1,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Next")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = pageInput,
                        onValueChange = { candidate ->
                            if (candidate.length <= 6 && candidate.all(Char::isDigit)) {
                                pageInput = candidate
                            }
                        },
                        enabled = readyState != null,
                        singleLine = true,
                        label = { Text("Page") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier =
                            Modifier
                                .width(100.dp)
                                .testTag("RobotReferencePdfPageInput")
                    )
                    Text(
                        text = readyState?.let { "of ${it.pageCount}" } ?: "of —",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = {
                            val pageCount = readyState?.pageCount ?: return@OutlinedButton
                            val requested = pageInput.toIntOrNull() ?: 1
                            pageIndex = requested.coerceIn(1, pageCount) - 1
                        },
                        enabled = readyState != null,
                        modifier = Modifier.weight(1f).testTag("RobotReferencePdfPageGo")
                    ) {
                        Text("Go to page")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { updateZoom(zoom - 0.5f) },
                        enabled = zoom > 1f,
                        modifier = Modifier.weight(1f).testTag("RobotReferencePdfZoomOut")
                    ) {
                        Text("Zoom out")
                    }
                    Text("${(zoom * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(
                        onClick = { updateZoom(zoom + 0.5f) },
                        enabled = zoom < 4f,
                        modifier = Modifier.weight(1f).testTag("RobotReferencePdfZoomIn")
                    ) {
                        Text("Zoom in")
                    }
                    TextButton(
                        onClick = { updateZoom(1f) },
                        enabled = zoom != 1f || panX != 0f || panY != 0f,
                        modifier = Modifier.testTag("RobotReferencePdfZoomReset")
                    ) {
                        Text("Reset")
                    }
                }
                Text(
                    text = "Pinch or use the controls to zoom; drag the page while zoomed.",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun initialPdfPageState(
    context: Context,
    document: RobotReferenceDocument
): PdfPageState {
    if (document.resourceId != null || document.officialDownloadUrl == null) {
        return PdfPageState.Loading
    }
    val durableCopy = File(File(context.filesDir, "robot-reference-pdfs"), document.localFileName)
    val legacyCopy = File(File(context.cacheDir, "robot-reference-pdfs"), document.localFileName)
    return if (durableCopy.isFile || legacyCopy.isFile) {
        PdfPageState.Loading
    } else {
        PdfPageState.AwaitingOfficialDownload
    }
}

private suspend fun resolvePdfFile(
    context: Context,
    document: RobotReferenceDocument,
    allowOfficialDownload: Boolean,
    onDownloadProgress: suspend (downloadedBytes: Long, totalBytes: Long?) -> Unit
): File? {
    if (document.resourceId != null) {
        return copyRawPdfToCache(context, document)
    }
    val acquisitionContext = coroutineContext
    verifiedOfficialCacheOrNull(context, document) { acquisitionContext.ensureActive() }?.let { return it }
    if (!allowOfficialDownload || document.officialDownloadUrl == null) {
        return null
    }
    return downloadOfficialPdfToCache(context, document, onDownloadProgress)
}

private fun renderPdfPage(
    pdfFile: File,
    document: RobotReferenceDocument,
    requestedPage: Int,
    checkCancellation: () -> Unit
): PdfPageState.Ready {
    var ownership: PdfBitmapOwnership? = null
    try {
    checkCancellation()
    ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            check(renderer.pageCount > 0) { "PDF contains no pages." }
            document.expectedPageCount?.let { expected ->
                check(renderer.pageCount == expected) {
                    "PDF page count does not match the verified document manifest."
                }
            }
            val safePage = requestedPage.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(safePage).use { page ->
                val dimensions = calculatePdfRenderDimensions(page.width, page.height)
                val transform =
                    Matrix().apply {
                        postScale(
                            dimensions.width.toFloat() / page.width.toFloat(),
                            dimensions.height.toFloat() / page.height.toFloat()
                        )
                    }
                val bitmap = renderPdfBitmap(dimensions.width, dimensions.height, checkCancellation) {
                    page.render(it, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
                val allocated = PdfBitmapOwnership(bitmap)
                ownership = allocated
                return PdfPageState.Ready(
                    ownership = allocated,
                    pageCount = renderer.pageCount
                )
            }
        }
    }
    } catch (failure: Throwable) {
        ownership?.release()
        throw failure
    }
}

internal fun calculatePdfRenderDimensions(
    pageWidth: Int,
    pageHeight: Int,
    preferredWidth: Int = PDF_RENDER_WIDTH_PX,
    maxEdge: Int = PDF_RENDER_MAX_EDGE_PX,
    maxPixels: Long = PDF_RENDER_MAX_PIXELS
): PdfRenderDimensions {
    require(pageWidth > 0 && pageHeight > 0) { "PDF page dimensions must be positive." }
    require(preferredWidth > 0 && maxEdge > 0 && maxPixels > 0L) {
        "PDF render limits must be positive."
    }

    val widthScale = preferredWidth.toDouble() / pageWidth.toDouble()
    val edgeScale =
        minOf(
            maxEdge.toDouble() / pageWidth.toDouble(),
            maxEdge.toDouble() / pageHeight.toDouble()
        )
    val pixelScale =
        sqrt(
            maxPixels.toDouble() /
                    (pageWidth.toDouble() * pageHeight.toDouble())
        )
    val scale = minOf(widthScale, edgeScale, pixelScale)
    val width = floor(pageWidth * scale).toInt().coerceAtLeast(1)
    val height = floor(pageHeight * scale).toInt().coerceAtLeast(1)
    return PdfRenderDimensions(width = width, height = height)
}

internal fun validateBundledPdfFileName(fileName: String) {
    require(fileName.length in 1..PDF_MAX_FILE_NAME_LENGTH) {
        "Bundled document file name has an invalid length."
    }
    require(PDF_FILE_NAME_PATTERN.matches(fileName)) {
        "Bundled document file name must be a plain PDF file name."
    }
}

internal fun validateOfficialPdfUrl(rawUrl: String): URL {
    require(rawUrl.length in 1..2_048 && rawUrl.none(Char::isISOControl)) {
        "Official PDF download address is malformed."
    }
    val url = URL(rawUrl)
    require(url.protocol.equals("https", ignoreCase = true)) {
        "Official PDF downloads must use HTTPS."
    }
    require(url.host.isNotBlank() && url.userInfo == null) {
        "Official PDF download address is malformed."
    }
    require(url.port == -1 || url.port == 443) {
        "Official PDF downloads must use the standard HTTPS port."
    }
    return url
}

internal fun validateRemotePdfIntegrityPolicy(document: RobotReferenceDocument) {
    require(document.resourceId == null) { "Remote PDF policy cannot be applied to a bundled resource." }
    validateOfficialPdfUrl(
        requireNotNull(document.officialDownloadUrl) {
            "No official PDF download address is configured."
        }
    )
    val expectedSha256 = requireNotNull(document.expectedSha256).trim()
    require(PDF_SHA256_PATTERN.matches(expectedSha256)) {
        "Official PDF requires a valid pinned SHA-256 digest."
    }
    val expectedByteCount = requireNotNull(document.expectedByteCount)
    require(expectedByteCount in 1L..PDF_MAX_DOWNLOAD_BYTES) {
        "Official PDF requires a pinned byte count within the safe download limit."
    }
    require((document.expectedPageCount ?: 0) > 0) {
        "Official PDF requires a positive pinned page count."
    }
}

internal fun verifiedOfficialCacheOrNull(
    context: Context,
    document: RobotReferenceDocument,
    checkCancellation: () -> Unit = {}
): File? {
    checkCancellation()
    validateBundledPdfFileName(document.localFileName)
    validateRemotePdfIntegrityPolicy(document)
    return synchronized(PDF_CACHE_LOCK) {
        checkCancellation()
        val destination = File(File(context.filesDir, "robot-reference-pdfs"), document.localFileName)
        val legacy = File(File(context.cacheDir, "robot-reference-pdfs"), document.localFileName)
        if (isVerifiedPdf(destination, document, checkCancellation)) return@synchronized destination
        if (!isVerifiedPdf(legacy, document, checkCancellation)) return@synchronized null
        // A corrupt durable entry must not hide a surviving verified legacy copy.
        legacy.inputStream().buffered().use { importVerifiedOfficialPdf(context, document, it, checkCancellation) }
    }
}

private fun isVerifiedPdf(file: File, document: RobotReferenceDocument, checkCancellation: () -> Unit): Boolean {
    checkCancellation()
    if (!file.isFile || file.length() != document.expectedByteCount || file.length() !in 1..PDF_MAX_DOWNLOAD_BYTES) return false
    return try {
        verifyExpectedFingerprint(file.inputStream().buffered().use { fingerprintPdf(it, checkCancellation) }, document)
        checkCancellation()
        verifyExpectedPageCount(file, document)
        checkCancellation()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
}

/** Copies only the exact pinned revision. Failed/cancelled imports cannot replace an existing manual. */
internal fun importVerifiedOfficialPdf(
    context: Context,
    document: RobotReferenceDocument,
    input: InputStream,
    checkCancellation: () -> Unit = {}
): File = synchronized(PDF_CACHE_LOCK) {
    checkCancellation()
    validateBundledPdfFileName(document.localFileName)
    validateRemotePdfIntegrityPolicy(document)
    val destination = File(File(context.filesDir, "robot-reference-pdfs"), document.localFileName)
    AtomicFilePublisher.write(destination) { temporary ->
        temporary.outputStream().buffered().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                checkCancellation()
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= requireNotNull(document.expectedByteCount)) { "PDF exceeds the pinned size." }
                output.write(buffer, 0, count)
            }
        }
        verifyExpectedFingerprint(temporary.inputStream().buffered().use { fingerprintPdf(it, checkCancellation) }, document)
        checkCancellation()
        verifyExpectedPageCount(temporary, document)
        checkCancellation()
        // Preserve conflicting historical bytes completely before publishing a verified replacement.
        if (destination.isFile && !isVerifiedPdf(destination, document, checkCancellation)) {
            val quarantine = File(destination.parentFile, "unverified-manual-${java.util.UUID.randomUUID()}.pdf")
            AtomicFilePublisher.write(quarantine) { preservation ->
                destination.inputStream().buffered().use { historical ->
                    preservation.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            checkCancellation()
                            val count = historical.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                }
                checkCancellation()
            }
        }
        // This is the publication boundary; cancellation before it keeps the previous destination.
        checkCancellation()
    }
    destination
}

internal suspend fun downloadOfficialPdfToCache(
    context: Context,
    document: RobotReferenceDocument,
    onProgress: suspend (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> }
): File = withContext(Dispatchers.IO) {
    validateBundledPdfFileName(document.localFileName)
    validateRemotePdfIntegrityPolicy(document)
    val downloadUrl = requireNotNull(document.officialDownloadUrl)

    val directory = File(context.filesDir, "robot-reference-pdfs")
    check(directory.exists() || directory.mkdirs()) { "Could not prepare the local PDF cache." }
    val destination = File(directory, document.localFileName)
    val temporary = File.createTempFile("official-robot-pdf-", ".partial", directory)
    var connection: HttpsURLConnection? = null
    try {
        connection = openOfficialPdfConnection(downloadUrl)
        val announcedLength = connection.contentLengthLong.takeIf { it >= 0L }
        announcedLength?.let { length ->
            check(length <= PDF_MAX_DOWNLOAD_BYTES) {
                "Official PDF exceeds the 40 MiB download limit."
            }
        }
        val contentType = connection.contentType?.substringBefore(';')?.trim()?.lowercase()
        if (contentType != "application/pdf" && contentType != "application/octet-stream") {
            AppLog.w(PDF_RENDER_TAG) {
                "Official PDF response has an unusual Content-Type; strict signature verification will decide | contentType=$contentType"
            }
        }

        var copiedBytes = 0L
        var nextProgressAt = 0L
        connection.inputStream.buffered().use { input ->
            temporary.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    copiedBytes += count
                    check(copiedBytes <= PDF_MAX_DOWNLOAD_BYTES) {
                        "Official PDF exceeds the 40 MiB download limit."
                    }
                    output.write(buffer, 0, count)
                    if (copiedBytes >= nextProgressAt) {
                        onProgress(copiedBytes, announcedLength)
                        nextProgressAt = copiedBytes + PDF_PROGRESS_INTERVAL_BYTES
                    }
                }
            }
        }
        onProgress(copiedBytes, announcedLength)

        val downloadContext = coroutineContext
        val checkCancellation = { downloadContext.ensureActive() }
        val fingerprint = temporary.inputStream().buffered().use { fingerprintPdf(it, checkCancellation) }
        verifyExpectedFingerprint(fingerprint, document)
        checkCancellation()
        verifyExpectedPageCount(temporary, document)
        temporary.inputStream().buffered().use { importVerifiedOfficialPdf(context, document, it, checkCancellation) }
    } finally {
        connection?.disconnect()
        if (temporary.exists()) {
            temporary.delete()
        }
    }
}

private fun openOfficialPdfConnection(rawUrl: String): HttpsURLConnection {
    var current = validateOfficialPdfUrl(rawUrl)
    val pinnedHost = current.host
    val visited = linkedSetOf<String>()
    repeat(PDF_MAX_REDIRECTS + 1) { redirectCount ->
        check(visited.add(current.toExternalForm())) { "Official PDF redirect loop detected." }
        val connection = current.openConnection() as HttpsURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = PDF_NETWORK_TIMEOUT_MS
        connection.readTimeout = PDF_NETWORK_TIMEOUT_MS
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/pdf, application/octet-stream;q=0.9")
        connection.setRequestProperty("User-Agent", "RobotKinematicsLab/1.0 PDF verifier")
        connection.connect()
        val responseCode = connection.responseCode
        if (responseCode in HTTP_REDIRECT_CODES) {
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            check(redirectCount < PDF_MAX_REDIRECTS && !location.isNullOrBlank()) {
                "Official PDF redirect limit exceeded or target missing."
            }
            val redirected = validateOfficialPdfUrl(URL(current, location).toExternalForm())
            check(redirected.host.equals(pinnedHost, ignoreCase = true)) {
                "Official PDF redirected outside its pinned manufacturer host."
            }
            current = redirected
        } else {
            check(responseCode == HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                "Official PDF server returned HTTP $responseCode."
            }
            return connection
        }
    }
    error("Official PDF redirect limit exceeded.")
}

private val HTTP_REDIRECT_CODES =
    setOf(
        HttpURLConnection.HTTP_MOVED_PERM,
        HttpURLConnection.HTTP_MOVED_TEMP,
        HttpURLConnection.HTTP_SEE_OTHER,
        307,
        308
    )

private fun verifyExpectedFingerprint(
    fingerprint: PdfFingerprint,
    document: RobotReferenceDocument
) {
    document.expectedByteCount?.let { expected ->
        check(fingerprint.byteCount == expected) {
            "PDF byte count does not match the verified document manifest."
        }
    }
    document.expectedSha256?.let { expectedRaw ->
        val expected = expectedRaw.trim()
        check(PDF_SHA256_PATTERN.matches(expected)) {
            "Verified PDF manifest contains an invalid SHA-256 value."
        }
        check(fingerprint.sha256.toHex().equals(expected, ignoreCase = true)) {
            "PDF SHA-256 does not match the verified document manifest."
        }
    }
}

private fun verifyExpectedPageCount(
    pdfFile: File,
    document: RobotReferenceDocument
) {
    val expected = document.expectedPageCount ?: return
    check(expected > 0) { "Verified PDF manifest contains an invalid page count." }
    ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            check(renderer.pageCount == expected) {
                "PDF page count does not match the verified document manifest."
            }
        }
    }
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it) }

private fun formatPdfBytes(bytes: Long): String =
    when {
        bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.1f KiB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

internal fun copyRawPdfToCache(
    context: Context,
    document: RobotReferenceDocument
): File {
    validateBundledPdfFileName(document.localFileName)
    return synchronized(PDF_CACHE_LOCK) {
        val resourceId = requireNotNull(document.resourceId) {
            "A bundled PDF resource is required for this cache path."
        }
        val directory = File(context.cacheDir, "robot-reference-pdfs")
        check(directory.exists() || directory.mkdirs()) { "Could not prepare the local PDF cache." }
        val destination = File(directory, document.localFileName)
        val packagedFingerprint =
            PACKAGED_PDF_FINGERPRINTS.getOrPut(resourceId) {
                context.resources.openRawResource(resourceId).use(::fingerprintPdf)
            }
        verifyExpectedFingerprint(packagedFingerprint, document)
        val cachedFingerprint =
            if (destination.isFile) {
                // A same-size mutation can preserve filesystem timestamp granularity; verify bytes.
                runCatching { destination.inputStream().buffered().use(::fingerprintPdf) }.getOrNull()
            } else {
                null
            }

        val cacheIsCurrent =
            cachedFingerprint != null &&
                    cachedFingerprint.byteCount == packagedFingerprint.byteCount &&
                    cachedFingerprint.sha256.contentEquals(packagedFingerprint.sha256)
        if (!cacheIsCurrent) {
            val temporary = File.createTempFile("robot-reference-", ".partial", directory)
            try {
                context.resources.openRawResource(resourceId).use { input ->
                    temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                val copiedFingerprint = temporary.inputStream().buffered().use(::fingerprintPdf)
                check(copiedFingerprint.byteCount == packagedFingerprint.byteCount) {
                    "Bundled PDF cache copy has an unexpected size."
                }
                check(copiedFingerprint.sha256.contentEquals(packagedFingerprint.sha256)) {
                    "Bundled PDF cache copy failed its integrity check."
                }
                replaceFile(temporary, destination)
            } finally {
                if (temporary.exists()) {
                    temporary.delete()
                }
            }
        }
        check(destination.isFile && destination.length() == packagedFingerprint.byteCount) {
            "Bundled PDF cache file is unavailable after verification."
        }
        VERIFIED_PDF_CACHE[destination.absolutePath] =
            VerifiedPdfCacheStamp(
                byteCount = destination.length(),
                lastModified = destination.lastModified(),
                sha256 = packagedFingerprint.sha256.copyOf()
            )
        destination
    }
}

private fun fingerprintPdf(input: InputStream): PdfFingerprint = fingerprintPdf(input) { }

private fun fingerprintPdf(input: InputStream, checkCancellation: () -> Unit): PdfFingerprint {
    checkCancellation()
    val digest = MessageDigest.getInstance("SHA-256")
    val signature = ByteArray(5)
    var signatureBytes = 0
    var byteCount = 0L
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        checkCancellation()
        val count = input.read(buffer)
        if (count < 0) break
        if (signatureBytes < signature.size) {
            val copyCount = minOf(count, signature.size - signatureBytes)
            buffer.copyInto(signature, signatureBytes, 0, copyCount)
            signatureBytes += copyCount
        }
        digest.update(buffer, 0, count)
        byteCount += count
    }
    check(signatureBytes == signature.size && signature.contentEquals("%PDF-".toByteArray())) {
        "Bundled document does not have a valid PDF signature."
    }
    check(byteCount > signature.size) { "Bundled PDF is empty." }
    return PdfFingerprint(byteCount = byteCount, sha256 = digest.digest())
}

private fun replaceFile(
    source: File,
    destination: File
) {
    try {
        Files.move(
            source.toPath(),
            destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(
            source.toPath(),
            destination.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )
    }
}
