# App updates and releases

## Current delivery state

VEXO Music 0.7.0 has working update-check and release-notice UI, but its public release feed is **not connected**. Source is pushed to a private GitHub repository. A private GitHub raw-file URL cannot be used by installed apps without authentication; no GitHub credential is shipped in the app. The installed-version summary works offline without a feed.

To enable update delivery, publish `stable.json` and the signed APK on an HTTPS host users can read without signing in. Then paste the public JSON URL in Settings → Update source. Future builds may set `VEXO_UPDATE_FEED_URL` to that same public URL when compiling, so users do not need to configure it.

Example feed, with real public URLs substituted before publishing:

```json
{
  "applicationId": "space.vexo.music.radio",
  "versionCode": 10,
  "versionName": "0.8.0",
  "summary": "Describe what changed in this release.",
  "downloadUrl": "https://YOUR-PUBLIC-HOST/VEXO-Music-0.8.0.apk"
}
```

Version codes must increase for every release; 0.7.0 uses code 9. A current or older code does not prompt an update. The JSON response is limited to 64 KiB; the release summary to 12,000 characters. App identity and HTTPS URLs are validated, and at most three HTTPS redirects are followed. Feed errors show a retry message during manual checks and do not interrupt automatic checks. Requests have a 12-second timeout each (including each allowed redirect).

Automatic checks run at most once per 24 hours when the app opens, if enabled and connected. This is not background push notification delivery. Dismissing a remote version snoozes it until a newer version arrives; a manual check can show it again. Downloading opens the browser only after the user taps Get update; Android controls installation. No automatic APK install permission is requested. Keep this feed under developer control: metadata validation does not replace APK signing.

## Preserve app data and upgrade signing

Use the same signing certificate as 0.4.x/0.5.0. The delivered local APK uses the existing key, verified against certificate SHA-256:

`0b31c50904567389b723e23ad6043b2d2fc6635c30dfdb3ed5ef3f9ba9727ef3`

Install over the existing app; uninstalling removes its local data. The signing key is deliberately absent from source control and source ZIPs.

GitHub Actions creates a test/debug APK with the runner’s debug key by default. That CI artifact is for clean test installations, and **cannot update this signed installation** until the original key is configured securely. Do not publish the runner-signed APK as an upgrade. The workflow does not publish releases automatically.

## Search privacy

Typing at least two characters fetches phrase suggestions and playable song metadata from YouTube through NewPipe Extractor. Home also fetches metadata for its first three mixes. The selected music country is supplied. Up to eight suggestions are shown; recent matching searches come first. At most 20 recent terms are stored in private device preferences. URLs are not added to history. Settings and the search screen provide clear/remove controls. Local phone-music search does not request remote suggestions. No analytics backend is used.

Primary API reference for suggestions: https://teamnewpipe.github.io/NewPipeExtractor/javadoc/org/schabi/newpipe/extractor/suggestion/SuggestionExtractor.html
