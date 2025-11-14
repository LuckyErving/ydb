# Keystore 目录

此目录用于存储Android应用签名密钥库文件。

## 文件说明

### release.keystore（生产签名）
用于正式发布的签名密钥，确保APP更新时签名一致：

- Store Password: `yunduanban2025`
- Key Alias: `yunduanban`
- Key Password: `yunduanban2025`
- Validity: 10000 days

**如何生成：** 请参考 `generate-keystore.md` 文件

### debug.keystore（调试签名）
仅在 release.keystore 不存在时作为备用，会在构建时自动生成：

- Store Password: `android`
- Key Alias: `androiddebugkey`
- Key Password: `android`
- Validity: 10000 days

## 重要提醒

⚠️ **release.keystore 必须提交到仓库**
- 这样 GitHub Actions 和本地构建会使用相同签名
- 确保APP可以正常覆盖安装更新
- 密钥文件务必妥善备份，丢失后无法再发布更新

✅ **已配置自动检测**
- 如果 release.keystore 存在，自动使用
- 如果不存在，自动回退到 debug.keystore
- 构建时会有警告提示
