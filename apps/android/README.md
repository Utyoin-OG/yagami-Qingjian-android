# Yagami Android 输入法

Yagami 使用原生 Android `InputMethodService`，通过 JNI 复用青简的 GPL 开源 Core。

## 构建

需要 JDK 17、Android SDK 35、NDK 27、Rust 与 `cargo-ndk`：

```bash
rustup target add aarch64-linux-android x86_64-linux-android
cargo ndk -t arm64-v8a -t x86_64 -o apps/android/app/src/main/jniLibs build -p yagami-android-native --release
cd apps/android
./gradlew clean lintRelease assembleRelease
```

未配置签名环境变量时，`assembleRelease` 生成未签名 APK，适合 CI 验证但不能直接安装。

## 正式签名

正式发布前生成并离线备份自己的 keystore。不要把 keystore 或密码提交到 Git。
构建时设置以下环境变量：

```text
YAGAMI_KEYSTORE_FILE       keystore 的绝对路径
YAGAMI_KEYSTORE_PASSWORD   keystore 密码
YAGAMI_KEY_ALIAS           签名密钥别名
YAGAMI_KEY_PASSWORD        签名密钥密码
```

配置完成后再次运行 `./gradlew clean lintRelease assembleRelease`，输出位于
`app/build/outputs/apk/release/app-release.apk`。

## 安装与升级

应用包名是 `io.github.utyoinog.yagamiime`。Android 使用包名、签名证书和 `versionCode` 判断能否覆盖升级。
正式发布后必须永久保留同一签名证书，每次升级递增 `versionCode`；不要先卸载旧版，否则应用私有数据会被清除。

当前版本支持全拼、候选译词（英语、日语或西班牙语）、中英切换、Shift、数字与常用符号页、
短按及长按连续退格、空格上屏、多行换行、系统输入法切换和最近 50 条剪贴板文字记录。
滑动输入、语音和用户词频持久化尚未提供。

## 许可证与来源

代码采用 GPL-3.0-or-later。上游来源、修改范围和数据许可见仓库根目录的 `NOTICE.md` 与 `README.md`。
