package com.yuwei.yunduanban

/**
 * 自动化数据管理器，用于存储和管理车牌识别结果
 */
object AutomationDataManager {
    private val results = mutableListOf<String>()
    
    fun addResult(licensePlate: String) {
        synchronized(results) {
            results.add(licensePlate)
        }
    }
    
    fun getResults(): List<String> {
        synchronized(results) {
            return results.toList()
        }
    }
    
    fun clearResults() {
        synchronized(results) {
            results.clear()
        }
    }
}
