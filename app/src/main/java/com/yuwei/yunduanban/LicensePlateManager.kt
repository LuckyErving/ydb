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
    var timestamp: Long = System.currentTimeMillis()
)

enum class PlateStatus {
    PENDING,     // 待处理
    COMPLETED,   // 已完成
    FAILED       // 处理失败
}

/**
 * 车牌批量管理器
 */
object LicensePlateManager {
    private const val PREFS_NAME = "YunDuanBanPrefs"
    private const val KEY_LICENSE_PLATES = "license_plates_json"
    
    private val plates = mutableListOf<LicensePlate>()
    private var context: Context? = null
    
    fun init(ctx: Context) {
        context = ctx
        loadPlates()
    }
    
    /**
     * 批量导入车牌号（一行一个）
     */
    fun importPlates(text: String): Int {
        val lines = text.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct() // 去重
        
        var addedCount = 0
        synchronized(plates) {
            for (line in lines) {
                // 检查是否已存在
                if (plates.none { it.plateNumber == line }) {
                    plates.add(LicensePlate(line))
                    addedCount++
                }
            }
        }
        
        savePlates()
        return addedCount
    }
    
    /**
     * 获取待处理的车牌列表
     */
    fun getPendingPlates(): List<LicensePlate> {
        synchronized(plates) {
            return plates.filter { it.status == PlateStatus.PENDING }.toList()
        }
    }
    
    /**
     * 获取已完成的车牌列表
     */
    fun getCompletedPlates(): List<LicensePlate> {
        synchronized(plates) {
            return plates.filter { it.status == PlateStatus.COMPLETED }.toList()
        }
    }
    
    /**
     * 获取所有车牌
     */
    fun getAllPlates(): List<LicensePlate> {
        synchronized(plates) {
            return plates.toList()
        }
    }
    
    /**
     * 获取下一个待处理的车牌号
     */
    fun getNextPendingPlate(): String? {
        synchronized(plates) {
            return plates.firstOrNull { it.status == PlateStatus.PENDING }?.plateNumber
        }
    }
    
    /**
     * 标记车牌为已完成
     */
    fun markAsCompleted(plateNumber: String) {
        synchronized(plates) {
            plates.firstOrNull { it.plateNumber == plateNumber }?.apply {
                status = PlateStatus.COMPLETED
                timestamp = System.currentTimeMillis()
            }
        }
        savePlates()
    }
    
    /**
     * 标记车牌为失败
     */
    fun markAsFailed(plateNumber: String) {
        synchronized(plates) {
            plates.firstOrNull { it.plateNumber == plateNumber }?.apply {
                status = PlateStatus.FAILED
                timestamp = System.currentTimeMillis()
            }
        }
        savePlates()
    }
    
    /**
     * 删除车牌
     */
    fun removePlate(plateNumber: String) {
        synchronized(plates) {
            plates.removeAll { it.plateNumber == plateNumber }
        }
        savePlates()
    }
    
    /**
     * 清空所有车牌
     */
    fun clearAll() {
        synchronized(plates) {
            plates.clear()
        }
        savePlates()
    }
    
    /**
     * 清空已完成的车牌
     */
    fun clearCompleted() {
        synchronized(plates) {
            plates.removeAll { it.status == PlateStatus.COMPLETED }
        }
        savePlates()
    }
    
    /**
     * 获取统计信息
     */
    fun getStatistics(): Map<String, Int> {
        synchronized(plates) {
            return mapOf(
                "total" to plates.size,
                "pending" to plates.count { it.status == PlateStatus.PENDING },
                "completed" to plates.count { it.status == PlateStatus.COMPLETED },
                "failed" to plates.count { it.status == PlateStatus.FAILED }
            )
        }
    }
    
    /**
     * 保存到SharedPreferences
     */
    private fun savePlates() {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            synchronized(plates) {
                val json = Gson().toJson(plates)
                prefs.edit().putString(KEY_LICENSE_PLATES, json).apply()
            }
        }
    }
    
    /**
     * 从SharedPreferences加载
     */
    private fun loadPlates() {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_LICENSE_PLATES, null)
            if (!json.isNullOrEmpty()) {
                try {
                    val type = object : TypeToken<MutableList<LicensePlate>>() {}.type
                    val loadedPlates: MutableList<LicensePlate> = Gson().fromJson(json, type)
                    synchronized(plates) {
                        plates.clear()
                        plates.addAll(loadedPlates)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
