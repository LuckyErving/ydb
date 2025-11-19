package com.yuwei.yunduanban

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

class YunDuanBanAccessibilityService : AccessibilityService() {
    
    private var isRunning = false
    private var shouldStop = false
    private var selectedPolice = ""
    private val automationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var ocrManager: OCRManager? = null
    
    // 屏幕尺寸信息
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0f
    
    companion object {
        const val TAG = "YunDuanBanService"
        const val ACTION_START_AUTOMATION = "com.yuwei.yunduanban.START_AUTOMATION"
        const val EXTRA_POLICE_NAME = "police_name"
        const val ACTION_TASK_STATUS_CHANGED = "com.yuwei.yunduanban.TASK_STATUS_CHANGED"
        const val EXTRA_IS_RUNNING = "is_running"
        
        var instance: YunDuanBanAccessibilityService? = null
            private set
    }
    
    override fun onKeyEvent(event: KeyEvent): Boolean {
        // 监听音量上键来停止任务
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP && event.action == KeyEvent.ACTION_DOWN) {
            if (isRunning) {
                Log.d(TAG, "用户按下音量上键，停止任务")
                LogManager.warning("用户按音量上键终止任务")
                shouldStop = true
                stopAutomation()
                return true // 拦截按键，不触发音量调节
            }
        }
        return super.onKeyEvent(event)
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        ocrManager = OCRManager(applicationContext)
        
        // 初始化屏幕尺寸
        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        screenDensity = displayMetrics.density
        
        // 初始化坐标缩放器
        CoordinateScaler.init(screenWidth, screenHeight)
        
