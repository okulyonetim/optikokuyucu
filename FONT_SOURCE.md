# Embedded form font

Optical-form rendering uses **Noto Sans** from Google Fonts.

- Upstream repository: `google/fonts`
- Pinned upstream commit: `5e35378e6bda803962ee6fd257e444a7d459660d`
- Source path: `ofl/notosans/NotoSans[wdth,wght].ttf`
- Expected Git blob SHA-1: `75575046c015ff623a848096a15779867ba71453`
- License: SIL Open Font License 1.1 (`app/src/main/res/raw/noto_sans_ofl.txt`)

The Android build downloads this exact pinned file, verifies its Git blob hash, and packages it as the generated `@font/noto_sans` resource. Runtime rendering never downloads a font and does not depend on the device font family.
