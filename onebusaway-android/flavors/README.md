# Product Flavor Configuration

This directory contains modular Gradle configuration files for each white-label brand flavor.

## Architecture

Instead of a large `productFlavors {}` block in `build.gradle`, each brand has its own configuration file:

```
flavors/
├── README.md          # This file
├── oba.gradle         # OneBusAway (original brand)
├── agencyX.gradle     # Sample multi-region brand
├── agencyY.gradle     # Sample fixed-region brand
└── kiedybus.gradle    # KiedyBus (Poland)
```

## Adding a New White-Label Brand

### Step 1: Create Your Flavor Configuration

Copy `agencyX.gradle` (for multi-region) or `agencyY.gradle` (for fixed-region) and rename it to your brand name:

```bash
cp flavors/agencyX.gradle flavors/mybrand.gradle
```

Edit the file and update:

1. **Flavor name** - Change `agencyX {` to `mybrand {`
2. **Application ID** - Your unique package name (e.g., `com.mybrand.transit`)
3. **Pelias key reference** - Update `getPeliasKey(name)` (or use empty string)
4. **Region settings** - Set `USE_FIXED_REGION` and related fields

### Step 2: Create Resource Directory

Create minimal resources in `src/mybrand/res/`:

```
src/mybrand/
└── res/
    ├── values/
    │   ├── strings.xml          # Just app_name (required)
    │   ├── colors.xml           # Theme colors (required)
    │   └── do_not_translate.xml # API keys, URLs (required)
    └── mipmap-*/
        ├── ic_launcher.png      # App icon (all densities)
        └── ic_launcher_round.png
```

#### Minimal strings.xml (thanks to placeholder system)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">My Brand</string>
</resources>
```

That's it! All other branded strings use `%1$s` placeholders that automatically substitute your app name.

**Note:** A few strings are referenced directly in XML layout files and cannot use placeholders (the placeholder would display as literal text). These strings are handled programmatically in the Java code to support the app name substitution. You don't need to do anything special for these.

#### colors.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="theme_primary">#YOUR_PRIMARY_COLOR</color>
    <color name="theme_primary_dark">#YOUR_DARK_COLOR</color>
    <color name="theme_muted">#YOUR_MUTED_COLOR</color>
    <color name="theme_accent">#YOUR_ACCENT_COLOR</color>
    <color name="tutorial_background">#dfYOUR_PRIMARY</color>
    <color name="ic_launcher_background">#YOUR_PRIMARY_COLOR</color>
    <!-- Keep on-time arrivals green even if your theme isn't green -->
    <color name="stop_info_ontime">#4CAF50</color>
</resources>
```

#### do_not_translate.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="apiv2_key">YOUR_GOOGLE_MAPS_API_KEY</string>
    <!-- Only if using custom regions API: -->
    <string name="regions_api_url">https://your-domain.com/regions.json</string>
</resources>
```

### Step 3: Generate Launcher Icons

Use [Android Asset Studio](http://romannurik.github.io/AndroidAssetStudio/icons-launcher.html) to generate icons for all densities:
- mdpi (48x48)
- hdpi (72x72)
- xhdpi (96x96)
- xxhdpi (144x144)
- xxxhdpi (192x192)

### Step 4: Add Firebase Configuration (Optional)

Add your app's Firebase client info to `google-services.json`. See `REBRANDING.md` for detailed instructions.

**Note on API key security:** Firebase API keys in `google-services.json` are safe to commit to version control. Unlike server-side API keys, these client-side keys are designed to be embedded in apps and are protected by:
- Package name restrictions (the key only works for your specific app ID)
- SHA-1 certificate fingerprint restrictions (the key only works with your signing certificate)

The keys cannot be used by other apps or for server-side access.

### Step 5: Build

```bash
./gradlew assembleMybrandGoogleDebug
```

## Configuration Reference

### Multi-Region Configuration (like `agencyX.gradle`)

```groovy
buildConfigField "boolean", "USE_FIXED_REGION", "false"
// All FIXED_REGION_* fields should be null or placeholder values
```

### Fixed-Region Configuration (like `agencyY.gradle`)

```groovy
buildConfigField "boolean", "USE_FIXED_REGION", "true"
buildConfigField "String", "FIXED_REGION_NAME", "\"Your Region\""
buildConfigField "String", "FIXED_REGION_OBA_BASE_URL", "\"https://api.example.com/api\""
// ... configure all region bounds and features
```

## Benefits of This Architecture

1. **Isolation** - Each brand's config is self-contained
2. **Maintainability** - Easy to review PRs that add new brands
3. **Scalability** - build.gradle stays clean regardless of brand count
4. **Discoverability** - Clear pattern for new white-labelers to follow
