# Fastlane Screenshot Automation

This skill provides expert knowledge on using Fastlane for automated screenshot generation and app store deployment for iOS and Android apps.

## When to Use

- Setting up Fastlane for screenshot automation
- Configuring `fastlane snapshot` for iOS simulators
- Configuring `fastlane screengrab` for Android emulators
- Managing device/simulator configurations for different screen sizes
- Generating localised screenshots
- Automating app store submissions

## Fastlane Overview

Fastlane is a suite of tools for automating mobile app development workflows:

| Tool | Platform | Purpose |
|------|----------|---------|
| `snapshot` | iOS | Automated screenshots |
| `screengrab` | Android | Automated screenshots |
| `frameit` | Both | Add device frames to screenshots |
| `deliver` | iOS | Upload to App Store Connect |
| `supply` | Android | Upload to Play Console |
| `match` | iOS | Code signing management |
| `pilot` | iOS | TestFlight management |

## Installation

### Via Homebrew (Recommended)

```bash
brew install fastlane
```

### Via RubyGems

```bash
sudo gem install fastlane -NV
```

### Verify Installation

```bash
fastlane --version
```

## Project Setup

### Initialise Fastlane

```bash
# In your project root
fastlane init
```

This creates:
- `fastlane/Fastfile` - Lane definitions
- `fastlane/Appfile` - App configuration

### Directory Structure

```
your-project/
├── ios/
│   └── fastlane/
│       ├── Fastfile
│       ├── Appfile
│       ├── Snapfile
│       └── screenshots/
├── android/
│   └── fastlane/
│       ├── Fastfile
│       ├── Appfile
│       ├── Screengrabfile
│       └── screenshots/
└── ...
```

## iOS Screenshot Automation (Snapshot)

### Initialise Snapshot

```bash
cd ios
fastlane snapshot init
```

Creates:
- `fastlane/Snapfile` - Snapshot configuration
- `fastlane/SnapshotHelper.swift` - UI test helper

### Snapfile Configuration

```ruby
# fastlane/Snapfile

# Simulators to use for screenshots
devices([
  "iPhone 15 Pro Max",    # 6.7" display
  "iPhone 14 Plus",       # 6.5" display
  "iPhone 8 Plus",        # 5.5" display
  "iPad Pro (12.9-inch) (6th generation)"  # If iPad support
])

# Languages for localised screenshots
languages([
  "en-GB",
  "en-US",
  "de-DE",
  "fr-FR",
  "ja",
  "zh-Hans"
])

# Output directory
output_directory("./fastlane/screenshots")

# Clear previous screenshots
clear_previous_screenshots(true)

# Xcode scheme (must have UI tests)
scheme("YourAppUITests")

# Workspace (for CocoaPods projects)
workspace("../YourApp.xcworkspace")

# Or project (if not using CocoaPods)
# project("../YourApp.xcodeproj")

# Override status bar (clean screenshots)
override_status_bar(true)

# Launch arguments (optional)
launch_arguments(["-screenshots"])

# Number of concurrent simulators
concurrent_simulators(true)

# Stop on first error
stop_after_first_error(false)

# Skip opening HTML summary
skip_open_summary(false)
```

### SnapshotHelper Setup

1. Add `SnapshotHelper.swift` to your UI Test target
2. Import in your test file
3. Call `setupSnapshot(app)` in setUp

### UI Test for Screenshots

Create `YourAppUITests/ScreenshotTests.swift`:

