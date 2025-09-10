# 📦 smartChat – CI/CD Setup

This project includes Docker, mobile builds (Android/iOS/Windows), and CI/CD pipelines using **GitHub Actions**.

---

## 🚀 Workflows

Located in `.github/workflows/`:

- **docker-images.yml** → builds & pushes backend/frontend Docker images with automatic tags (`branch`, `tag`, `sha`).
- **android-build.yml** → builds a **signed APK** using base64-encoded keystore + Gradle signing.
- **ios-build.yml** → uses **Fastlane** to import certs & provisioning profiles and upload to **TestFlight**.
- **electron-build.yml** → packages the **Windows Electron app**.

---

## 🔑 Required GitHub Secrets

### 🔹 Docker
- `DOCKER_USER` → DockerHub or GitHub Container Registry user
- `DOCKER_PASS` → password or token

---

### 🔹 Android (signed APK)
- `ANDROID_KEYSTORE_BASE64` → keystore file encoded as base64
- `ANDROID_KEYSTORE_PASSWORD` → keystore password
- `ANDROID_KEY_ALIAS` → alias of your signing key
- `ANDROID_KEY_PASSWORD` → password for the key

**Create keystore (if you don’t have one yet):**
```bash
keytool -genkeypair -v -keystore release-key.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias smartchat
```
**Note:** remember to use a strong password and keep the `release-key.jks` file safe!

**Encode for GitHub Secrets:**
```bash
# Linux
base64 -w0 release-key.jks > release-key.jks.b64
# macOS
base64 release-key.jks > release-key.jks.b64
```
**Note:** Copy the contents of release-key.jks.b64 into the secret ANDROID_KEYSTORE_BASE64

### 🔹 iOS (Fastlane + TestFlight)
- APPLE_CERT_BASE64 → base64-encoded .p12 signing certificate
- APPLE_CERT_PASSWORD → password set during .p12 export
- APPLE_PROV_PROFILE_BASE64 → base64-encoded .mobileprovision provisioning profile
- APP_STORE_CONNECT_API_KEY_ID → App Store Connect API Key ID
- APP_STORE_CONNECT_API_ISSUER_ID → App Store Connect Issuer ID
- APP_STORE_CONNECT_API_KEY → contents of .p8 App Store Connect API key (or base64-encoded string)
- FASTLANE_APPLE_APPLICATION_SPECIFIC_PASSWORD → optional, for some upload flows

**Encode certificate & provisioning profile:**
```bash
# Encode signing certificate
base64 -w0 ios_dist.p12 > ios_cert.b64

# Encode provisioning profile
base64 -w0 provisioning_profile.mobileprovision > prov.b64
```
### 🔹 Windows (Electron)
No special secrets required. Build artifacts (.exe) are uploaded as GitHub Actions artifacts.
- `CSC_LINK` → base64-encoded PFX code signing certificate
- `CSC_KEY_PASSWORD` → password for the PFX file
- `WINDOWS_NOTARIZATION_USERNAME` → Apple ID email for notarization
- `WINDOWS_NOTARIZATION_PASSWORD` → app-specific password for notarization
- `WINDOWS_NOTARIZATION_TEAM_ID` → Apple Developer Team ID
- `WINDOWS_NOTARIZATION_BUNDLE_ID` → app bundle identifier
- `WINDOWS_NOTARIZATION_ASC_PROVIDER` → optional, Apple Service Connection provider
**Encode PFX certificate:**
```bash
base64 -w0 cert.pfx > cert.b64
```
---

## 📱 Local Development

### Backend (Spring Boot)
```bash
cd backend
./mvnw spring-boot:run
```
### Frontend (React)
```bash
cd frontend
npm install
npm start
```
### Dockerized stack (Postgres, Mongo, Redis, API, Angular frontend)
```bash
docker-compose up --build
```

## 📲 Mobile Platforms

### Android

- Code lives in mobile/android/ (after running npx cap add android).
- Open in Android Studio.
- Update app/build.gradle if needed for signingConfigs.
- Run locally:
```bash
cd mobile
cd mobile
npx cap sync android
npx cap open android
```

### iOS
- Code lives in mobile/ios/ (after running npx cap add ios).
- Open in Xcode.
- Update Info.plist for permissions.
- Run locally:
```bash
cd mobile
npx cap sync ios
npx cap open ios
```
### Windows (Electron)
- Code in mobile/windows/.
- Run locally:
```bash
  cd mobile
  npm install
  npm run electron:dev
```

