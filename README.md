<div align="center">

# Pixel Watermark

**A native Material 3 watermark app for Android**<br>
**一款原生 Material 3 风格的 Android 照片水印工具**

[![Android](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)

[中文](#中文) · [English](#english)

</div>

---

## 中文

Pixel Watermark 是一款使用 Kotlin、Jetpack Compose 和 Material 3 构建的原生 Android 照片水印应用。选择一张或多张照片后，应用会在设备本地完成处理，并将结果直接保存到系统相册。

它既支持 Pixel / Google Logo，也支持多种字体水印、自动照片信息、智能黑白配色和独立的底部信息栏。界面会跟随系统深浅色模式，并使用 Android 系统照片选择器，无需上传照片到服务器。

### 功能特性

- **批量处理**：一次最多选择 50 张照片，选择后自动处理并导出。
- **原生 Material 3 界面**：支持动态色彩、深色模式和符合 Android 习惯的交互。
- **两类 Logo 水印**：
  - Pixel Logo：自动、白色、黑色。
  - Google Logo：自动、白色、黑色、彩色。
- **五种字体样式**：Google Sans、Bitcount、Gilbert Color、Inter、Libertinus Serif。
- **可编辑的双行内容**：分别设置设备名称和拍摄参数。
- **模板变量**：
  - `{device}`：替换为当前 Android 设备型号。
  - `{image_info}`：替换为照片中的焦距、光圈、快门和 ISO 等 EXIF 信息。
- **智能颜色**：分析水印区域的亮度，在黑色与白色之间自动选择对比度更高的版本。
- **灵活布局**：支持四角定位，并可调整边距、大小和不透明度。
- **底部信息栏**：可在照片下方扩展独立区域，让文字不遮挡画面；还可在右侧添加单色或彩色 Google Logo。
- **高质量导出**：不缩小原照片像素尺寸，以 JPEG 质量 100 导出；启用底部信息栏时只增加画布高度。
- **元数据保留**：尽可能复制拍摄时间、设备、镜头、曝光参数、GPS 和 XMP 等标准元数据。
- **本地处理**：照片处理完全在设备端完成，应用不需要网络权限。

### 水印样式

| 样式 | 类型 | 可用颜色 |
| --- | --- | --- |
| Pixel Logo | Logo | 自动 / 白色 / 黑色 |
| Google Logo | Logo | 自动 / 白色 / 黑色 / 彩色 |
| Google Sans | 文字 | 自动 / 白色 / 黑色 |
| Bitcount | 文字 | 自动 / 白色 / 黑色 |
| Gilbert Color | 彩色文字 | 字体原生颜色 |
| Inter | 文字 | 自动 / 白色 / 黑色 |
| Libertinus Serif | 文字 | 自动 / 白色 / 黑色 |

> Pixel Logo 与 Google Logo 是独立水印样式，不会与文字水印叠加。选择 Google Logo 作为主水印时，不会再显示底部信息栏的右侧 Logo 选项。

### 使用方法

1. 在“设置”中选择水印样式。
2. 如果使用文字水印，编辑主标题与拍摄信息模板。
3. 设置颜色、位置、大小、边距和不透明度。
4. 按需开启底部信息栏和右侧 Logo。
5. 返回首页，点击“选择照片”。
6. 选择一张或多张照片，应用会自动处理并保存到：

```text
Pictures/Pixel Watermark
```

### 权限与隐私

应用仅声明 `ACCESS_MEDIA_LOCATION` 权限，用于在用户允许后读取照片中未脱敏的位置信息，并将 GPS 元数据写入处理后的照片。

- 拒绝该权限不会阻止普通水印处理，但导出的照片无法保留 GPS。
- Android 系统照片选择器可能无法为部分云端照片提供原始文件，此时应用会继续处理照片，但不保证保留位置元数据。
- 应用没有网络权限，不会上传照片或元数据。

### 图像质量与元数据

添加水印需要重新编码图像，因此 JPEG 文件无法做到字节级无损。应用不会主动缩小原图分辨率，并使用 JPEG 质量 100 导出，以尽量减少额外画质损失。

标准 EXIF、GPS 和 XMP 元数据会尽可能保留，但部分厂商私有 MakerNote、嵌入式缩略图或云端媒体特有元数据可能无法复制。

### 构建项目

要求：

- Android Studio（建议使用当前稳定版）
- JDK 17 或更高版本
- Android SDK 36 / API 36.1
- Android 10（API 29）或更高版本的设备或模拟器

克隆并构建 Debug APK：

```bash
git clone https://github.com/lopleec/Pixel-Water-Mark.git
cd Pixel-Water-Mark
./gradlew assembleDebug
```

APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 许可证与声明

本项目使用 [GNU General Public License v3.0](LICENSE) 开源。

Pixel、Google 及相关 Logo 是其各自权利人的商标。本项目与 Google LLC 没有隶属、授权或背书关系。使用内置字体和品牌素材时，请同时遵守其各自的许可与品牌规范。

---

## English

Pixel Watermark is a native Android photo watermarking app built with Kotlin, Jetpack Compose, and Material 3. Select one or multiple photos, and the app processes them locally before saving the results directly to the system gallery.

It supports Pixel and Google logos, multiple font styles, automatic photo information, adaptive black-or-white coloring, and an optional bottom information bar. The interface follows the system light or dark theme and uses Android's system Photo Picker—your photos do not need to be uploaded to a server.

### Features

- **Batch processing**: Select up to 50 photos and export them automatically.
- **Native Material 3 UI**: Dynamic color, dark mode, and familiar Android interactions.
- **Two logo watermark styles**:
  - Pixel Logo: Auto, White, or Black.
  - Google Logo: Auto, White, Black, or Color.
- **Five font styles**: Google Sans, Bitcount, Gilbert Color, Inter, and Libertinus Serif.
- **Editable two-line text**: Configure the device name and shooting-information lines independently.
- **Template variables**:
  - `{device}` is replaced with the current Android device model.
  - `{image_info}` is replaced with available EXIF data such as focal length, aperture, shutter speed, and ISO.
- **Adaptive coloring**: Analyzes the target area and automatically selects black or white for better contrast.
- **Flexible placement**: Four-corner positioning with adjustable margin, size, and opacity.
- **Bottom information bar**: Adds a separate area below the photo so text does not cover the image, with an optional monochrome or color Google logo on the right.
- **High-quality export**: Keeps the source pixel dimensions and exports at JPEG quality 100. Bottom-bar mode only extends the canvas height.
- **Metadata preservation**: Attempts to preserve standard capture time, camera, lens, exposure, GPS, and XMP metadata.
- **On-device processing**: Photo processing stays on the device, and the app does not request network access.

### Watermark styles

| Style | Type | Available colors |
| --- | --- | --- |
| Pixel Logo | Logo | Auto / White / Black |
| Google Logo | Logo | Auto / White / Black / Color |
| Google Sans | Text | Auto / White / Black |
| Bitcount | Text | Auto / White / Black |
| Gilbert Color | Color text | Native font colors |
| Inter | Text | Auto / White / Black |
| Libertinus Serif | Text | Auto / White / Black |

> Pixel Logo and Google Logo are standalone watermark styles and are not combined with text watermarks. When Google Logo is selected as the primary watermark, the extra right-side logo option is hidden in bottom-bar mode.

### Usage

1. Open **Settings** and choose a watermark style.
2. For text watermarks, edit the title and shooting-information templates.
3. Configure color, position, size, margin, and opacity.
4. Optionally enable the bottom information bar and its right-side logo.
5. Return to the home screen and tap **Select photos**.
6. Choose one or multiple photos. Processed images are saved to:

```text
Pictures/Pixel Watermark
```

### Permissions and privacy

The app declares only the `ACCESS_MEDIA_LOCATION` permission. With your consent, it uses this permission to read unredacted location metadata and copy GPS information to processed photos.

- Denying the permission does not block normal watermark processing, but GPS cannot be preserved.
- Android's Photo Picker may not expose the original file for some cloud media. The app will still process those images, but location metadata may not be retained.
- The app has no network permission and does not upload photos or metadata.

### Image quality and metadata

Applying a watermark requires re-encoding the image, so JPEG output cannot be byte-for-byte lossless. The app does not intentionally downscale the source and exports at JPEG quality 100 to minimize additional quality loss.

Standard EXIF, GPS, and XMP metadata is preserved where possible. Some vendor-specific MakerNote data, embedded thumbnails, or cloud-provider metadata may not be transferable.

### Build from source

Requirements:

- Android Studio (current stable version recommended)
- JDK 17 or newer
- Android SDK 36 / API 36.1
- A device or emulator running Android 10 (API 29) or newer

Clone the repository and build a debug APK:

```bash
git clone https://github.com/lopleec/Pixel-Water-Mark.git
cd Pixel-Water-Mark
./gradlew assembleDebug
```

The APK will be generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### License and trademarks

This project is licensed under the [GNU General Public License v3.0](LICENSE).

Pixel, Google, and the associated logos are trademarks of their respective owners. This project is not affiliated with, authorized by, or endorsed by Google LLC. When using bundled fonts and brand assets, please also follow their respective licenses and brand guidelines.