```swift
import XCTest

class ScreenshotTests: XCTestCase {

    var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        setupSnapshot(app)
        app.launch()
    }

    override func tearDownWithError() throws {
        app = nil
    }

    func testTakeScreenshots() throws {
        // Wait for app to load
        sleep(2)

        // 1. Home Screen
        snapshot("01_HomeScreen")

        // 2. Navigate to feature
        app.buttons["FeatureButton"].tap()
        sleep(1)
        snapshot("02_FeatureScreen")

        // 3. Navigate to another screen
        app.tabBars.buttons["Settings"].tap()
        sleep(1)
        snapshot("03_SettingsScreen")

        // 4. Show detail view
        app.cells["ProfileCell"].tap()
        sleep(1)
        snapshot("04_ProfileScreen")

        // 5. Dark mode (if applicable)
        // Toggle dark mode and capture
        snapshot("05_DarkModeScreen")
    }
}
```

### Running Snapshot

```bash
cd ios

# Run all screenshots
fastlane snapshot

# Specific device only
fastlane snapshot --devices "iPhone 15 Pro Max"

# Specific language only
fastlane snapshot --languages "en-GB"

# Skip building (if already built)
fastlane snapshot --skip_build
```

## Android Screenshot Automation (Screengrab)

### Initialise Screengrab

```bash
cd android
fastlane screengrab init
```

Creates:
- `fastlane/Screengrabfile` - Configuration

### Screengrabfile Configuration

```ruby
# fastlane/Screengrabfile

# Package name
app_package_name("com.yourcompany.yourapp")

# Test instrumentation runner
use_tests_in_classes(["com.yourcompany.yourapp.ScreenshotTests"])

# APK paths
app_apk_path("app/build/outputs/apk/debug/app-debug.apk")
tests_apk_path("app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")

# Locales for screenshots
locales([
  "en-US",
  "en-GB",
  "de-DE",
  "fr-FR",
  "ja-JP",
  "zh-CN"
])

# Output directory
output_directory("./fastlane/screenshots")

# Clear previous screenshots
clear_previous_screenshots(true)

# Exit on test failure
exit_on_test_failure(false)

# ADB path (optional)
# adb_path("/usr/local/bin/adb")

# Ending locale
ending_locale("en-US")

# Specific device serial (optional)
# specific_device("emulator-5554")

# Skip open summary
skip_open_summary(false)
```

### Add Screengrab Dependency

In `app/build.gradle`:

```groovy
dependencies {
    // ...
    androidTestImplementation 'tools.fastlane:screengrab:2.1.1'
}
```

### Instrumented Test for Screenshots

Create `app/src/androidTest/java/com/yourcompany/yourapp/ScreenshotTests.kt`:

```kotlin
package com.yourcompany.yourapp

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy
import tools.fastlane.screengrab.locale.LocaleTestRule

@RunWith(AndroidJUnit4::class)
class ScreenshotTests {

    companion object {
        @get:ClassRule
        @JvmStatic
        val localeTestRule = LocaleTestRule()
    }

    @Before
    fun setUp() {
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())
    }

    @Test
    fun takeScreenshots() {
        ActivityScenario.launch(MainActivity::class.java)

        // Wait for app to load
        Thread.sleep(2000)

        // 1. Home Screen
        Screengrab.screenshot("01_HomeScreen")

        // 2. Navigate to feature
        onView(withId(R.id.feature_button)).perform(click())
        Thread.sleep(1000)
        Screengrab.screenshot("02_FeatureScreen")

        // 3. Navigate to settings
        onView(withId(R.id.settings_tab)).perform(click())
        Thread.sleep(1000)
        Screengrab.screenshot("03_SettingsScreen")

        // 4. Show profile
        onView(withText("Profile")).perform(click())
        Thread.sleep(1000)
        Screengrab.screenshot("04_ProfileScreen")
    }
}
```

### Running Screengrab

```bash
cd android

# Build APKs first
./gradlew assembleDebug assembleAndroidTest

# Run screenshots
fastlane screengrab

# Specific locale
fastlane screengrab --locales "en-GB"
```

## Framing Screenshots (Frameit)

Add device frames and marketing text to screenshots.

### Basic Usage

```bash
# In screenshots directory
fastlane frameit
```

### Configuration (frameit.json)