### 🛠 Fastlane (iOS)
mobile/ios/fastlane/Fastfile contains:
```bash
default_platform(:ios)

platform :ios do
  desc "Build and upload to TestFlight"
  lane :beta do
    build_app(scheme: "App", export_method: "app-store")
    upload_to_testflight
  end
end
```
- Adjust scheme: "App" to match your Xcode scheme.
- Appfile contains your app identifier and Apple ID.
- Run locally:
```bash
cd mobile/ios
fastlane beta
```
---
## ⚙️ GitHub Actions Usage
- On push to main, the following happen:
  -   Docker images built & pushed (tagged by branch, tag, sha).
  -  Android signed APK built & uploaded as artifact.
  -  iOS app built & uploaded to TestFlight.
  - Windows Electron app built & uploaded as artifact.
  - Adjust triggers in .github/workflows/*.yml as needed.
  - Monitor runs in the Actions tab of your GitHub repo.
  - Artifacts available in the respective workflow run details.
  - Ensure all required secrets are set in the repo settings.
  - Customize build steps as needed for your project.
  - Refer to GitHub Actions docs for advanced usage.
-To run manually:
  - Go to the Actions tab in your GitHub repository.
  - Select the workflow you want to run (e.g., docker-images, android-build).
  - Click the "Run workflow" button on the right side.
  - Choose the branch and any required inputs, then click "Run workflow".
  - Monitor the progress and view logs in real-time.

---
## 🛠 Troubleshooting
### Android
- ❌ Error: keystore not found → Check that ANDROID_KEYSTORE_BASE64 is set and correctly base64-encoded.
- ❌ Gradle signing error → Ensure app/build.gradle supports android.injected.signing.* properties (default Android projects do).

### iOS
- ❌ Certificate import failed → Make sure .p12 is exported with a password, and that password is in APPLE_CERT_PASSWORD.
- ❌ Provisioning profile not found → Check that APPLE_PROV_PROFILE_BASE64 is correctly encoded.
- ❌ Fastlane cannot find scheme → Open project in Xcode and confirm your app scheme is shared. Update scheme: in Fastfile.

### Windows (Electron)
- ❌ Build error on Windows → Ensure electron-builder is installed, and GitHub runner is windows-latest.

### Docker
- ❌ Push failed → Ensure DOCKER_USER/DOCKER_PASS secrets are correct and have access to the registry.

## ✅ Checklist After Cloning
###### 1. Run:
```bash
cd mobile
npx cap sync
npx cap add android
npx cap add ios
npx cap add windows
```
- This regenerates android/ and ios/ projects.
- Make sure to open these in Android Studio and Xcode respectively to configure any platform-specific settings.
###### 2. Add **GitHub secrets** as above.
###### 3. Push to main → CI/CD runs automatically.

---
## 📚 References
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Docker Documentation](https://docs.docker.com/)
- [Fastlane Documentation](https://docs.fastlane.tools/)
- [Capacitor Documentation](https://capacitorjs.com/docs)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [React Documentation](https://reactjs.org/docs/getting-started.html)
- [Electron Documentation](https://www.electronjs.org/docs)
- [Android Developer Documentation](https://developer.android.com/docs)
- [iOS Developer Documentation](https://developer.apple.com/documentation/)
- [Windows App Development Documentation](https://docs.microsoft.com/en-us/windows/apps/)
- [Keytool Documentation](https://docs.oracle.com/javase/8/docs/technotes/tools/windows/keytool.html)
- [Base64 Encoding Documentation](https://en.wikipedia.org/wiki/Base64)
- [App Store Connect API Documentation](https://developer.apple.com/documentation/appstoreconnectapi)
- [Notarizing macOS Software](https://developer.apple.com/documentation/security/notarizing_your_app_before_distribution)
- [GitHub Secrets Documentation](https://docs.github.com/en/actions/security-guides/encrypted-secrets)
- [Gradle Documentation](https://docs.gradle.org/current/userguide/userguide.html)
- [Xcode Documentation](https://developer.apple.com/xcode/)
- [Android Studio Documentation](https://developer.android.com/studio)
- [TestFlight Documentation](https://developer.apple.com/testflight/)
- [Electron Builder Documentation](https://www.electron.build/)
- [Spring Initializr](https://start.spring.io/)
- [Create React App](https://create-react-app.dev/)
- [Capacitor CLI](https://capacitorjs.com/docs/cli)
- [Docker Compose Documentation](https://docs.docker.com/compose/)

---


