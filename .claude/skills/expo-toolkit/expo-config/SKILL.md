# Expo Configuration

This skill provides expert knowledge on Expo project configuration, environment management, and the `expo` CLI.

## When to Use

- Configuring `app.config.js` (dynamic configuration)
- Setting up environment-specific bundle IDs (dev, preview, production)
- Running `expo doctor` for project health checks
- Managing environment variables and build-time configuration
- Understanding Expo SDK versions and upgrades
- Configuring app metadata (name, icon, splash, etc.)

## app.config.js vs app.json

**Use `app.config.js` (recommended):**
- Dynamic configuration based on environment
- Conditional logic for different build variants
- Access to environment variables
- Can import from other files

**Use `app.json`:**
- Simple static configuration
- No environment-specific needs
- Quick prototyping

### Converting app.json to app.config.js

If you have an existing `app.json`:

```javascript
// app.config.js
export default ({ config }) => {
  return {
    ...config,
    // Your customizations here
  };
};
```

Or start fresh:

```javascript
// app.config.js
export default {
  name: "My App",
  slug: "my-app",
  // ... rest of config
};
```

## Environment-Specific Bundle IDs

The recommended pattern for running dev/preview/production builds side-by-side on the same device:

```javascript
// app.config.js
const IS_DEV = process.env.APP_VARIANT === 'development';
const IS_PREVIEW = process.env.APP_VARIANT === 'preview';

const getUniqueIdentifier = () => {
  if (IS_DEV) {
    return 'com.yourcompany.yourapp.dev';
  }
  if (IS_PREVIEW) {
    return 'com.yourcompany.yourapp.preview';
  }
  return 'com.yourcompany.yourapp';
};

const getAppName = () => {
  if (IS_DEV) {
    return 'YourApp (Dev)';
  }
  if (IS_PREVIEW) {
    return 'YourApp (Preview)';
  }
  return 'YourApp';
};

export default {
  name: getAppName(),
  slug: 'your-app',
  version: '1.0.0',
  orientation: 'portrait',
  icon: './assets/icon.png',
  userInterfaceStyle: 'automatic',
  splash: {
    image: './assets/splash.png',
    resizeMode: 'contain',
    backgroundColor: '#ffffff',
  },
  assetBundlePatterns: ['**/*'],
  ios: {
    supportsTablet: true,
    bundleIdentifier: getUniqueIdentifier(),
  },
  android: {
    adaptiveIcon: {
      foregroundImage: './assets/adaptive-icon.png',
      backgroundColor: '#ffffff',
    },
    package: getUniqueIdentifier(),
  },
  web: {
    favicon: './assets/favicon.png',
  },
  extra: {
    eas: {
      projectId: 'your-project-id',
    },
  },
};
```

### Corresponding eas.json

```json
{
  "build": {
    "development": {
      "developmentClient": true,
      "distribution": "internal",
      "env": {
        "APP_VARIANT": "development"
      }
    },
    "preview": {
      "distribution": "internal",
      "env": {
        "APP_VARIANT": "preview"
      }
    },
    "production": {
      "env": {
        "APP_VARIANT": "production"
      }
    }
  }
}
```

## Environment Variables

### Build-time vs Runtime Variables

| Type | Prefix | Available | Use Case |
|------|--------|-----------|----------|
| Build-time | None | `app.config.js` only | Bundle ID, app name |
| Runtime | `EXPO_PUBLIC_` | App code | API URLs, feature flags |

### Build-time Variables

Used in `app.config.js` during build:

```javascript
// app.config.js
const API_URL = process.env.API_URL || 'https://api.default.com';

export default {
  // ...
  extra: {
    apiUrl: API_URL,
  },
};
```

Set in `eas.json`:
```json
{
  "build": {
    "production": {
      "env": {
        "API_URL": "https://api.production.com"
      }
    }
  }
}
```

### Runtime Variables (Client-side)

Variables accessible in your app code:

```javascript
// In eas.json
{
  "build": {
    "production": {
      "env": {
        "EXPO_PUBLIC_API_URL": "https://api.example.com"
      }
    }
  }
}

// In your app code
const apiUrl = process.env.EXPO_PUBLIC_API_URL;
```

