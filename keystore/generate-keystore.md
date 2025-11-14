# 生成签名密钥步骤

## 问题说明
签名不一致是因为每次GitHub Actions构建都生成新的临时密钥，导致无法覆盖安装。

## 解决方案：生成固定的签名密钥

### 方法1：使用Android Studio生成（推荐）

1. 打开 Android Studio
2. 菜单：Build → Generate Signed Bundle / APK
3. 选择 APK → Next
4. 点击 "Create new..."
5. 填写信息：
   - Key store path: 选择 `项目目录/keystore/release.keystore`
   - Password: `yunduanban2025`
   - Alias: `yunduanban`
   - Key password: `yunduanban2025`
   - Validity: 25 年
   - Certificate:
     - First and Last Name: YunDuanBan
     - Organizational Unit: YUWEI
     - Organization: Future Center
     - City: Guangzhou
     - State: Guangdong
     - Country Code: CN
6. 点击 OK 生成

### 方法2：使用命令行（需要JDK）

如果系统已安装JDK，在项目根目录执行：

```bash
keytool -genkeypair -v \
  -keystore keystore/release.keystore \
  -alias yunduanban \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass yunduanban2025 \
  -keypass yunduanban2025 \
  -dname "CN=YunDuanBan,OU=YUWEI,O=Future Center,L=Guangzhou,ST=Guangdong,C=CN"
```

## 生成后的步骤

1. 确保 `keystore/release.keystore` 文件已创建
2. 运行：`git add keystore/release.keystore`
3. 运行：`git commit -m "add release keystore"`
4. 运行：`git push`
5. 重新构建并安装APP

## 重要提醒

⚠️ **密钥文件非常重要！**
- 一旦丢失，无法再发布更新（用户需要卸载重装）
- 建议备份到安全位置
- 不要修改密码和别名

✅ **签名密钥已配置在 GitHub Actions 中**
- 提交后，CI/CD 会自动使用此密钥签名
- 本地构建和线上构建使用相同签名
- 可以正常覆盖安装
