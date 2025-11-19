# 云端办小助手

<div align="center">

[![Android CI/CD](https://github.com/your-username/ydb/actions/workflows/android-ci.yml/badge.svg)](https://github.com/your-username/ydb/actions/workflows/android-ci.yml)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-10%2B-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.20-purple.svg)](https://kotlinlang.org)

**自动化政务微信和黄盾执法处理流程的Android应用**

新质战斗力未来中心 出品

</div>

---

## 📋 项目简介

云端办小助手是一款基于Android无障碍服务开发的自动化工具，用于简化执法处理流程。通过自动化操作政务微信和黄盾执法系统，极大提升工作效率。

原项目基于AutoJS编写，现已完整迁移到原生Android应用，提供更稳定、更高效的使用体验。

## ✨ 核心功能

- 🤖 **自动化执法流程** - 自动处理违法车辆信息录入和开单
- 👮 **民警快速切换** - 支持多个民警账号快速选择
- 📋 **车牌批量导出** - 一键导出已处理的车牌信息
- 🔄 **智能循环处理** - 自动处理多条违法记录（最多150条）
- ⚡ **智能错误处理** - 自动应对各种异常情况和网络问题
- 🗑️ **自动消息清理** - 处理完成后自动清理微信消息

## 📱 系统要求

- **操作系统**: Android 10.0 (API 29) 及以上
- **必需权限**:
  - 无障碍服务权限
  - 悬浮窗权限
- **依赖应用**:
  - 政务微信（企业微信）
  - 黄盾执法处理系统

## 🚀 快速开始

### 下载安装

1. 前往 [Releases](https://github.com/your-username/ydb/releases) 页面
2. 下载最新版本的 APK 文件
3. 在Android设备上安装（需允许安装未知来源应用）

### 首次配置

1. **开启无障碍服务**
   - 打开手机 `设置` → `无障碍` → `已下载的服务`
   - 找到 `云端办自动化服务` 并开启

2. **授予悬浮窗权限**（如提示）
   - 允许应用显示悬浮窗，用于状态提示

3. **配置政务微信包名**（重要！）
   - 在主界面找到"政务微信包名"输入框
   - 默认为企业微信：`com.tencent.wework`
   - 如果是公安内网政务微信，请修改为实际的包名
   - 常见包名示例：
     - 企业微信：`com.tencent.wework`
     - 公安政务微信：请咨询IT部门获取实际包名
   - 💡 **如何查看包名**：使用 APK提取器 或 开发者工具查看已安装应用的包名

4. **确认屏幕分辨率**
   - 应用会自动显示当前设备的屏幕分辨率
   - 如果OCR识别位置不准确，可能需要调整基准分辨率
   - 基准分辨率默认为 1080x2340（开发设备）
   - 如需修改，请联系开发者或参考"开发者配置"章节

5. **准备工作环境**
   - 登录黄盾系统，进入 `执法处理` → `云端办` 模块
   - 打开政务微信，确保有待处理的违法信息

## 📖 使用说明

### 基本流程

1. **准备数据**
   - 先登录 [黄盾]，进入 [执法处理] 模块，点击 [云端办]
   - 打开 [政务微信]，找到从网页端转发过来的违法信息

2. **选择民警**
   - 在应用主界面的下拉框中选择当前执法民警

3. **启动自动化**
   - 点击 `开始运行` 按钮
   - 应用将自动执行以下流程：
     - 读取政务微信中的违法车辆信息
     - 自动切换到黄盾执法系统
     - 查询车辆信息
     - 自动开单（简易A版/警告等）
     - 清理已处理的微信消息

4. **导出结果**
   - 任务完成后，点击 `导出车牌` 按钮
   - 已处理的车牌信息会复制到剪贴板
   - 可将结果发送到政务微信完成后续流程

5. **紧急停止**
   - 按下手机 **音量上键** 可随时终止运行

### 注意事项

⚠️ **使用前请注意**:
- 确保网络连接稳定
- 保持屏幕常亮，避免锁屏中断
- 首次使用建议小批量测试
- 自动化过程中请勿手动操作屏幕
- 定期检查处理结果的准确性

## 🛠️ 技术架构

### 技术栈

- **开发语言**: Kotlin
- **最低SDK**: API 29 (Android 10)
- **目标SDK**: API 34 (Android 14)
- **UI框架**: Material Design 3
- **核心技术**:
  - AccessibilityService（无障碍服务）
  - Coroutines（协程）
  - ViewBinding（视图绑定）
  - Google ML Kit（OCR识别）

### 项目结构

```
ydb/
├── app/
│   ├── src/main/
│   │   ├── java/com/yuwei/yunduanban/
│   │   │   ├── MainActivity.kt                    # 主界面
│   │   │   ├── YunDuanBanAccessibilityService.kt  # 无障碍服务
│   │   │   └── AutomationDataManager.kt           # 数据管理
│   │   ├── res/
│   │   │   ├── layout/                            # 布局文件
│   │   │   ├── values/                            # 资源文件
│   │   │   └── xml/                               # 配置文件
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── .github/workflows/
│   └── android-ci.yml                             # CI/CD配置
├── build.gradle
├── settings.gradle
└── README.md
```

### 核心模块

1. **MainActivity** - 主界面，负责用户交互和权限管理
2. **YunDuanBanAccessibilityService** - 无障碍服务，执行自动化流程
3. **AutomationDataManager** - 管理处理结果数据

## ⚙️ 开发者配置

### 调整基准分辨率

如果您的设备屏幕分辨率与开发设备（1080x2340）差异较大，OCR识别和点击位置可能不准确。

**修改基准分辨率**：

1. 打开文件：`app/src/main/java/com/yuwei/yunduanban/CoordinateScaler.kt`
2. 修改以下常量为您的设备分辨率：

```kotlin
// 将这两个值修改为您的设备分辨率
private const val BASE_WIDTH = 1080   // 改为您的屏幕宽度
private const val BASE_HEIGHT = 2340  // 改为您的屏幕高度
```

3. 重新编译并安装应用

**说明**：
- 应用会自动检测当前设备分辨率
- 所有坐标会根据分辨率比例自动缩放
- 主界面会显示当前分辨率信息供参考

### 调整OCR识别区域

如果坐标缩放后OCR仍不准确，可能需要微调OCR识别区域：

位置：`YunDuanBanAccessibilityService.kt` 中的 `performOCR()` 调用
- 第1个参数：X坐标（左上角）
- 第2个参数：Y坐标（左上角）
- 第3个参数：宽度
- 第4个参数：高度

示例：
```kotlin
val weifacheliang = performOCR(150, 1888, 333, 116)
// 调整为：performOCR(X偏移, Y偏移, 宽度, 高度)
```

## 🔧 开发指南

### 环境准备

```bash
# 克隆仓库
git clone https://github.com/your-username/ydb.git
cd ydb

# 使用Android Studio打开项目
# 或使用命令行构建
./gradlew build
```

### 编译APK

```bash
# Debug版本
./gradlew assembleDebug

# Release版本
./gradlew assembleRelease
```

APK输出路径: `app/build/outputs/apk/`

### 运行测试

```bash
./gradlew test
```

## 📦 CI/CD

项目使用 GitHub Actions 实现自动化构建和发布：

### 自动构建

- **触发条件**: 推送到 main/master 分支或提交PR
- **执行内容**: 
  - 编译项目
  - 运行单元测试
  - 生成Release APK
  - 上传APK作为artifact（保留30天）

### 自动发布

- **触发条件**: 推送版本标签 (如 `v1.0.0`)
- **执行内容**:
  - 构建Release APK
  - 自动创建GitHub Release
  - 上传APK到Release页面
  - 生成版本说明

### 发布新版本

```bash
# 1. 更新版本号（修改 app/build.gradle 中的 versionCode 和 versionName）
# 2. 提交代码
git add .
git commit -m "chore: bump version to 1.0.1"
git push

# 3. 创建并推送标签
git tag v1.0.1
git push origin v1.0.1

# GitHub Actions 会自动构建并发布
```

## 🤝 贡献指南

欢迎提交Issue和Pull Request！

### 贡献流程

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

## 📄 开源协议

本项目采用 MIT 协议开源 - 查看 [LICENSE](LICENSE) 文件了解详情

## 👨‍💻 作者

**YUWEI** - 新质战斗力未来中心

## 🙏 致谢

- 感谢AutoJS项目提供的灵感
- 感谢Google ML Kit提供OCR能力
- 感谢所有贡献者和使用者

## 📞 联系方式

如有问题或建议，欢迎通过以下方式联系：

- 提交 [Issue](https://github.com/your-username/ydb/issues)
- 发送邮件至: your-email@example.com

---

<div align="center">

**© 2025 新质战斗力未来中心**

Made with ❤️ by YUWEI

</div>
