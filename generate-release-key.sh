#!/bin/bash

# 云端办APP签名密钥生成脚本

set -e

KEYSTORE_DIR="keystore"
KEYSTORE_FILE="$KEYSTORE_DIR/release.keystore"
ALIAS="yunduanban"
STORE_PASS="yunduanban2025"
KEY_PASS="yunduanban2025"
VALIDITY="10000"
DNAME="CN=YunDuanBan,OU=YUWEI,O=Future Center,L=Guangzhou,ST=Guangdong,C=CN"

echo "======================================"
echo "云端办APP签名密钥生成工具"
echo "======================================"
echo ""

# 检查keystore目录
if [ ! -d "$KEYSTORE_DIR" ]; then
    echo "创建 $KEYSTORE_DIR 目录..."
    mkdir -p "$KEYSTORE_DIR"
fi

# 检查是否已存在
if [ -f "$KEYSTORE_FILE" ]; then
    echo "⚠️  警告: $KEYSTORE_FILE 已存在！"
    read -p "是否覆盖? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "取消操作。"
        exit 0
    fi
    rm -f "$KEYSTORE_FILE"
fi

# 查找keytool
KEYTOOL=""

# 方法1: 尝试使用系统keytool
if command -v keytool &> /dev/null; then
    KEYTOOL="keytool"
    echo "✅ 找到系统keytool"
# 方法2: 尝试使用JAVA_HOME
elif [ -n "$JAVA_HOME" ] && [ -f "$JAVA_HOME/bin/keytool" ]; then
    KEYTOOL="$JAVA_HOME/bin/keytool"
    echo "✅ 找到JAVA_HOME keytool: $JAVA_HOME"
# 方法3: 尝试查找Android Studio的JDK
elif [ -d "/Applications/Android Studio.app" ]; then
    AS_KEYTOOL=$(find "/Applications/Android Studio.app" -name "keytool" 2>/dev/null | head -1)
    if [ -n "$AS_KEYTOOL" ]; then
        KEYTOOL="$AS_KEYTOOL"
        echo "✅ 找到Android Studio keytool: $KEYTOOL"
    fi
fi

# 如果找不到keytool
if [ -z "$KEYTOOL" ]; then
    echo "❌ 错误: 找不到keytool工具"
    echo ""
    echo "请选择以下方法之一："
    echo "1. 安装JDK: brew install openjdk@17"
    echo "2. 使用Android Studio生成（推荐）："
    echo "   Build → Generate Signed Bundle / APK → Create new..."
    echo ""
    exit 1
fi

# 生成密钥
echo ""
echo "开始生成签名密钥..."
echo "位置: $KEYSTORE_FILE"
echo "别名: $ALIAS"
echo "有效期: $VALIDITY 天"
echo ""

"$KEYTOOL" -genkeypair -v \
    -keystore "$KEYSTORE_FILE" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity "$VALIDITY" \
    -storepass "$STORE_PASS" \
    -keypass "$KEY_PASS" \
    -dname "$DNAME"

if [ $? -eq 0 ]; then
    echo ""
    echo "======================================"
    echo "✅ 签名密钥生成成功！"
    echo "======================================"
    echo ""
    echo "密钥信息："
    echo "  文件: $KEYSTORE_FILE"
    echo "  Store Password: $STORE_PASS"
    echo "  Key Alias: $ALIAS"
    echo "  Key Password: $KEY_PASS"
    echo ""
    echo "下一步："
    echo "  1. git add $KEYSTORE_FILE"
    echo "  2. git commit -m 'add release keystore'"
    echo "  3. git push"
    echo ""
    echo "⚠️  重要提醒："
    echo "  - 请妥善备份此密钥文件"
    echo "  - 密钥丢失后无法再发布更新"
    echo "  - 不要修改密码和别名"
    echo ""
else
    echo ""
    echo "❌ 密钥生成失败"
    exit 1
fi
