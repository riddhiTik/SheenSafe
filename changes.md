# Changes — SheenSafe logo & app icon

## Summary
Added the actual SheenSafe logo (blue shield + wave + orange sun/heat mark) as the app's launcher icon and as the in-app branding on the login screen.

## Source
Logo image provided by user, saved at `~/Downloads/imagelogo.jpeg` (1254x1254 JPEG). Cropped with PIL to isolate the icon mark from the "SheenSafe" wordmark so it isn't clipped by the Android adaptive-icon mask.

## Files added
- `app/src/main/res/drawable/image.png` — full logo (icon + "SheenSafe" wordmark), converted from the source JPEG.
- `app/src/main/res/drawable/ic_launcher_foreground.png` — icon-only crop (wordmark excluded), used as the adaptive icon foreground.
- `app/src/main/res/drawable/ic_launcher_background.xml` — solid near-white background shape for the adaptive icon.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` — adaptive icon descriptor (background + foreground).
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` — same, for the round icon variant.

## Files modified
- `app/src/main/AndroidManifest.xml` — added `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"` to the `<application>` tag (previously no icon was set).
- `app/src/main/res/layout/fragment_login.xml` — replaced the plain tinted `ic_shield` icon in the "SheenSafe" header with the real logo (`ic_launcher_foreground.png`), sized 40dp x 40dp, no tint (full color).

## Files removed
- `app/src/main/res/drawable/ic_sheensafe_logo.xml` — an earlier hand-drawn vector recreation of the logo (built before the real image file was available); deleted once the real image was wired in.

## Notes
- `minSdk = 26`, so only `mipmap-anydpi-v26` adaptive icon XML is needed — no legacy density-specific PNG mipmaps required.
- Verified with `./gradlew :app:processDebugResources` — resources compile cleanly, no errors.
- No other UI, logic, or data files were touched.