**IMPORTANT:** `EXPO_PUBLIC_` variables are embedded in the JS bundle. Never use for secrets!

### Local Development (.env)

For local development, use `.env` files:

```bash
# .env.local (gitignored)
EXPO_PUBLIC_API_URL=http://localhost:3000
```

Install `expo-env`:
```bash
npx expo install expo-env
```

## Expo Doctor

Run diagnostics to check project health:

```bash
npx expo doctor
```

### Common Issues Detected

| Issue | Solution |
|-------|----------|
| SDK version mismatch | Update packages: `npx expo install --fix` |
| Deprecated packages | Migrate to recommended alternatives |
| Invalid config | Fix `app.config.js` / `app.json` |
| Missing peer dependencies | Install missing packages |
| Native module issues | Run `npx expo prebuild --clean` |

### Fixing Common Problems

```bash
# Fix package version mismatches
npx expo install --fix

# Clear caches and reinstall
rm -rf node_modules
rm -rf .expo
npm install

# Regenerate native projects (if using prebuild)
npx expo prebuild --clean
```

## Complete app.config.js Template

```javascript
// app.config.js
const IS_DEV = process.env.APP_VARIANT === 'development';
const IS_PREVIEW = process.env.APP_VARIANT === 'preview';

const getUniqueIdentifier = () => {
  if (IS_DEV) return 'com.yourcompany.yourapp.dev';
  if (IS_PREVIEW) return 'com.yourcompany.yourapp.preview';
  return 'com.yourcompany.yourapp';
};

const getAppName = () => {
  if (IS_DEV) return 'YourApp (Dev)';
  if (IS_PREVIEW) return 'YourApp (Preview)';
  return 'YourApp';
};

export default {
  // Basic Info
  name: getAppName(),
  slug: 'your-app',
  version: '1.0.0',
  orientation: 'portrait',

  // Assets
  icon: './assets/icon.png',
  splash: {
    image: './assets/splash.png',
    resizeMode: 'contain',
    backgroundColor: '#ffffff',
  },
  assetBundlePatterns: ['**/*'],

  // Appearance
  userInterfaceStyle: 'automatic',

  // iOS Configuration
  ios: {
    supportsTablet: false, // or true for iPad support
    bundleIdentifier: getUniqueIdentifier(),
    buildNumber: '1',
    infoPlist: {
      NSCameraUsageDescription: 'This app uses the camera to...',
      NSPhotoLibraryUsageDescription: 'This app accesses photos to...',
      // Add other permissions as needed
    },
    config: {
      usesNonExemptEncryption: false, // If no custom encryption
    },
  },

  // Android Configuration
  android: {
    package: getUniqueIdentifier(),
    versionCode: 1,
    adaptiveIcon: {
      foregroundImage: './assets/adaptive-icon.png',
      backgroundColor: '#ffffff',
    },
    permissions: [
      // Add permissions as needed
      // 'android.permission.CAMERA',
      // 'android.permission.READ_EXTERNAL_STORAGE',
    ],
  },

  // Web Configuration (if applicable)
  web: {
    favicon: './assets/favicon.png',
    bundler: 'metro',
  },

  // Plugins
  plugins: [
    // Add Expo plugins here
    // 'expo-camera',
    // ['expo-image-picker', { photosPermission: '...' }],
  ],

  // Extra Configuration
  extra: {
    eas: {
      projectId: 'your-eas-project-id',
    },
    // Add custom config accessible via expo-constants
  },

  // Updates (EAS Update)
  updates: {
    url: 'https://u.expo.dev/your-project-id',
  },
  runtimeVersion: {
    policy: 'appVersion',
  },

  // Owner (for EAS)
  owner: 'your-expo-username',
};
```

## Key Configuration Fields

### App Identity

| Field | Description | Example |
|-------|-------------|---------|
| `name` | Display name | `"My App"` |
| `slug` | URL-friendly name | `"my-app"` |
| `version` | User-facing version | `"1.0.0"` |
| `ios.bundleIdentifier` | iOS bundle ID | `"com.company.app"` |
| `android.package` | Android package name | `"com.company.app"` |

### Versioning

