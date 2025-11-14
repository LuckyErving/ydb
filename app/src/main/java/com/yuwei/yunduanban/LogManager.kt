package com.yuwei.yunduanban

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

/**
 * 日志管理器，用于记录和管理自动化流程日志
 * 支持持久化存储，重启APP后日志不会丢失
 */
object LogManager {
    private val logs = mutableListOf<LogEntry>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private const val MAX_LOGS = 500
    private const val PREFS_NAME = "YunDuanBanLogs"
    private const val KEY_LOGS = "saved_logs"
    
    private var context: Context? = null
    private val gson = Gson()
    
    /**
     * 初始化LogManager，必须在使用前调用
     */
    fun init(appContext: Context) {
        context = appContext.applicationContext
        loadLogs()
    }
    
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
            // 自动保存到本地
            saveLogs()
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
            saveLogs()
        }
    }
    
    /**
     * 保存日志到本地存储
     */
    private fun saveLogs() {
        context?.let { ctx ->
            try {
                val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val logsJson = gson.toJson(logs)
                prefs.edit().putString(KEY_LOGS, logsJson).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 从本地存储加载日志
     */
    private fun loadLogs() {
        context?.let { ctx ->
            try {
                val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val logsJson = prefs.getString(KEY_LOGS, null)
                if (!logsJson.isNullOrEmpty()) {
                    val type = object : TypeToken<MutableList<LogEntry>>() {}.type
                    val loadedLogs = gson.fromJson<MutableList<LogEntry>>(logsJson, type)
                    synchronized(logs) {
                        logs.clear()
                        logs.addAll(loadedLogs)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
