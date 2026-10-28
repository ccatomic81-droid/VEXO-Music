# VEXO Music 0.26.2

- Reduce the gap between artist circles from 30 dp to 24 dp while preserving the 76 dp circle size and readable labels.
- Add Import device music as the primary Phone library and local-search empty-state action. It requests Android music/audio access and scans the phone’s music after permission is granted.
- Use READ_MEDIA_AUDIO on Android 13+ and READ_EXTERNAL_STORAGE on older supported versions, with permission rationale and an app-settings path for blocked access.
- Refresh the library when audio permission changes after returning from Android settings, including when access is revoked.
- Keep Choose files for individually selected audio files using Android’s document picker. Audio files stay on the device.
- Preserve artist portraits, light/dark/device themes, Global discovery, animated updates and 500 MB offline saving.

Install over the existing app without uninstalling. Version code 42 updates public 0.26.1/code41 using the original signing identity and preserves saved music and settings.

Validation includes the actual library import action, Android 13+ and older permission selection, granted access, blocked permission settings, selected-file fallback, and native light/dark artist-row layout review. Physical-phone playback and Android/OEM permission UI were not manually tested.
