# CI/CD：GitHub Actions 自动构建

push 即 build，2 分钟出 APK，不用在自己电脑上装安卓开发环境。

## 思路

用 GitHub Actions 的 Ubuntu 机器跑 gradle build，产出 APK 作为 workflow artifact。

## 简化版 workflow

只做 **build（编译）**，不签名的示意：

```yaml
name: Build APK

on:
  push:
    branches: [ main ]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Gradle build
        run: chmod +x gradlew && ./gradlew assembleDebug

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/*.apk
```

## 签名自动化（release）

正式包要在 GitHub Secrets 里存 keystore：

1. 本地生成 keystore，base64 后存进仓库的 **Settings → Secrets**（如 `KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`）。
2. workflow 里 decode 出来再签名：

```yaml
- name: Restore keystore
  run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > keystore.jks
- name: Build release
  run: ./gradlew assembleRelease
```

**绝不把 keystore 明文写进仓库。** 哪怕是私有仓库也一样。

## 踩坑

- 国内网络拉 Android Gradle Plugin 可能很慢，可加 `mavenLocal` / 阿里镜像，或 `cache` gradle 依赖。
- `gradlew` 需要在仓库里带上，否则 Actions 里没有。
- CI 不会弹出登录/权限页，所以它只做构建，运行时权限引导仍靠 app 内的页面。