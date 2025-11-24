package com.yuwei.yunduanban

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 车牌数据模型
 */
data class LicensePlate(
    val plateNumber: String,
    var status: PlateStatus = PlateStatus.PENDING,
    var timestamp: Long = System.currentTimeMillis(),
    var failureReason: String? = null
)

enum class PlateStatus {
    PENDING,     // 待处理
    COMPLETED,   // 已完成
    FAILED       // 处理失败
}

/**
 * 车牌管理器 - 负责车牌数据的增删改查和持久化
 */
object LicensePlateManager {
    private const val PREFS_NAME = "license_plates"
    private const val KEY_PLATES = "plates"
    
    private lateinit var context: Context
    private val gson = Gson()
    private var plates: MutableList<LicensePlate> = mutableListOf()
    
    /**
     * 初始化管理器
     */
    fun init(context: Context) {
        this.context = context.applicationContext
        loadPlates()
    }
    
    /**
     * 从SharedPreferences加载车牌数据
     */
    private fun loadPlates() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PLATES, null)
        if (json != null) {
            val type = object : TypeToken<MutableList<LicensePlate>>() {}.type
            plates = gson.fromJson(json, type) ?: mutableListOf()
        }
    }
    
    /**
     * 保存车牌数据到SharedPreferences
     */
    private fun savePlates() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(plates)
        prefs.edit().putString(KEY_PLATES, json).apply()
    }
    
    /**
     * 批量导入车牌号（每行一个）
     */
    fun importPlates(text: String): Int {
        val newPlates = text.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { LicensePlate(plateNumber = it) }
        
        // 去重：只添加不存在的车牌
        var addedCount = 0
        newPlates.forEach { newPlate ->
            if (plates.none { it.plateNumber == newPlate.plateNumber }) {
                plates.add(newPlate)
                addedCount++
            }
        }
        
        savePlates()
        return addedCount
    }
    
    /**
     * 获取所有车牌
     */
    fun getAllPlates(): List<LicensePlate> {
        return plates.toList()
    }
    
    /**
     * 获取待处理的车牌
     */
    fun getPendingPlates(): List<LicensePlate> {
        return plates.filter { it.status == PlateStatus.PENDING }
    }
    
    /**
     * 获取已完成的车牌
     */
    fun getCompletedPlates(): List<LicensePlate> {
        return plates.filter { it.status == PlateStatus.COMPLETED }
    }
    
    /**
     * 获取失败的车牌
     */
    fun getFailedPlates(): List<LicensePlate> {
        return plates.filter { it.status == PlateStatus.FAILED }
    }
    
    /**
     * 获取下一个待处理的车牌号
     */
    fun getNextPendingPlate(): String? {
        return plates.firstOrNull { it.status == PlateStatus.PENDING }?.plateNumber
    }
    
    /**
     * 标记车牌为已完成
     */
    fun markAsCompleted(plateNumber: String) {
        plates.find { it.plateNumber == plateNumber }?.apply {
            status = PlateStatus.COMPLETED
            timestamp = System.currentTimeMillis()
        }
        savePlates()
    }
    
    /**
     * 标记车牌为失败
     */
    fun markAsFailed(plateNumber: String, reason: String = "未知错误") {
        plates.find { it.plateNumber == plateNumber }?.apply {
            status = PlateStatus.FAILED
            failureReason = reason
            timestamp = System.currentTimeMillis()
        }
        savePlates()
    }
    
    /**
     * 重置车牌状态为待处理
     */
    fun resetPlate(plateNumber: String) {
        plates.find { it.plateNumber == plateNumber }?.apply {
            status = PlateStatus.PENDING
            failureReason = null
            timestamp = System.currentTimeMillis()
        }
        savePlates()
    }
    
    /**
     * 删除车牌
     */
    fun deletePlate(plateNumber: String) {
        plates.removeAll { it.plateNumber == plateNumber }
        savePlates()
    }
    
    /**
     * 清空所有车牌
     */
    fun clearAll() {
        plates.clear()
        savePlates()
    }
    
    /**
     * 清空已完成的车牌
     */
    fun clearCompleted() {
        plates.removeAll { it.status == PlateStatus.COMPLETED }
        savePlates()
    }
    
    /**
     * 获取统计信息
     */
    fun getStatistics(): Map<String, Int> {
        return mapOf(
            "total" to plates.size,
            "pending" to plates.count { it.status == PlateStatus.PENDING },
            "completed" to plates.count { it.status == PlateStatus.COMPLETED },
            "failed" to plates.count { it.status == PlateStatus.FAILED }
        )
    }
}
