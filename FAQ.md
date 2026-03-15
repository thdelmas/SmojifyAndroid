# Installation

1. Make sure you ran the `./install.sh` script

## Spotify Developer Dashboard Configuration

### Required Steps:

1. **Add Package Name:**
   - Go to https://developer.spotify.com/dashboard
   - Open your app settings
   - Add package name: `com.emuji.emuji`

2. **Add SHA-1 Fingerprint:**
   - Run: `./gradlew signingReport`
   - Copy the SHA-1 fingerprint (usually starts with `SHA1:`)
   - Add it to the Spotify Developer Dashboard

3. **Add Redirect URI:**
   - In your Spotify app settings, add this redirect URI:
   - **Redirect URI:** `emuji://callback`
   - Click "Add" and then "Save"

## If you get KO at spotify authentication
> Run `./gradlew signingReport` and upload the SHA-1 to Spotify Developer Dashboard

## If you get "AUTHENTICATION_SERVICE_UNKNOWN_ERROR"
> Make sure you've added the redirect URI `emuji://callback` to your Spotify Developer Dashboard

## If emojis don't display correctly in the keyboard
The app uses EmojiCompat for consistent emoji rendering across Android versions. Make sure to:
1. Clean and rebuild the project: `./gradlew clean build`
2. Ensure you're testing on a device/emulator with Android API 24+ (Android 7.0+)
3. If emojis still don't appear, check that the device supports emoji rendering

## If your reactions aren't creating playlists
Check the Settings screen to ensure at least one playlist feature is enabled:
- **Create Personal Playlists**: Creates emoji-based playlists in your own Spotify account (enabled by default)
- **Participate in World Wide Playlists**: Contributes your reactions to global Emuji playlists

If you see "Personal playlist creation is disabled" in the logs, go to Settings and toggle on "Create Personal Playlists"

## If the app creates duplicate playlists (especially if your playlists are in folders)

**What's happening:**
- The app searches for existing emoji playlists by name before creating new ones
- The Spotify API returns all playlists regardless of folder organization
- The app now properly searches through **all** your playlists (even if you have 100+)

**Solution:**
The latest code fix ensures the app:
1. ✅ Searches through **all pages** of your playlists (not just the first 50)
2. ✅ Finds playlists even if they're organized in folders
3. ✅ Only creates a new playlist if the exact name doesn't exist anywhere
4. ✅ Shows clear log messages: "✓ Found existing playlist" or "Creating new playlist..."

**To check logs:**
```bash
adb logcat | grep SpotifyUtil
```

You should see messages like:
- "Searching playlists (offset: 0, found: 50 playlists)"
- "Retrieved playlist: [playlist name]"
- "✓ Found existing playlist: [your emoji playlist]" ← Good! Using existing
- "Playlist not found. Creating new playlist..." ← Creating new one

**Note:** If you already have duplicates, you can delete them in Spotify and the app will find/reuse the remaining one on your next reaction.
