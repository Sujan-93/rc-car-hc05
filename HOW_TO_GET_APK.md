# How to build your APK (no Android Studio needed)

This project now includes a GitHub Actions workflow that builds the APK
in the cloud automatically. Steps:

1. **Create a free GitHub account** at https://github.com if you don't have one.
2. **Create a new repository** (e.g. `rc-car-hc05`), public or private, empty (no README).
3. **Upload this project folder** to that repository. Easiest way:
   - On the repo page, click "Add file" → "Upload files"
   - Drag in *everything inside* `RC_Car_HC05_Android/` (including the hidden
     `.github` folder — see note below)
   - Commit directly to the `main` branch
4. GitHub will automatically start the "Build APK" workflow. Watch it under
   the **Actions** tab of your repo.
5. When it finishes (green check, ~2-3 minutes), click into that workflow run,
   scroll to **Artifacts**, and download **RC_Car_HC05-debug-apk**.
   It's a zip containing `app-debug.apk` — that's your installable app.
6. Transfer the APK to your Android phone (email, Drive, USB) and tap it to
   install. You'll need to allow "install from unknown sources" the first time.

### Note on the hidden `.github` folder
The GitHub web uploader sometimes hides dotfolders in your file picker.
If `.github/workflows/build-apk.yml` doesn't show up after upload, the
easiest fix is to use **GitHub Desktop** (https://desktop.github.com) or the
`git` command line instead of the web uploader:

```bash
cd RC_Car_HC05_Android
git init
git add -A
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/<your-username>/rc-car-hc05.git
git push -u origin main
```

Then check the **Actions** tab as in step 4.

### What I fixed in this project
- Added `gradle/wrapper/gradle-wrapper.properties` (Gradle 8.9)
- Generated placeholder launcher icons for all densities (mipmap-mdpi
  through xxxhdpi) — swap these out later with your own app icon if you like
- Wired the icon into `AndroidManifest.xml`
- Added `.gitignore` for build artifacts
- Added `.github/workflows/build-apk.yml` — this is what makes GitHub build
  the APK for you automatically, no local SDK install required

### If you'd rather build locally instead
Open the project folder in Android Studio, let it sync (it will regenerate
`gradlew`/`gradlew.bat` automatically), then
**Build → Build Bundle(s)/APK(s) → Build APK(s)**.
