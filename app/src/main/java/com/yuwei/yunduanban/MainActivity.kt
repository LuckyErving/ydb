package com.yuwei.yunduanban

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yuwei.yunduanban.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private var selectedPolice: String = "赵炜彦"
    private var policeList: MutableList<String> = mutableListOf()
    
    private val taskStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == YunDuanBanAccessibilityService.ACTION_TASK_STATUS_CHANGED) {
                val isRunning = intent.getBooleanExtra(YunDuanBanAccessibilityService.EXTRA_IS_RUNNING, false)
                runOnUiThread {
                    updateUIStatus()
                }
            }
        }
    }
    
    companion object {
        private const val PREFS_NAME = "YunDuanBanPrefs"
        private const val KEY_SELECTED_POLICE = "selected_police"
        private const val KEY_SELECTED_POLICE_POSITION = "selected_police_position"
        private const val KEY_POLICE_LIST = "police_list_json"
        private const val KEY_WEWORK_PACKAGE = "wework_package_name"
        private const val REQUEST_MEDIA_PROJECTION = 1001
        private const val REQUEST_STORAGE_PERMISSION = 1002
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                // 将截屏权限传递给AccessibilityService
                YunDuanBanAccessibilityService.instance?.setMediaProjection(resultCode, data)
                // Toast.makeText(this, "截屏权限已授予，正在启动自动化任务...", Toast.LENGTH_SHORT).show()
                
                // 延迟600ms确保MediaProjection初始化完成，然后启动自动化任务
                binding.btnStart.postDelayed({
                    startAutomation()
                }, 600)
            } else {
                Toast.makeText(this, "截屏权限被拒绝，OCR功能将无法使用", Toast.LENGTH_LONG).show()
                LogManager.warning("用户拒绝了截屏权限")
                // 恢复按钮状态
                binding.btnStart.isEnabled = true
                binding.btnStop.isEnabled = false
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 初始化LogManager
        LogManager.init(applicationContext)
        
        // 注册广播接收器
        val filter = IntentFilter(YunDuanBanAccessibilityService.ACTION_TASK_STATUS_CHANGED)
        // Android 13+ 需要RECEIVER_NOT_EXPORTED，Android 10使用旧API
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(taskStatusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(taskStatusReceiver, filter)
        }
        
        setupUI()
        setupListeners()
        loadLastSelection()
    }
    
    override fun onResume() {
        super.onResume()
        // 每次回到前台时检查状态
        updateUIStatus()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 取消注册广播接收器
        try {
            unregisterReceiver(taskStatusReceiver)
        } catch (e: Exception) {
            // 忽略已取消注册的异常
        }
    }
    
    private fun updateUIStatus() {
        val isRunning = YunDuanBanAccessibilityService.instance?.isTaskRunning() == true
        
        // 检查任务运行状态
        if (isRunning) {
            binding.tvStatus.text = getString(R.string.status_running)
            binding.btnStart.isEnabled = false
            binding.btnStop.isEnabled = true
        } else if (AutomationDataManager.getResults().isNotEmpty()) {
            binding.tvStatus.text = getString(R.string.status_completed)
            binding.btnStart.isEnabled = true
            binding.btnStop.isEnabled = false
        } else {
            binding.tvStatus.text = getString(R.string.status_ready)
            binding.btnStart.isEnabled = true
            binding.btnStop.isEnabled = false
        }
        
        // 检查无障碍服务状态
        if (!isAccessibilityServiceEnabled()) {
            binding.tvStatus.text = "⚠️ 无障碍服务已关闭，请重新开启"
            binding.tvStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
            binding.btnStop.isEnabled = false
        } else if (!isRunning) {
            binding.tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
        }
    }
    
    private fun setupUI() {
        // 动态加载民警列表并设置下拉框
        loadPoliceList()
        refreshPoliceSpinner()

        binding.spinnerPolice.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in policeList.indices) {
                    selectedPolice = policeList[position]
                    saveSelection(position, selectedPolice)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 管理按钮（弹出管理对话框）
        binding.btnManagePolice.setOnClickListener {
            showManagePoliceDialog()
        }
        
        // 加载政务微信包名配置
        loadWeworkPackage()
        
        // 显示屏幕分辨率信息
        displayScreenInfo()
    }

    private fun loadPoliceList() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_POLICE_LIST, null)
        if (json.isNullOrEmpty()) {
            // 使用默认资源数组作为初始数据
            policeList = resources.getStringArray(R.array.police_names).toMutableList()
            savePoliceList()
        } else {
            try {
                val type = object : TypeToken<MutableList<String>>() {}.type
                policeList = Gson().fromJson(json, type)
            } catch (e: Exception) {
                policeList = resources.getStringArray(R.array.police_names).toMutableList()
            }
        }
    }
    
    private fun loadWeworkPackage() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val packageName = prefs.getString(KEY_WEWORK_PACKAGE, getString(R.string.default_wework_package))
        binding.etWeworkPackage.setText(packageName)
        
        // 监听文本变化，自动保存
        binding.etWeworkPackage.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                saveWeworkPackage(s.toString())
            }
        })
    }
    
    private fun saveWeworkPackage(packageName: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WEWORK_PACKAGE, packageName).apply()
    }
    
    fun getWeworkPackage(): String {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WEWORK_PACKAGE, getString(R.string.default_wework_package)) ?: getString(R.string.default_wework_package)
    }
    
    private fun displayScreenInfo() {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.density
        val dpi = displayMetrics.densityDpi
        
        val info = "屏幕分辨率：${width}x${height} | 密度：${density} | DPI：${dpi}"
        binding.tvScreenInfo.text = info
        
        LogManager.info("📱 $info")
    }

    private fun savePoliceList() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(policeList)
        prefs.edit().putString(KEY_POLICE_LIST, json).apply()
    }

    private fun refreshPoliceSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, policeList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPolice.adapter = adapter
        // 恢复上次选择的位置
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastPosition = prefs.getInt(KEY_SELECTED_POLICE_POSITION, 0)
        if (lastPosition in policeList.indices) {
            binding.spinnerPolice.setSelection(lastPosition)
            selectedPolice = policeList[lastPosition]
        } else if (policeList.isNotEmpty()) {
            binding.spinnerPolice.setSelection(0)
            selectedPolice = policeList[0]
        }
    }

    private fun showManagePoliceDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_manage_police, null)
        val listView = dialogView.findViewById<ListView>(R.id.lvPolice)
        val addBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddPolice)

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, policeList)
        listView.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setTitle("管理民警列表")
            .setView(dialogView)
            .setNegativeButton("关闭", null)
            .create()

        // 长按删除
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val name = policeList[position]
            MaterialAlertDialogBuilder(this)
                .setTitle("删除")
                .setMessage("确认删除：$name ?")
                .setPositiveButton("删除") { _, _ ->
                    policeList.removeAt(position)
                    savePoliceList()
                    adapter.notifyDataSetChanged()
                    refreshPoliceSpinner()
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }

        addBtn.setOnClickListener {
            val input = EditText(this)
            input.hint = "输入民警姓名"
            MaterialAlertDialogBuilder(this)
                .setTitle("添加民警")
                .setView(input)
                .setPositiveButton("添加") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) {
                        policeList.add(name)
                        savePoliceList()
                        adapter.notifyDataSetChanged()
                        refreshPoliceSpinner()
                    } else {
                        Toast.makeText(this, "姓名不能为空", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        dialog.show()
    }
    
    private fun loadLastSelection() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastPosition = prefs.getInt(KEY_SELECTED_POLICE_POSITION, 0)
        val lastName = prefs.getString(KEY_SELECTED_POLICE, "赵炜彦") ?: "赵炜彦"
        
        binding.spinnerPolice.setSelection(lastPosition)
        selectedPolice = lastName
    }
    
    private fun saveSelection(position: Int, name: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt(KEY_SELECTED_POLICE_POSITION, position)
            putString(KEY_SELECTED_POLICE, name)
            apply()
        }
    }
    
    private fun setupListeners() {
        // 开始运行按钮
        binding.btnStart.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                showAccessibilityPermissionDialog()
                return@setOnClickListener
            }
            
            // Android 10+ 都需要先启动前台服务（MediaProjection要求）
            YunDuanBanAccessibilityService.instance?.let { service ->
                // 先启动前台服务
                service.startForegroundServiceOnly()
                
                // 延迟800ms确保前台服务完全启动后再请求截屏权限
                // Android 10需要更长时间确保服务就绪
                binding.btnStart.postDelayed({
                    requestScreenCapturePermission()
                }, 800)
            } ?: run {
                Toast.makeText(this, "无障碍服务未启动", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 终止运行按钮
        binding.btnStop.setOnClickListener {
            stopAutomation()
        }
        
        // 导出车牌按钮
        binding.btnExport.setOnClickListener {
            exportResults()
        }
        
        // 查看日志按钮
        binding.btnViewLog.setOnClickListener {
            showLogDialog()
        }
        
        // 问题反馈按钮
        binding.tvFeedback.setOnClickListener {
            showFeedbackDialog()
        }
        
        // YDB专用图按钮
        binding.btnSaveImage.setOnClickListener {
            saveImageToGallery()
        }
    }
    
    private fun showLogDialog() {
        val logs = LogManager.getLogsAsText()
        val logText = if (logs.isEmpty()) {
            "暂无日志记录"
        } else {
            logs
        }
        
        // 创建可滚动的TextView
        val scrollView = android.widget.ScrollView(this)
        val textView = android.widget.TextView(this).apply {
            text = logText
            setPadding(40, 20, 40, 20)
            textSize = 12f
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        scrollView.addView(textView)
        
        // 创建对话框
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("自动化运行日志 (${LogManager.getLogs().size}条)")
            .setView(scrollView)
            .setPositiveButton("确定", null)
            .setNeutralButton("清空日志") { _, _ ->
                LogManager.clearLogs()
                Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("复制日志") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("运行日志", logText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
            .create()
        
        dialog.show()
        
        // 对话框显示后，滚动到底部
        scrollView.post {
            scrollView.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/${YunDuanBanAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(service) == true
    }
    
    private fun showAccessibilityPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_accessibility_title)
            .setMessage(R.string.permission_accessibility_message)
            .setPositiveButton(R.string.dialog_positive) { _, _ ->
                openAccessibilitySettings()
            }
            .setNegativeButton(R.string.dialog_negative, null)
            .show()
    }
    
    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开无障碍设置", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun requestScreenCapturePermission() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
    }
    
    private fun startAutomation() {
        binding.tvStatus.text = getString(R.string.status_starting)
        binding.btnStart.isEnabled = false
        binding.btnStop.isEnabled = true
        binding.btnExport.isEnabled = false
        
        lifecycleScope.launch {
            try {
                // 清空之前的结果（但不清空日志，让用户查看历史记录）
                AutomationDataManager.clearResults()
                
                LogManager.info("========== 开始新的自动化任务 ==========")
                LogManager.info("民警：$selectedPolice")
                
                // 通过Intent启动无障碍服务的自动化流程
                val intent = Intent(this@MainActivity, YunDuanBanAccessibilityService::class.java)
                intent.action = YunDuanBanAccessibilityService.ACTION_START_AUTOMATION
                intent.putExtra(YunDuanBanAccessibilityService.EXTRA_POLICE_NAME, selectedPolice)
                
                // 通知服务开始执行
                YunDuanBanAccessibilityService.instance?.startAutomationTask(selectedPolice)
                
                binding.tvStatus.text = getString(R.string.status_running)
                // Toast.makeText(this@MainActivity, "自动化任务已启动", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                LogManager.error("启动失败: ${e.message}")
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = "${getString(R.string.status_error)}: ${e.message}"
                    Toast.makeText(this@MainActivity, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.btnStart.isEnabled = true
                    binding.btnStop.isEnabled = false
                    binding.btnExport.isEnabled = true
                }
            }
        }
    }
    
    private fun stopAutomation() {
        YunDuanBanAccessibilityService.instance?.let { service ->
            if (service.isTaskRunning()) {
                LogManager.warning("用户点击按钮终止任务")
                service.stopAutomation()
                Toast.makeText(this, "正在终止任务...", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun exportResults() {
        val results = AutomationDataManager.getResults()
        
        if (results.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_export_data, Toast.LENGTH_SHORT).show()
            return
        }
        
        val resultText = results.joinToString("\n")
        
        // 复制到剪贴板
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("车牌结果", resultText)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show()
        
        // 显示预览对话框
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_export_title)
            .setMessage(resultText)
            .setPositiveButton(R.string.dialog_positive, null)
            .setNeutralButton(R.string.dialog_neutral) { _, _ ->
                val clip2 = ClipData.newPlainText("车牌结果", resultText)
                clipboard.setPrimaryClip(clip2)
                Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    private fun showFeedbackDialog() {
        val message = "如遇到问题或需要帮助，请联系：\n\n叶裕威（13538772957）"
        
        MaterialAlertDialogBuilder(this)
            .setTitle("问题反馈")
            .setMessage(message)
            .setPositiveButton("复制联系方式") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("联系方式", "叶裕威：13538772957")
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "联系方式已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }
    
    private fun saveImageToGallery() {
        // Android 10+ 不需要存储权限，使用 MediaStore API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageToGalleryQ()
        } else {
            // Android 9 及以下需要检查权限
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_STORAGE_PERMISSION
                )
            } else {
                saveImageToGalleryLegacy()
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                saveImageToGalleryLegacy()
            } else {
                Toast.makeText(this, "需要存储权限才能保存图片", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun saveImageToGalleryQ() {
        try {
            // 从资源加载图片
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.ydb_special_image)
            
            // 使用 MediaStore API 保存到相册
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "云端办专用图_${System.currentTimeMillis()}.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/云端办")
                }
            }
            
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            
            uri?.let {
                contentResolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                }
                Toast.makeText(this, "图片已保存到相册", Toast.LENGTH_SHORT).show()
                LogManager.info("YDB专用图已保存到相册")
            } ?: run {
                Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            LogManager.error("保存图片失败: ${e.message}")
        }
    }
    
    private fun saveImageToGalleryLegacy() {
        try {
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.ydb_special_image)
            val savedImageURL = MediaStore.Images.Media.insertImage(
                contentResolver,
                bitmap,
                "云端办专用图_${System.currentTimeMillis()}",
                "云端办专用图"
            )
            
            if (savedImageURL != null) {
                Toast.makeText(this, "图片已保存到相册", Toast.LENGTH_SHORT).show()
                LogManager.info("YDB专用图已保存到相册")
            } else {
                Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            LogManager.error("保存图片失败: ${e.message}")
        }
    }
}
