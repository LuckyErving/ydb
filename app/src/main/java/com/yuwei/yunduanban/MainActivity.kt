package com.yuwei.yunduanban

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
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
    
    companion object {
        private const val PREFS_NAME = "YunDuanBanPrefs"
        private const val KEY_SELECTED_POLICE = "selected_police"
        private const val KEY_SELECTED_POLICE_POSITION = "selected_police_position"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 初始化LogManager
        LogManager.init(applicationContext)
        
        setupUI()
        setupListeners()
        loadLastSelection()
    }
    
    override fun onResume() {
        super.onResume()
        // 每次回到前台时检查无障碍服务状态
        checkAccessibilityServiceStatus()
    }
    
    private fun checkAccessibilityServiceStatus() {
        if (!isAccessibilityServiceEnabled()) {
            // 如果无障碍服务被关闭，显示提示
            binding.tvStatus.text = "⚠️ 无障碍服务已关闭，请重新开启"
            binding.tvStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
        } else {
            binding.tvStatus.text = "✅ 无障碍服务已开启"
            binding.tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
        }
    }
    
    private fun setupUI() {
        // 设置民警选择下拉框
        val policeNames = resources.getStringArray(R.array.police_names)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, policeNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPolice.adapter = adapter
        
        binding.spinnerPolice.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedPolice = policeNames[position]
                saveSelection(position, selectedPolice)
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
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
            
            startAutomation()
        }
        
        // 导出车牌按钮
        binding.btnExport.setOnClickListener {
            exportResults()
        }
        
        // 查看日志按钮
        binding.btnViewLog.setOnClickListener {
            showLogDialog()
        }
    }
    
    private fun showLogDialog() {
        val logs = LogManager.getLogsAsText()
        val logText = if (logs.isEmpty()) {
            "暂无日志记录"
        } else {
            logs
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("自动化运行日志")
            .setMessage(logText)
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
            .show()
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
    
    private fun startAutomation() {
        binding.tvStatus.text = getString(R.string.status_starting)
        binding.btnStart.isEnabled = false
        binding.btnExport.isEnabled = false
        
        lifecycleScope.launch {
            try {
                // 清空之前的结果
                AutomationDataManager.clearResults()
                LogManager.clearLogs()
                
                LogManager.info("开始自动化任务，民警：$selectedPolice")
                
                // 通过Intent启动无障碍服务的自动化流程
                val intent = Intent(this@MainActivity, YunDuanBanAccessibilityService::class.java)
                intent.action = YunDuanBanAccessibilityService.ACTION_START_AUTOMATION
                intent.putExtra(YunDuanBanAccessibilityService.EXTRA_POLICE_NAME, selectedPolice)
                
                // 通知服务开始执行
                YunDuanBanAccessibilityService.instance?.startAutomationTask(selectedPolice)
                
                binding.tvStatus.text = getString(R.string.status_running)
                Toast.makeText(this@MainActivity, "自动化任务已启动", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                LogManager.error("启动失败: ${e.message}")
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = "${getString(R.string.status_error)}: ${e.message}"
                    Toast.makeText(this@MainActivity, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.btnStart.isEnabled = true
                    binding.btnExport.isEnabled = true
                }
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
    
    override fun onResume() {
        super.onResume()
        // 检查服务状态并更新UI
        if (YunDuanBanAccessibilityService.instance?.isTaskRunning() == true) {
            binding.tvStatus.text = getString(R.string.status_running)
            binding.btnStart.isEnabled = false
        } else if (AutomationDataManager.getResults().isNotEmpty()) {
            binding.tvStatus.text = getString(R.string.status_completed)
            binding.btnStart.isEnabled = true
        } else {
            binding.tvStatus.text = getString(R.string.status_ready)
            binding.btnStart.isEnabled = true
        }
    }
}
