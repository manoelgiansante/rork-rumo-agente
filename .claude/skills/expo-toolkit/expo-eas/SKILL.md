# EAS Build & Submit

This skill provides expert knowledge on Expo Application Services (EAS) for building, submitting, and managing React Native Expo apps.

## When to Use

- Setting up EAS for a new project (`eas init`, `eas.json` configuration)
- Configuring build profiles (development, preview, production)
- Managing credentials (iOS certificates, Android keystores, push notifications)
- Understanding `--submit` behaviour differences between iOS and Android
- Troubleshooting EAS build or submit issues
- Configuring environment-specific bundle IDs for development builds

## Quick Reference

### Essential Commands

```bash
# Initialize EAS in project
eas init

# Configure build profiles
eas build:configure

# Check/manage credentials
eas credentials
eas credentials --platform ios
eas credentials --platform android

# Build commands
eas build --platform ios --profile development
eas build --platform android --profile preview
eas build --platform all --profile production

# Build with auto-submit
eas build --platform ios --profile production --auto-submit
eas build --platform android --profile production --auto-submit

# Check build status
eas build:list
```

## eas.json Configuration

### Standard Build Profiles

The `eas.json` file defines build profiles. A well-configured file looks like:

```json
{
  "cli": {
    "version": ">= 16.18.0",
    "appVersionSource": "remote"
  },
  "build": {
    "base": {
      "env": {
        "APP_VARIANT": "production"
      }
    },
    "development": {
      "extends": "base",
      "developmentClient": true,
      "distribution": "internal",
      "env": {
        "APP_VARIANT": "development"
      },
      "ios": {
        "simulator": false
      },
      "android": {
        "buildType": "apk",
        "gradleCommand": ":app:assembleDebug"
      }
    },
    "development-simulator": {
      "extends": "development",
      "ios": {
        "simulator": true
      },
      "android": {
        "buildType": "apk"
      }
    },
    "preview": {
      "extends": "base",
      "distribution": "internal",
      "env": {
        "APP_VARIANT": "preview"
      },
      "android": {
        "buildType": "apk"
      }
    },
    "production": {
      "extends": "base",
      "autoIncrement": true,
      "env": {
        "APP_VARIANT": "production"
      }
    }
  },
  "submit": {
    "production": {
      "ios": {
        "appleId": "your@email.com",
        "ascAppId": "1234567890"
      },
      "android": {
        "serviceAccountKeyPath": "./play-store-credentials.json",
        "track": "internal"
      }
    }
  }
}
```

### Profile Properties Reference

| Property | Description | Common Values |
|----------|-------------|---------------|
| `developmentClient` | Include expo-dev-client | `true` for dev builds |
| `distribution` | How build is distributed | `internal`, `store`, `simulator` |
| `autoIncrement` | Auto-increment version | `true`, `"version"`, `"buildNumber"` |
| `env` | Environment variables | Object of key-value pairs |
| `extends` | Inherit from another profile | Profile name string |

### iOS-Specific Properties

| Property | Description |
|----------|-------------|
| `simulator` | Build for simulator (no signing) |
| `enterpriseProvisioning` | Use enterprise distribution |
| `resourceClass` | Build machine size (`default`, `large`) |

### Android-Specific Properties

| Property | Description |
|----------|-------------|
| `buildType` | Output format (`apk` or `app-bundle`) |
| `gradleCommand` | Custom Gradle command |
| `withoutCredentials` | Skip signing (simulator/dev) |

## Credentials Management

### iOS Credentials

EAS manages three primary iOS credentials:

1. **Distribution Certificate** - Signs your app for distribution
2. **Provisioning Profile** - Links app, certificate, and devices
3. **Push Notification Key** - For APNs (Apple Push Notification service)

```bash
# Interactive credential management
eas credentials --platform ios

# Options available:
# - Distribution Certificate: Manage your distribution certificate
# - Provisioning Profile: Manage your provisioning profile
# - Push Notifications: Manage your Apple Push Notifications Key
```

**Let EAS manage credentials (recommended):**
- EAS automatically creates and manages credentials
- Certificates stored securely on Expo servers
- Automatic renewal before expiration

**Use local credentials:**
Create `credentials.json` at project root:
```json
{
  "ios": {
    "provisioningProfilePath": "ios/certs/profile.mobileprovision",
    "distributionCertificate": {
      "path": "ios/certs/dist-cert.p12",
      "password": "certificate-password"
    }
  }
}
```

### Android Credentials

Android requires a keystore for signing:

```bash
# Interactive credential management
eas credentials --platform android

# Options available:
# - Keystore: Manage your keystore
# - FCM API Key: Manage your Firebase Cloud Messaging key
```

**Local keystore configuration:**
```json
{
  "android": {
    "keystore": {
      "keystorePath": "android/keystores/release.keystore",
      "keystorePassword": "keystore-password",
      "keyAlias": "key-alias",
      "keyPassword": "key-password"
    }
  }
}
```

### Push Notification Setup

**iOS (APNs):**
```bash
eas credentials --platform ios
# Select: Push Notifications: Manage your Apple Push Notifications Key
# Follow prompts to create or upload key
```

**Android (FCM):**
```bash
eas credentials --platform android
# Select: FCM API Key: Manage your Firebase Cloud Messaging key
# Provide your FCM server key from Firebase Console
```

## Build Submission (`--auto-submit`)

### iOS Submission Behaviour

iOS builds can auto-submit to App Store Connect / TestFlight:

```bash
# Auto-submit to TestFlight
eas build --platform ios --profile production --auto-submit
```

**What happens:**
1. Build completes on EAS servers
2. Build automatically uploaded to App Store Connect
3. Appears in TestFlight for testing
4. Still requires manual submission to App Review

**Requirements:**
- Valid Apple Developer account
- `appleId` and `ascAppId` in submit profile
- App created in App Store Connect

### Android Submission Behaviour

**IMPORTANT: First submission must be manual**

For a brand new app on Google Play:
1. Build the AAB: `eas build --platform android --profile production`
2. Download the AAB from EAS dashboard
3. Manually upload to Google Play Console
4. Complete store listing, content rating, etc.
5. After first release, `--auto-submit` works

```bash
# After first manual upload, auto-submit works
eas build --platform android --profile production --auto-submit
```

**Submit profile for Android:**
```json
{
  "submit": {
    "production": {
      "android": {
        "serviceAccountKeyPath": "./play-store-credentials.json",
        "track": "internal"
      }
    }
  }
}
```

**Track options:**
- `internal` - Internal testing (up to 100 testers, instant)
- `alpha` - Closed testing
- `beta` - Open testing
- `production` - Production release

### Service Account Setup (Android)

1. Go to Google Cloud Console
2. Create service account with "Service Account User" role
3. Go to Play Console → Setup → API access
4. Link the service account
5. Grant "Release manager" permissions
6. Download JSON key file
7. Reference in `eas.json`

## Development Build Setup

### For Physical Devices

```bash
# iOS device (requires Apple Developer account)
eas build --platform ios --profile development

# Android device
eas build --platform android --profile development
```

**iOS device registration:**
- Devices must be registered in Apple Developer Portal
- EAS can register devices automatically during build
- Or register manually: `eas device:create`

### For Simulators/Emulators

```bash
# iOS Simulator
eas build --platform ios --profile development-simulator

# Android Emulator (APK works on emulators)
eas build --platform android --profile development
```

## Environment Variables

### Build-time Variables

Set in `eas.json` per profile:
```json
{
  "build": {
    "production": {
      "env": {
        "APP_VARIANT": "production",
        "API_URL": "https://api.example.com"
      }
    }
  }
}
```

### Sensitive Variables (Secrets)

Use EAS Secrets for sensitive data:
```bash
# Set a secret
eas secret:create --name API_KEY --value "secret-value" --scope project

# List secrets
eas secret:list

# Secrets available as env vars during build
```

### Client-side Variables

Use `EXPO_PUBLIC_` prefix for variables accessible in app code:
```json
{
  "env": {
    "EXPO_PUBLIC_API_URL": "https://api.example.com"
  }
}
```

Access in code:
```javascript
const apiUrl = process.env.EXPO_PUBLIC_API_URL;
```

## Common Issues & Solutions

### Build Fails: Missing Credentials

**Symptom:** Build fails with credential errors

**Solution:**
```bash
# Check credential status
eas credentials --platform [ios|android]

# Regenerate if needed
# For iOS: Create new distribution certificate
# For Android: Create new keystore (WARNING: loses update capability)
```

### Build Fails: CocoaPods Error (iOS)

**Symptom:** `pod install` fails during build

**Solutions:**
1. Clear cache: `eas build --clear-cache`
2. Check `Podfile` compatibility
3. Verify native dependencies support current iOS version

### Build Fails: Gradle Error (Android)

**Symptom:** Gradle build fails

**Solutions:**
1. Check `build.gradle` configuration
2. Verify Java version compatibility
3. Check for conflicting native dependencies

### Submission Fails: App Not Found

**Symptom:** Submit fails with "app not found" error

**iOS Solution:**
- Verify `ascAppId` matches App Store Connect
- Ensure app is created in App Store Connect first

**Android Solution:**
- Ensure app is created in Play Console first
- First build must be manually uploaded
- Service account has correct permissions

### Version Mismatch

**Symptom:** Build rejected due to version already exists

**Solution:**
Use `autoIncrement` in production profile:
```json
{
  "production": {
    "autoIncrement": true
  }
}
```

Or increment manually:
```bash
eas build:version:set --platform [ios|android]
```

## Best Practices

1. **Use profile inheritance** - Define common settings in `base` profile
2. **Separate environments** - Use different bundle IDs for dev/preview/production
3. **Let EAS manage credentials** - Simpler and more secure
4. **Use secrets for sensitive data** - Never commit API keys to repo
5. **Enable autoIncrement** - Prevents version conflicts in production
6. **Test with preview builds** - Catch issues before production
7. **Use internal track for Android** - Faster distribution than other tracks

## CLI Quick Reference

```bash
# Builds
eas build --platform [ios|android|all] --profile [profile]
eas build:list
eas build:view [build-id]
eas build:cancel [build-id]

# Credentials
eas credentials --platform [ios|android]
eas credentials:configure-build

# Submissions
eas submit --platform [ios|android] --profile [profile]
eas submit --platform ios --latest
eas submit --platform android --id [build-id]

# Secrets
eas secret:create --name NAME --value VALUE
eas secret:list
eas secret:delete NAME

# Devices (iOS)
eas device:create
eas device:list

# Updates
eas update --branch [branch] --message "Update message"
eas update:list
```

## Further Resources

- Use Context7 to query latest EAS documentation
- Run `eas [command] --help` for detailed command options
- Check EAS dashboard for build logs and status