Create `fastlane/screenshots/frameit.json`:

```json
{
  "default": {
    "title": {
      "font": "./fonts/Helvetica-Bold.ttf",
      "font_size": 128,
      "color": "#000000"
    },
    "background": "#FFFFFF",
    "padding": 50,
    "show_complete_frame": true,
    "stack_title": false
  },
  "data": [
    {
      "filter": "01_HomeScreen",
      "title": {
        "text": "Manage your tasks effortlessly"
      }
    },
    {
      "filter": "02_FeatureScreen",
      "title": {
        "text": "Powerful features at your fingertips"
      }
    },
    {
      "filter": "03_SettingsScreen",
      "title": {
        "text": "Customise to your needs"
      }
    },
    {
      "filter": "04_ProfileScreen",
      "title": {
        "text": "Your profile, your way"
      }
    }
  ]
}
```

### Frameit Options

| Option | Description |
|--------|-------------|
| `silver` | Use silver device frame |
| `rose_gold` | Use rose gold frame |
| `force_device_type` | Force specific device |
| `use_platform` | Use platform-specific frames |

```bash
# Silver frames
fastlane frameit silver

# Force iPhone 14 Pro frame
fastlane frameit --force_device_type "iPhone 14 Pro"
```

## Fastfile Lanes

Define reusable workflows in `Fastfile`:

### iOS Fastfile

```ruby
# ios/fastlane/Fastfile

default_platform(:ios)

platform :ios do
  desc "Capture screenshots"
  lane :screenshots do
    snapshot
    frameit(white: true)
  end

  desc "Upload screenshots to App Store Connect"
  lane :upload_screenshots do
    deliver(
      skip_binary_upload: true,
      skip_metadata: true,
      screenshots_path: "./fastlane/screenshots"
    )
  end

  desc "Full screenshot workflow"
  lane :refresh_screenshots do
    screenshots
    upload_screenshots
  end
end
```

### Android Fastfile

```ruby
# android/fastlane/Fastfile

default_platform(:android)

platform :android do
  desc "Build debug APKs"
  lane :build_for_screenshots do
    gradle(
      task: "assemble",
      build_type: "Debug"
    )
    gradle(
      task: "assemble",
      build_type: "AndroidTest"
    )
  end

  desc "Capture screenshots"
  lane :screenshots do
    build_for_screenshots
    screengrab
  end

  desc "Upload screenshots to Play Console"
  lane :upload_screenshots do
    supply(
      skip_upload_apk: true,
      skip_upload_aab: true,
      skip_upload_metadata: true,
      skip_upload_changelogs: true,
      images_path: "./fastlane/screenshots"
    )
  end
end
```

### Running Lanes

```bash
# iOS
cd ios
fastlane screenshots
fastlane upload_screenshots

# Android
cd android
fastlane screenshots
fastlane upload_screenshots
```

## Required Screenshot Sizes

### iOS (App Store Connect)

| Display Size | Resolution | Device |
|--------------|------------|--------|
| 6.7" | 1290 x 2796 | iPhone 15 Pro Max |
| 6.5" | 1284 x 2778 | iPhone 14 Plus |
| 5.5" | 1242 x 2208 | iPhone 8 Plus |
| 12.9" iPad | 2048 x 2732 | iPad Pro |

**Note:** App Store Connect can auto-scale from 6.7" to smaller sizes.

### Android (Play Console)

| Type | Minimum | Recommended |
|------|---------|-------------|
| Phone | 320px min side | 1080 x 1920 |
| 7" Tablet | 1024 x 500 | 1200 x 1920 |
| 10" Tablet | 1024 x 500 | 1600 x 2560 |

## Localisation

### Setting Up Localised Screenshots

1. Configure languages in Snapfile/Screengrabfile
2. Use localised strings in your app
3. Capture screenshots for each locale

### iOS Localisation in Tests

