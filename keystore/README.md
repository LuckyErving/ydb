# Keystore 目录

此目录用于存储Android应用签名密钥库文件。

## Debug Keystore

debug.keystore 文件会在GitHub Actions构建时自动生成，使用标准的Android调试签名配置：

- Store Password: `android`
- Key Alias: `androiddebugkey`
- Key Password: `android`
- Validity: 10000 days

## 注意

⚠️ debug.keystore 仅用于开发和测试，**不应用于生产环境**。

生产环境应使用安全的release keystore，并将密钥信息存储在GitHub Secrets中。
