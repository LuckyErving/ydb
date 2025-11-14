package com.yuwei.yunduanban

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR截屏和文字识别管理器
 */
class OCRManager(private val context: Context) {
    
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val textRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    
    private val displayMetrics = DisplayMetrics()
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    
    companion object {
        private const val TAG = "OCRManager"
        private const val VIRTUAL_DISPLAY_NAME = "YunDuanBan-ScreenCapture"
    }
    
    init {
        windowManager.defaultDisplay.getMetrics(displayMetrics)
    }
    
    /**
     * 初始化MediaProjection（需要在获取权限后调用）
     */
    fun initMediaProjection(projection: MediaProjection) {
        this.mediaProjection = projection
        setupImageReader()
    }
    
    private fun setupImageReader() {
        imageReader = ImageReader.newInstance(
            displayMetrics.widthPixels,
            displayMetrics.heightPixels,
            PixelFormat.RGBA_8888,
            2
        )
    }
    
    /**
     * 执行OCR识别
     * @param x 区域左上角X坐标
     * @param y 区域左上角Y坐标
     * @param width 区域宽度
     * @param height 区域高度
     * @return 识别到的文字，失败返回null
     */
    suspend fun performOCR(x: Int, y: Int, width: Int, height: Int): String? {
        return try {
            // 1. 截屏
            val screenshot = captureScreen() ?: run {
                Log.e(TAG, "截屏失败")
                return null
            }
            
            // 2. 裁剪区域
            val croppedBitmap = cropBitmap(screenshot, x, y, width, height)
            screenshot.recycle()
            
            // 3. OCR识别
            val text = recognizeText(croppedBitmap)
            croppedBitmap.recycle()
            
            Log.d(TAG, "OCR识别结果: ($x, $y, $width, $height) -> $text")
            text
        } catch (e: Exception) {
            Log.e(TAG, "OCR识别失败", e)
            null
        }
    }
    
    /**
     * 截取屏幕
     */
    private suspend fun captureScreen(): Bitmap? = suspendCancellableCoroutine { continuation ->
        val projection = mediaProjection
        val reader = imageReader
        
        if (projection == null || reader == null) {
            Log.e(TAG, "MediaProjection或ImageReader未初始化")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        
        try {
            // 创建虚拟显示
            virtualDisplay = projection.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                displayMetrics.widthPixels,
                displayMetrics.heightPixels,
                displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                Handler(Looper.getMainLooper())
            )
            
            // 延迟一下让画面稳定
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        val bitmap = imageToBitmap(image)
                        image.close()
                        
                        virtualDisplay?.release()
                        virtualDisplay = null
                        
                        continuation.resume(bitmap)
                    } else {
                        Log.e(TAG, "获取图像失败")
                        virtualDisplay?.release()
                        virtualDisplay = null
                        continuation.resume(null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理图像失败", e)
                    virtualDisplay?.release()
                    virtualDisplay = null
                    continuation.resumeWithException(e)
                }
            }, 100)
            
        } catch (e: Exception) {
            Log.e(TAG, "创建虚拟显示失败", e)
            continuation.resumeWithException(e)
        }
    }
    
    /**
     * Image转Bitmap
     */
    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width
        
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        
        return if (rowPadding == 0) {
            bitmap
        } else {
            // 裁剪掉padding
            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            bitmap.recycle()
            croppedBitmap
        }
    }
    
    /**
     * 裁剪Bitmap
     */
    private fun cropBitmap(source: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
        // 确保坐标在有效范围内
        val safeX = x.coerceIn(0, source.width - 1)
        val safeY = y.coerceIn(0, source.height - 1)
        val safeWidth = width.coerceAtMost(source.width - safeX)
        val safeHeight = height.coerceAtMost(source.height - safeY)
        
        return Bitmap.createBitmap(source, safeX, safeY, safeWidth, safeHeight)
    }
    
    /**
     * 使用ML Kit识别文字
     */
    private suspend fun recognizeText(bitmap: Bitmap): String? = suspendCancellableCoroutine { continuation ->
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        
        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val text = visionText.text.trim()
                continuation.resume(if (text.isNotEmpty()) text else null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "文字识别失败", e)
                continuation.resume(null)
            }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        virtualDisplay?.release()
        virtualDisplay = null
        
        imageReader?.close()
        imageReader = null
        
        mediaProjection?.stop()
        mediaProjection = null
        
        textRecognizer.close()
    }
}
