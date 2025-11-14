package com.yuwei.yunduanban

import java.text.SimpleDateFormat
import java.util.*

/**
 * 日志管理器，用于记录和管理自动化流程日志
 */
object LogManager {
    private val logs = mutableListOf<LogEntry>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private const val MAX_LOGS = 500
    
    data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val message: String
    ) {
        fun getFormattedTime(): String {
            return dateFormat.format(Date(timestamp))
        }
        
        fun getFullMessage(): String {
            return "[${getFormattedTime()}] [${level.name}] $message"
        }
    }
    
    enum class LogLevel {
        INFO, WARNING, ERROR, SUCCESS
    }
    
    fun log(level: LogLevel, message: String) {
        synchronized(logs) {
            logs.add(LogEntry(System.currentTimeMillis(), level, message))
            // 保持日志数量在限制内
            if (logs.size > MAX_LOGS) {
                logs.removeAt(0)
            }
        }
    }
    
    fun info(message: String) = log(LogLevel.INFO, message)
    fun warning(message: String) = log(LogLevel.WARNING, message)
    fun error(message: String) = log(LogLevel.ERROR, message)
    fun success(message: String) = log(LogLevel.SUCCESS, message)
    
    fun getLogs(): List<LogEntry> {
        synchronized(logs) {
            return logs.toList()
        }
    }
    
    fun getLogsAsText(): String {
        return getLogs().joinToString("\n") { it.getFullMessage() }
    }
    
    fun clearLogs() {
        synchronized(logs) {
            logs.clear()
        }
    }
}
