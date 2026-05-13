# Changelog

All notable changes to Strictly will be documented in this file.

## [Unreleased]

### Added
- Initial public release scaffolding
- `Strictly` public API with `install`, `openDetailScreen`, `markBaseline`, `clear`, `violations`
- Zero-config `ContentProvider` auto-init
- Live, Chucker-style sticky notification with de-duplication plus debounced updates
- Compose detail screen with violation cards, severity coloring, copy/share actions
- Dynamic home-screen app shortcut (long-press app icon)
- `POST_NOTIFICATIONS` permission flow on API 33+ when launched from shortcut
- No-op release artifact (`strictly-noop`) for zero footprint in production
- Configurable `appPackages`, `ignoredPackages`, `detectedTypes`, store size, debounce
- Baseline mode for legacy codebases
- Apache 2.0 license