| Field | Platform | Description |
|-------|----------|-------------|
| `version` | Both | Semantic version shown to users |
| `ios.buildNumber` | iOS | Internal build number (string) |
| `android.versionCode` | Android | Internal version code (integer) |

**Tip:** Use `autoIncrement` in EAS to manage build numbers automatically.

### Assets

| Field | Size | Format |
|-------|------|--------|
| `icon` | 1024x1024 | PNG |
| `splash.image` | 1284x2778 (or similar) | PNG |
| `android.adaptiveIcon.foregroundImage` | 1024x1024 | PNG |
| `web.favicon` | 48x48 | PNG |

### Permissions (iOS)

Add to `ios.infoPlist`:

```javascript
infoPlist: {
  NSCameraUsageDescription: 'Required for taking photos',
  NSPhotoLibraryUsageDescription: 'Required for selecting photos',
  NSLocationWhenInUseUsageDescription: 'Required for location features',
  NSMicrophoneUsageDescription: 'Required for recording audio',
  NSFaceIDUsageDescription: 'Required for secure authentication',
}
```

### Permissions (Android)

Add to `android.permissions`:

```javascript
permissions: [
  'android.permission.CAMERA',
  'android.permission.READ_EXTERNAL_STORAGE',
  'android.permission.WRITE_EXTERNAL_STORAGE',
  'android.permission.ACCESS_FINE_LOCATION',
  'android.permission.RECORD_AUDIO',
]
```

## Config Plugins

For native configuration that goes beyond standard options:

```javascript
plugins: [
  // Simple plugin
  'expo-camera',

  // Plugin with options
  ['expo-image-picker', {
    photosPermission: 'Allow access to select photos',
  }],

  // Custom plugin (local)
  './plugins/my-plugin.js',
]
```

### Common Plugins

| Plugin | Purpose |
|--------|---------|
| `expo-camera` | Camera access |
| `expo-image-picker` | Photo library access |
| `expo-notifications` | Push notifications |
| `expo-location` | Location services |
| `expo-av` | Audio/video playback |
| `expo-build-properties` | Native build settings |

## EAS Update Configuration

For over-the-air updates:

```javascript
export default {
  // ...
  updates: {
    url: 'https://u.expo.dev/your-project-id',
    fallbackToCacheTimeout: 0,
  },
  runtimeVersion: {
    policy: 'appVersion', // or 'sdkVersion', 'fingerprint', or explicit version
  },
};
```

### Runtime Version Policies

| Policy | When Updates Apply |
|--------|-------------------|
| `appVersion` | Same `version` in config |
| `sdkVersion` | Same Expo SDK version |
| `fingerprint` | Same native code fingerprint |
| `"1.0.0"` | Explicit version string |

## Best Practices

1. **Use app.config.js** - Dynamic config enables environment variants
2. **Separate bundle IDs** - Run dev/preview/production side-by-side
3. **Use APP_VARIANT pattern** - Clean separation of environments
4. **Never commit secrets** - Use EAS Secrets for sensitive data
5. **Run expo doctor regularly** - Catch issues early
6. **Keep SDK updated** - Security and compatibility
7. **Document permissions** - Explain why each permission is needed
8. **Use config plugins** - For native customisation beyond defaults

## Troubleshooting

### Config Not Updating

```bash
# Clear Metro cache
npx expo start --clear

# Clear all caches
rm -rf node_modules/.cache
rm -rf .expo
```

### Environment Variables Not Working

1. Ensure variable is set in `eas.json` for the correct profile
2. For runtime access, use `EXPO_PUBLIC_` prefix
3. Restart Metro bundler after changes

### Bundle ID Conflicts

If you get "app already installed" errors when switching environments:
- Ensure each variant has a unique bundle ID
- Uninstall the old app before installing new variant
- Check `APP_VARIANT` is set correctly in build profile

## CLI Quick Reference

```bash
# Start development server
npx expo start

# Start with clean cache
npx expo start --clear

# Run diagnostics
npx expo doctor

# Fix package versions
npx expo install --fix

# Install a package
npx expo install package-name

# Generate native projects
npx expo prebuild

# Clean and regenerate native projects
npx expo prebuild --clean

# Export for web
npx expo export --platform web

# View config
npx expo config
```
