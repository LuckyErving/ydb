package com.yuwei.yunduanban

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.huawei.hms.mlsdk.MLAnalyzerFactory
import com.huawei.hms.mlsdk.common.MLFrame
import com.huawei.hms.mlsdk.text.MLLocalTextSetting
import com.huawei.hms.mlsdk.text.MLTextAnalyzer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR截屏和文字识别管理器
 * 支持华为ML Kit和Google ML Kit双引擎
 */
class OCRManager(private val context: Context) {
    
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    
    // Google ML Kit识别器
    private val googleTextRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    
    // 华为ML Kit识别器
    private var huaweiTextAnalyzer: MLTextAnalyzer? = null
    
    // OCR引擎类型
    private var ocrEngine: OCREngine = OCREngine.UNKNOWN
    
    private val displayMetrics = DisplayMetrics()
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    
    companion object {
        private const val TAG = "OCRManager"
        private const val VIRTUAL_DISPLAY_NAME = "YunDuanBan-ScreenCapture"
    }
    
    enum class OCREngine {
        HUAWEI_ML_KIT,  // 华为ML Kit（华为设备优先）
        GOOGLE_ML_KIT,  // Google ML Kit（通用方案）
        UNKNOWN         // 未初始化
    }
    
    init {
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        initOCREngine()
    }
    
    /**
     * 初始化OCR引擎，优先使用华为ML Kit
     */
    private fun initOCREngine() {
        try {
            // 尝试初始化华为ML Kit
            val setting = MLLocalTextSetting.Factory()
                .setOCRMode(MLLocalTextSetting.OCR_DETECT_MODE)
                .setLanguage("zh")
                .create()
            huaweiTextAnalyzer = MLAnalyzerFactory.getInstance().getLocalTextAnalyzer(setting)
            ocrEngine = OCREngine.HUAWEI_ML_KIT
            Log.i(TAG, "✅ 使用华为ML Kit OCR引擎")
            LogManager.info("🚀 OCR引擎：华为ML Kit（识别更准确）")
        } catch (e: Exception) {
            // 华为ML Kit不可用，使用Google ML Kit
            ocrEngine = OCREngine.GOOGLE_ML_KIT
            Log.i(TAG, "✅ 使用Google ML Kit OCR引擎")
            LogManager.info("🚀 OCR引擎：Google ML Kit（通用方案）")
        }
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
     * 使用对应引擎识别文字
     */
    private suspend fun recognizeText(bitmap: Bitmap): String? {
        return when (ocrEngine) {
            OCREngine.HUAWEI_ML_KIT -> recognizeTextWithHuawei(bitmap)
            OCREngine.GOOGLE_ML_KIT -> recognizeTextWithGoogle(bitmap)
            OCREngine.UNKNOWN -> {
                Log.e(TAG, "OCR引擎未初始化")
                null
            }
        }
    }
    
    /**
     * 使用华为ML Kit识别文字
     */
    private suspend fun recognizeTextWithHuawei(bitmap: Bitmap): String? = suspendCancellableCoroutine { continuation ->
        try {
            val frame = MLFrame.fromBitmap(bitmap)
            val task = huaweiTextAnalyzer?.asyncAnalyseFrame(frame)
            
            task?.addOnSuccessListener { mlText ->
                val text = mlText?.stringValue?.trim() ?: ""
                Log.d(TAG, "[华为ML Kit] 识别结果: $text")
                continuation.resume(if (text.isNotEmpty()) text else null)
            }?.addOnFailureListener { e ->
                Log.e(TAG, "[华为ML Kit] 识别失败", e)
                continuation.resume(null)
            } ?: continuation.resume(null)
        } catch (e: Exception) {
            Log.e(TAG, "[华为ML Kit] 识别异常", e)
            continuation.resume(null)
        }
    }
    
    /**
     * 使用Google ML Kit识别文字
     */
    private suspend fun recognizeTextWithGoogle(bitmap: Bitmap): String? = suspendCancellableCoroutine { continuation ->
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            
            googleTextRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val text = visionText.text.trim()
                    Log.d(TAG, "[Google ML Kit] 识别结果: $text")
                    continuation.resume(if (text.isNotEmpty()) text else null)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "[Google ML Kit] 识别失败", e)
                    continuation.resume(null)
                }
        } catch (e: Exception) {
            Log.e(TAG, "[Google ML Kit] 识别异常", e)
            continuation.resume(null)
        }
    }
    
    /**
     * 获取当前使用的OCR引擎
     */
    fun getOCREngineName(): String {
        return when (ocrEngine) {
            OCREngine.HUAWEI_ML_KIT -> "华为ML Kit"
            OCREngine.GOOGLE_ML_KIT -> "Google ML Kit"
            OCREngine.UNKNOWN -> "未知"
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
        
        try {
            googleTextRecognizer.close()
        } catch (e: Exception) {
            Log.e(TAG, "关闭Google识别器失败", e)
        }
        
        try {
            huaweiTextAnalyzer?.stop()
            huaweiTextAnalyzer = null
        } catch (e: Exception) {
            Log.e(TAG, "关闭华为识别器失败", e)
        }
    }
}
