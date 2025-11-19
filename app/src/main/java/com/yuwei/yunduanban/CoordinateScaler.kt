package com.yuwei.yunduanban

import android.util.Log

/**
 * 坐标缩放工具类
 * 用于将基准分辨率的坐标转换为当前设备分辨率的坐标
 */
object CoordinateScaler {
    private const val TAG = "CoordinateScaler"
    
    // 基准分辨率（开发时使用的设备分辨率）
    // 请根据实际开发设备修改这个值
    private const val BASE_WIDTH = 1080
    private const val BASE_HEIGHT = 2340
    
    // 当前设备分辨率
    private var currentWidth = BASE_WIDTH
    private var currentHeight = BASE_HEIGHT
    
    // 缩放比例
    private var scaleX = 1.0f
    private var scaleY = 1.0f
    
    /**
     * 初始化坐标缩放器
     */
    fun init(width: Int, height: Int) {
        currentWidth = width
        currentHeight = height
        scaleX = width.toFloat() / BASE_WIDTH
        scaleY = height.toFloat() / BASE_HEIGHT
        
        Log.i(TAG, "坐标缩放器已初始化")
        Log.i(TAG, "基准分辨率: ${BASE_WIDTH}x${BASE_HEIGHT}")
        Log.i(TAG, "当前分辨率: ${currentWidth}x${currentHeight}")
        Log.i(TAG, "缩放比例: X=${scaleX}, Y=${scaleY}")
        
        LogManager.info("📐 坐标缩放器已初始化")
        LogManager.info("   基准: ${BASE_WIDTH}x${BASE_HEIGHT}")
        LogManager.info("   当前: ${currentWidth}x${currentHeight}")
        LogManager.info("   缩放: X=${String.format("%.2f", scaleX)}, Y=${String.format("%.2f", scaleY)}")
    }
    
    /**
     * 缩放X坐标
     */
    fun scaleX(x: Int): Int {
        return (x * scaleX).toInt()
    }
    
    /**
     * 缩放Y坐标
     */
    fun scaleY(y: Int): Int {
        return (y * scaleY).toInt()
    }
    
    /**
     * 缩放宽度
     */
    fun scaleWidth(width: Int): Int {
        return (width * scaleX).toInt()
    }
    
    /**
     * 缩放高度
     */
    fun scaleHeight(height: Int): Int {
        return (height * scaleY).toInt()
    }
    
    /**
     * 同时缩放坐标和尺寸（用于OCR区域）
     * @return Quadruple(x, y, width, height)
     */
    fun scaleRect(x: Int, y: Int, width: Int, height: Int): Quadruple<Int, Int, Int, Int> {
        return Quadruple(
            scaleX(x),
            scaleY(y),
            scaleWidth(width),
            scaleHeight(height)
        )
    }
    
    /**
     * 同时缩放点坐标
     * @return Pair(x, y)
     */
    fun scalePoint(x: Int, y: Int): Pair<Int, Int> {
        return Pair(scaleX(x), scaleY(y))
    }
    
    /**
     * 获取当前缩放信息（用于调试）
     */
    fun getScaleInfo(): String {
        return "基准: ${BASE_WIDTH}x${BASE_HEIGHT} | " +
               "当前: ${currentWidth}x${currentHeight} | " +
               "缩放: X=${String.format("%.2f", scaleX)}, Y=${String.format("%.2f", scaleY)}"
    }
    
    /**
     * 四元组数据类
     */
    data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
}