```swift
func testTakeLocalizedScreenshots() throws {
    // The locale is set automatically by snapshot
    // Just ensure your app uses NSLocalizedString

    snapshot("01_HomeScreen")
    // Screenshots saved to: en-GB/01_HomeScreen.png, de-DE/01_HomeScreen.png, etc.
}
```

### Android Localisation in Tests

```kotlin
@Test
fun takeLocalizedScreenshots() {
    // LocaleTestRule handles locale switching
    // Just ensure your app uses string resources

    Screengrab.screenshot("01_HomeScreen")
    // Screenshots saved to: en-US/01_HomeScreen.png, de-DE/01_HomeScreen.png, etc.
}
```

## Troubleshooting

### Common iOS Issues

**Simulator not found:**
```bash
# List available simulators
xcrun simctl list devices

# Check exact device name in Snapfile
devices(["iPhone 15 Pro Max"])  # Must match exactly
```

**UI test target issues:**
- Ensure SnapshotHelper.swift is in UI Test target
- Check scheme includes UI Test target
- Verify setupSnapshot(app) is called

**Status bar not overridden:**
```ruby
# In Snapfile
override_status_bar(true)
```

### Common Android Issues

**APK not found:**
```bash
# Build APKs first
./gradlew assembleDebug assembleAndroidTest

# Check paths in Screengrabfile match actual output
```

**Emulator connection issues:**
```bash
# Check emulator is running
adb devices

# Start emulator if needed
emulator -avd Pixel_6_API_34
```

**Permission issues:**
```kotlin
// Add to test class
@get:Rule
val grantPermissionRule: GrantPermissionRule =
    GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE)
```

### General Issues

**Screenshots too slow:**
- Use `concurrent_simulators(true)` (iOS)
- Run fewer devices at once
- Use `skip_build` if already built

**Memory issues:**
```bash
# Limit concurrent simulators
# In Snapfile
number_of_retries(3)
concurrent_simulators(false)
```

## Best Practices

### Screenshot Quality

1. **Use realistic data** - Not Lorem ipsum
2. **Clean status bar** - Use override_status_bar
3. **Consistent state** - Reset app between captures
4. **Wait for animations** - Add appropriate sleeps/waits
5. **Test edge cases** - Empty states, full lists, etc.

### Organisation

1. **Name screenshots descriptively** - `01_Home`, `02_Feature`
2. **Use consistent numbering** - Ensures correct order
3. **Group by feature** - Logical organisation
4. **Document test scenarios** - What each screenshot shows

### Performance

1. **Build once, capture many** - Use `skip_build`
2. **Parallel capture** - `concurrent_simulators(true)`
3. **Target specific devices** - When debugging
4. **CI integration** - Automate in pipeline

## Quick Reference

```bash
# iOS
cd ios
fastlane snapshot init          # First time setup
fastlane snapshot               # Capture screenshots
fastlane frameit               # Add device frames
fastlane deliver screenshots   # Upload to ASC

# Android
cd android
fastlane screengrab init       # First time setup
./gradlew assembleDebug assembleAndroidTest
fastlane screengrab            # Capture screenshots
fastlane supply images         # Upload to Play Console

# Specific options
fastlane snapshot --devices "iPhone 15 Pro Max"
fastlane snapshot --languages "en-GB,de-DE"
fastlane snapshot --skip_build
fastlane screengrab --locales "en-US"
```

## Integration with EAS/Expo

For Expo projects:

1. Run `npx expo prebuild` to generate native projects
2. Configure Fastlane in `ios/` and `android/` directories
3. Run screenshot automation as normal
4. Native projects are regenerated on prebuild, so:
   - Keep Fastfile in project root, copy on prebuild
   - Or add to `.gitignore` and regenerate as needed

```bash
# In package.json scripts
{
  "screenshots:ios": "cd ios && fastlane snapshot",
  "screenshots:android": "cd android && fastlane screengrab"
}
```
