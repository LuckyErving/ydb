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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        setupListeners()
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
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
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
                
                // 通过Intent启动无障碍服务的自动化流程
                val intent = Intent(this@MainActivity, YunDuanBanAccessibilityService::class.java)
                intent.action = YunDuanBanAccessibilityService.ACTION_START_AUTOMATION
                intent.putExtra(YunDuanBanAccessibilityService.EXTRA_POLICE_NAME, selectedPolice)
                
                // 通知服务开始执行
                YunDuanBanAccessibilityService.instance?.startAutomationTask(selectedPolice)
                
                binding.tvStatus.text = getString(R.string.status_running)
                Toast.makeText(this@MainActivity, "自动化任务已启动", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
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