        Log.d(TAG, "云端办无障碍服务已创建")
        LogManager.info("📱 屏幕: ${screenWidth}x${screenHeight}, 密度: $screenDensity")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        automationScope.cancel()
        ocrManager?.release()
        ocrManager = null
        Log.d(TAG, "云端办无障碍服务已销毁")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 监听界面变化，用于自动化流程
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
        isRunning = false
    }
    
    fun isTaskRunning(): Boolean = isRunning
    
    fun startAutomationTask(policeName: String) {
        if (isRunning) {
            Log.w(TAG, "任务已在运行中")
            return
        }
        
        selectedPolice = policeName
        isRunning = true
        shouldStop = false
        
        // 确保前台服务已启动（可能已经在startForegroundServiceOnly中启动）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForegroundService()
            } catch (e: Exception) {
                Log.w(TAG, "前台服务可能已启动", e)
            }
        }
        
        notifyTaskStatusChanged(true)
        
        automationScope.launch {
            try {
                Log.d(TAG, "开始执行自动化任务，民警: $policeName")
                LogManager.info("开始执行自动化任务，民警: $policeName")
                runAutomation()
            } catch (e: Exception) {
                Log.e(TAG, "自动化任务执行失败", e)
                LogManager.error("自动化任务执行失败: ${e.message}")
            } finally {
                isRunning = false
                stopForegroundService()
                notifyTaskStatusChanged(false)
                Log.d(TAG, "自动化任务结束")
                if (!shouldStop) {
                    LogManager.success("自动化任务正常结束")
                }
            }
        }
    }
    
    fun stopAutomation() {
        if (isRunning) {
            Log.d(TAG, "正在停止自动化任务...")
            LogManager.warning("正在停止自动化任务...")
            shouldStop = true
            isRunning = false
            
            // 取消所有协程
            automationScope.coroutineContext.cancelChildren()
            
            // 停止前台服务
            stopForegroundService()
            
            // 通知UI更新
            notifyTaskStatusChanged(false)
            
            LogManager.warning("自动化任务已停止")
        }
    }
    
    private fun startForegroundService() {
        try {
            // Android 8.0+ 需要通知渠道
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    "yunduanban_channel",
                    "自动化任务",
                    android.app.NotificationManager.IMPORTANCE_LOW
                )
                val notificationManager = getSystemService(android.app.NotificationManager::class.java)
                notificationManager?.createNotificationChannel(channel)
            }
            
            // 创建通知（Android 8.0+ 使用渠道ID）
            val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.Notification.Builder(this, "yunduanban_channel")
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(this)
            }
            
            val notification = notificationBuilder
                .setContentTitle("云端办小助手")
                .setContentText("自动化任务运行中...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build()
            
            // Android 14+ 需要指定前台服务类型，但Android 10不需要
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(1, notification)
            }
            
            Log.d(TAG, "前台服务已启动")
        } catch (e: Exception) {
            Log.e(TAG, "启动前台服务失败", e)
            LogManager.error("启动前台服务失败: ${e.message}")
        }
    }
    
    private fun stopForegroundService() {
        try {
            // Android 5.0+ 支持 stopForeground(int)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            Log.d(TAG, "前台服务已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止前台服务失败", e)
        }
    }
    
    private fun notifyTaskStatusChanged(isRunning: Boolean) {
        val intent = Intent(ACTION_TASK_STATUS_CHANGED)
        intent.putExtra(EXTRA_IS_RUNNING, isRunning)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }
    
    private fun getWeworkPackage(): String {
        val prefs = getSharedPreferences("YunDuanBanPrefs", Context.MODE_PRIVATE)
        return prefs.getString("wework_package_name", "com.tencent.weworklocal") ?: "com.tencent.weworklocal"
    }
    
    private suspend fun runAutomation() = withContext(Dispatchers.Main) {
        // 检查OCR是否已初始化
        if (ocrManager == null) {
            LogManager.error("OCR管理器未初始化！请先授予截屏权限")
            return@withContext
        }
        
        // 请求截屏权限（需要MediaProjection API）
        delay(1000)
        
        // 1. 打开政务微信
        val weworkPackage = getWeworkPackage()
        LogManager.info("正在打开政务微信... (包名: $weworkPackage)")
        launchApp(weworkPackage)
        delay(1000)  // 增加等待时间，确保应用完全打开
        
        LogManager.info("开始主循环，最多处理150条记录")
        
        // 主循环，最多150次
        for (i in 0 until 150) {
            // 检查协程是否被取消
            if (!isActive || shouldStop) {
                LogManager.warning("任务已被用户终止")
                break
            }
            
            delay(150)
            
            // 再次检查
            if (!isActive || shouldStop) {
                LogManager.warning("任务已被用户终止")
                break
            }
            
            LogManager.info("处理第 ${i + 1} 条记录...")
            
            // 检查是否有违法车辆信息
            LogManager.info("正在识别违法车辆信息...")
            val weifacheliang = performOCR(150, 1888, 333, 116)
            LogManager.info("OCR识别结果: '$weifacheliang'")
            
            if (weifacheliang.isNullOrEmpty() || weifacheliang == "开始") {
                Log.d(TAG, "未检测到违法车辆信息（结果: $weifacheliang），结束循环")
                LogManager.warning("未检测到违法车辆信息（结果: '$weifacheliang'），结束循环")
                break
            }
            
            Log.d(TAG, "检测到违法车辆: $weifacheliang")
            LogManager.info("检测到违法车辆: $weifacheliang")
            
            // 2. 复制违法车辆号牌
            if (!isActive || shouldStop) break
            delay(150)
            performLongClick(332, 1950, 700)
            delay(800)
            performClick(540, 800)
            delay(500)
            
            // 3. 点击保存图片到相册
            // performClick(384, 1721)
            // delay(1900)
            // performLongClick(500, 1200, 800)
            // delay(100)
            // performClick(500, 1230)
            // delay(200)
            
            // 4. 返回并切换到执法处理app
            // performBack()
            // delay(800)
            openRecentApps()
            delay(800)
            
            if (i == 0) {
                performClick(80, 1200)
            } else {
                performClick(540, 1170)
            }
            delay(600)
            
            // 5. 检查是否在云端办界面
            val yunduanban = performOCR(450, 128, 165, 75)
            if (yunduanban != "云端办") {
                Log.d(TAG, "未在云端办界面，退出")
                LogManager.error("未在云端办界面，退出流程")
                break
            }
            LogManager.info("已进入云端办界面")
            
            // 6. 粘贴号牌搜索
            performClick(740, 355)
            delay(200)
            performClick(890, 348)
            delay(600)
            performLongClick(740, 355, 800)
            delay(300)
            performClick(261, 238)
            delay(300)
            performClick(995, 352) // 点击搜索
            delay(3000)
            
            // 7. 处理各种错误提示
            handleErrorDialogs()
            
            // 8. 检查是否未查询到数据
            if (findTextNode("未查询到数据") != null) {
                LogManager.warning("未查询到数据，跳过此条")
                performClick(540, 1298)
                delay(400)
                deleteWeixinMessages()
                continue
            }
            LogManager.info("查询到车辆数据，开始处理")
            
            // 9. 点击开单按钮
            // delay(700)
            val btnKd = findNodeById("btnKd")
            btnKd?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(300)
            
            // 10. 选择简易A版
            clickText("简易A版")
            delay(800)
            
            performClick(520, 430) // 闯红灯选择
            delay(800)

            performClick(530, 1320)
            delay(300)
            
            if (findTextNode("请稍后") != null || 
                findTextNode("未查询到当事人1年内的现场教育纠正记录！") != null) {
                performClick(530, 1320)
                delay(300)
            }
            
            // 11. 选择违法信息
            performClick(810, 303)
            delay(500)
            performClick(975, 1228)
            delay(300)
            
            // 12. 输入民警信息
            inputText(selectedPolice)
            delay(300)
            performClick(980, 336) // 搜索
            delay(500)
            performClick(540, 490)
            delay(500)
            
            // 13. 打印预览
            performClick(540, 2152)
            delay(500)
            
            // 14. 上传图片
            performClick(540, 1298)
            delay(500)
            performClick(194, 1521)
            delay(300)
            clickText("相册")
            delay(1500)
            performClick(260, 595)
            delay(300)
            performClick(990, 165)
            delay(400)
            performClick(540, 2152)
            delay(2000)
            
            // 15. 处理各种特殊情况
            handleSpecialCases(weifacheliang)
            
            // 16. 检查是否达到上限
            if (findTextNode("民警当日开具的简易程序已达") != null) {
                Log.d(TAG, "已达到开单上限")
                LogManager.warning("已达到开单上限，停止处理")
                break
            }
            
            // 17. 最终打印
            LogManager.info("准备打印决定书")
            performClick(540, 2158)
            val dangchangchufayulan = performOCR(324, 128, 502, 73)
            if (dangchangchufayulan != "当场处罚打印预览") {
                Log.d(TAG, "打印预览界面异常")
                LogManager.error("打印预览界面异常")
                break
            }
            LogManager.info("进入打印预览界面")
            delay(800)
            
            performClick(308, 1298) // 是
            delay(1000)
            clickText("确定")
            delay(1000)
            
            // 18. 删除微信消息并添加车牌到结果
            deleteWeixinMessages(weifacheliang)
        }
        
        Log.d(TAG, "自动化流程完成")
    }
    
    private suspend fun handleErrorDialogs() {
        // 处理"interrupted"错误
        while (findTextNode("interrupted") != null) {
            LogManager.warning("检测到interrupted错误，正在处理")
            performClick(540, 1298)
            delay(300)
            performClick(995, 352)
            delay(2500)
        }
        
        // 处理"请稍后"提示
        while (findTextNode("请稍后") != null) {
            LogManager.warning("检测到'请稍后'提示，正在处理")
            performClick(560, 1280)
            delay(300)
            performClick(995, 352)
            delay(2500)
        }

        // 处理"interrupted"错误
        while (findTextNode("interrupted") != null) {
            LogManager.warning("检测到interrupted错误，正在处理")
            performClick(540, 1298)
            delay(300)
            performClick(995, 352)
            delay(2500)
        }
            
        // 处理"请稍后"提示
        while (findTextNode("请稍后") != null) {
            LogManager.warning("检测到'请稍后'提示，正在处理")
            performClick(560, 1280)
            delay(300)
            performClick(995, 352)
            delay(2500)
        }

        // 处理"unexpected end of"错误
        while (findTextNode("unexpected end of") != null) {
            LogManager.warning("检测到unexpected end of错误，正在处理")
            performClick(530, 1360)
            delay(300)
            performClick(995, 352)
            delay(2500)
        }
    }
    
    private suspend fun handleSpecialCases(weifacheliang: String) {
        // 处理"请稍后"循环
        while (findTextNode("请稍后") != null) {
            LogManager.warning("等待系统响应...")
            performClick(550, 1300)
            delay(300)
            
            while (findTextNode("已取消业务数据校验") != null) {
                LogManager.info("取消业务数据校验")
                performClick(290, 1300) // 是
                delay(800)
                performClick(540, 2152)
                delay(2000)
            }
        }

        while (findTextNode("已取消业务数据校验") != null) {
            LogManager.info("取消业务数据校验")
            performClick(290, 1300) // 是
            delay(800)
            performClick(540, 2152)
            delay(2000)
        }

        // 处理"骑手性质"选择
        if (findTextNode("请选择骑手性质") != null) {
            LogManager.info("选择骑手性质：众包")
            performClick(540, 1298)
            delay(600)
            performClick(810, 1860)
            delay(600)
            clickText("众包")
            delay(800)
            performClick(540, 2158)
            delay(600)
        }


        // 处理"继续开单"情况
        if (findTextNode("继续开单") != null) {
            LogManager.info("检测到'继续开单'提示，按流程处理")
            performClick(773, 1545) // 否
            delay(800)
            performClick(773, 1400) // 否
            delay(600)
            performBack()
            delay(800)
            performClick(308, 1298) // 是
            delay(800)
            deleteWeixinMessages(weifacheliang)
            return
            // continue
        }
        
        // 处理"教育纠正"情况
        if (findTextNode("该违法符合教育纠正条件") != null) {
            LogManager.info("检测到'教育纠正'条件")
            if (findTextNode("民警当日开具的简易程序已达") != null) {
                LogManager.warning("已达到开单上限")
                return
            }
            performClick(773, 1478) // 否
            delay(700)
            performClick(540, 2158) // 打印
            
            val dangchangchufayulann = performOCR(324, 128, 502, 73)
            if (dangchangchufayulann != "当场处罚打印预览") {
                LogManager.error("教育纠正打印预览界面异常")
                return
            }
            delay(800)
            performClick(308, 1298) // 是
            delay(1000)
            clickText("确定")
            delay(800)
            deleteWeixinMessages(weifacheliang)
            return
            // continue
        }
        
        // 处理"首违警告"情况
        if (findTextNode("违法符合首违警告情形") != null) {
            LogManager.info("检测到'首违警告'情形，选择警告处理")
            performClick(773, 1370)
            delay(600)
            clickText("警告")
            delay(900)
            performClick(540, 2152) // 打印预览
            delay(2000)
        }

        
    }
    
    private suspend fun deleteWeixinMessages(weifacheliang: String? = null) {
        LogManager.info("清理微信消息${if (weifacheliang != null) "：$weifacheliang" else ""}")
        val weworkPackage = getWeworkPackage()
        launchApp(weworkPackage)
        delay(500)
        performLongClick(330, 1950, 800)
        delay(200)
        // performClick(540, 1371) // 多选
        // delay(200)
        // performClick(488, 1722)
        // delay(200)
        // performClick(945, 2145)
        // delay(600)
        performClick(540, 1530)
        delay(800)
        performClick(834, 1251)
        delay(300)
        
        weifacheliang?.let {
            AutomationDataManager.addResult(it)
            LogManager.success("已完成处理：$it")
        }
    }
    
    // ============= 辅助方法 =============
    
    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(it)
        }
    }
    
    private suspend fun performClick(x: Int, y: Int) {
        // 自动缩放坐标
        val (scaledX, scaledY) = CoordinateScaler.scalePoint(x, y)
        
        val path = Path().apply {
            moveTo(scaledX.toFloat(), scaledY.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        
        withContext(Dispatchers.Main) {
            dispatchGesture(gesture, null, null)
        }
    }
    
    private suspend fun performLongClick(x: Int, y: Int, duration: Long) {
        // 自动缩放坐标
        val (scaledX, scaledY) = CoordinateScaler.scalePoint(x, y)
        
        val path = Path().apply {
            moveTo(scaledX.toFloat(), scaledY.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        withContext(Dispatchers.Main) {
            dispatchGesture(gesture, null, null)
        }
    }
    
    private fun performBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }
    
    private fun openRecentApps() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }
    
    private fun findTextNode(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return findNodeByText(root, text)
    }
    
    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.contains(text) == true) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByText(child, text)
            if (result != null) return result
        }
        
        return null
    }
    
    private fun findNodeById(id: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByViewId(id)
        return nodes.firstOrNull()
    }
    
    private fun clickText(text: String) {
        val node = findTextNode(text)
        node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
    
    private fun inputText(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("input", text)
        clipboard.setPrimaryClip(clip)
        
        // 找到输入框并粘贴
        val root = rootInActiveWindow
        root?.let {
            val editText = findEditText(it)
            editText?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            editText?.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }
    }
    
    private fun findEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className == "android.widget.EditText") {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditText(child)
            if (result != null) return result
        }
        
        return null
    }
    
    private suspend fun performOCR(x: Int, y: Int, w: Int, h: Int): String? {
        return try {
            if (ocrManager == null) {
                Log.e(TAG, "OCR管理器为null")
                LogManager.error("OCR管理器未初始化")
                return null
            }
            
            // 自动缩放OCR区域坐标和尺寸
            val scaled = CoordinateScaler.scaleRect(x, y, w, h)
            val (scaledX, scaledY, scaledW, scaledH) = scaled
            
            val result = ocrManager?.performOCR(scaledX, scaledY, scaledW, scaledH)
            Log.d(TAG, "OCR识别 原始($x,$y,$w,$h) 缩放后($scaledX,$scaledY,$scaledW,$scaledH) -> '$result'")
            result
        } catch (e: Exception) {
            Log.e(TAG, "OCR识别异常 ($x,$y,$w,$h)", e)
            LogManager.error("OCR识别失败: ${e.message}")
            null
        }
    }
    
    /**
     * 只启动前台服务，不执行任务（用于在请求截屏权限前启动）
     */
    fun startForegroundServiceOnly() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForegroundService()
            Log.d(TAG, "前台服务已启动（仅服务）")
        }
    }
    
    /**
     * 设置MediaProjection用于截屏
     * 需要在MainActivity获取权限后调用
     * 注意：前台服务应该已经在请求权限前启动
     */
    fun setMediaProjection(resultCode: Int, data: Intent) {
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            ocrManager?.initMediaProjection(mediaProjection)
            Log.d(TAG, "MediaProjection初始化成功")
            LogManager.info("截屏权限已获取，OCR功能已启用")
        } catch (e: Exception) {
            Log.e(TAG, "MediaProjection初始化失败", e)
            LogManager.error("截屏权限获取失败: ${e.message}")
        }
    }
}
