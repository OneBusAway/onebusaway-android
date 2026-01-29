# Rebranding OneBusAway Android for Your City

There are two ways to deploy OneBusAway Android in your city:

1. **Join the OneBusAway [multi-region project](https://github.com/OneBusAway/onebusaway/wiki/Multi-Region)** - The easiest way to get started - simply set up your own OneBusAway server with your own transit data, and get added to *all* the OneBusAway apps!  See [this page](https://github.com/OneBusAway/onebusaway/wiki/Multi-Region) for details.
2. **Deploy a rebranded version of OneBusAway Android as your own app on Google Play** - Requires a bit more maintenance, but it allows you to set up your own app on Google Play based on the OneBusAway Android source code, and with your brand name and colors.  This page discusses this option in detail.

## Rebranding Using Gradle Build Flavors

We use [Gradle build flavors](http://developer.android.com/tools/building/configuring-gradle.html#workBuildVariants) to enable a number of different build variants of OneBusAway Android.

We have one Gradle "platform" flavor dimension:

* **google** = Normal Google Play release

...and several Gradle "brand" flavor dimensions:

* **oba** = Original OneBusAway brand
* **agencyX** = A sample rebranded version of OneBusAway for a fictitious "Agency X"
* **agencyY** = A sample rebranded version of OneBusAway for a fictitious "Agency Y"
* **kiedybus** = KiedyBus, a Polish transit app

Here's where you can download the apps for each of these brands on Google Play:

* [OneBusAway](https://play.google.com/store/apps/details?id=com.joulespersecond.seattlebusbot)
* [Agency X](https://play.google.com/store/apps/details?id=org.agencyx.android)
* [Agency Y](https://play.google.com/store/apps/details?id=org.agencyy.android)

And here are screenshots for these 3 brands:

<img src="https://cloud.githubusercontent.com/assets/928045/23876835/a6ceb718-0815-11e7-866a-5daef01d0a08.png" width="496" height="281" align=center />

Each brand is deployed as an independent app on Google Play (using the **google** platform flavor).

To build a variant, you need to combine the platform flavor with the brand flavor.  For example, the original OneBusAway brand for the Google platform can be built with:

`gradlew installObaGoogleDebug`

## Creating a New Brand

Brand flavors are defined in separate files in the `onebusaway-android/flavors/` directory. This keeps the main `build.gradle` clean and makes it easy to add new brands.

First, we recommend that you review the sample brands (`agencyX`, `agencyY`, `kiedybus`) to see how brands are implemented.

Here are the high-level steps to add a new brand, for a new brand name `newBrandName`:

1. Create a new flavor configuration file `onebusaway-android/flavors/newBrandName.gradle`
2. Create a new folder `src/newBrandName/res` with the appropriate resource files (or copy from one of the samples)
3. Edit resource files in `src/newBrandName/res` subfolders with your brand information
4. Add your own launcher icons for the `res/mipmap-*` folders
5. Configure Google Maps API key for your package name
6. Configure Firebase for analytics and crash reporting (optional but recommended)

### Step 1: Create Flavor Configuration

Copy an existing flavor file as a template:

```bash
cp onebusaway-android/flavors/agencyX.gradle onebusaway-android/flavors/newBrandName.gradle
```

Edit the file and update:

```groovy
android.productFlavors {
    newBrandName {
        dimension "brand"
        applicationId "com.newbrandname.android"  // Your unique package name
        manifestPlaceholders = [databaseAuthority: applicationId.toString() + '.provider']
        buildConfigField "int", "ARRIVAL_INFO_STYLE", "0"
        buildConfigField "boolean", "USE_FIXED_REGION", "false"  // or "true" for single-region
        // ... other configuration options
    }
}
```

See `flavors/README.md` for the complete list of configuration options.

### Step 2: Create Resource Directory

Create minimal resources in `src/newBrandName/res/`:

```
src/newBrandName/
└── res/
    ├── values/
    │   ├── strings.xml          # App name (required)
    │   ├── colors.xml           # Theme colors (required)
    │   └── do_not_translate.xml # API keys, URLs (required)
    ├── values-es/               # Optional: localized app name
    │   └── strings.xml
    └── mipmap-*/
        ├── ic_launcher.png
        └── ic_launcher_round.png
```

### Step 3: Configure Resources

#### strings.xml

Thanks to the placeholder system, you typically only need to override `app_name`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">New Brand Name</string>
</resources>
```

All other branded strings use `%1$s` placeholders that automatically substitute your app name.

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
    <!-- Keep on-time color green even if your theme isn't green -->
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

### Step 4: Generate Launcher Icons

Use one of these tools to generate icons for all densities:
- [Android Asset Studio](http://romannurik.github.io/AndroidAssetStudio/icons-launcher.html)
- [Image Asset Studio](https://developer.android.com/studio/write/image-asset-studio.html) in Android Studio

Required sizes:
- mdpi: 48x48
- hdpi: 72x72
- xhdpi: 96x96
- xxhdpi: 144x144
- xxxhdpi: 192x192

### Step 5: Configure Google Maps API

Google Maps requires an API key that is restricted to your app's package name.

1. Go to the [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the **Maps SDK for Android** API
4. Go to **Credentials** and create an API key
5. Restrict the key:
   - Click on the API key to edit it
   - Under "Application restrictions", select **Android apps**
   - Add your package name (e.g., `com.newbrandname.android`)
   - Add your app's SHA-1 fingerprint:
     ```bash
     # For debug builds:
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android

     # For release builds, use your release keystore
     ```
6. Copy the API key to `src/newBrandName/res/values/do_not_translate.xml`

**Important:** You need separate SHA-1 fingerprints for debug and release builds. For production, add both fingerprints to your API key restrictions.

See [Google's documentation](https://developers.google.com/maps/documentation/android-sdk/get-api-key) for more details.

### Step 6: Configure Firebase (Optional but Recommended)

Firebase provides analytics and crash reporting. To set up Firebase for your brand:

1. Go to the [Firebase Console](https://console.firebase.google.com/)
2. Create a new project or select an existing one
3. Click **Add app** and select Android
4. Enter your package name (e.g., `com.newbrandname.android`)
5. Download the `google-services.json` file
6. **Important:** Don't replace the existing `google-services.json`. Instead, add your app's client configuration to the existing file.

#### Adding Your App to google-services.json

Open `onebusaway-android/google-services.json` and add your app's client entry to the `client` array:

```json
{
  "project_info": { ... },
  "client": [
    // ... existing entries ...
    {
      "client_info": {
        "mobilesdk_app_id": "1:YOUR_PROJECT_NUMBER:android:YOUR_APP_ID",
        "android_client_info": {
          "package_name": "com.newbrandname.android"
        }
      },
      "oauth_client": [
        {
          "client_id": "YOUR_CLIENT_ID.apps.googleusercontent.com",
          "client_type": 3
        }
      ],
      "api_key": [
        {
          "current_key": "YOUR_FIREBASE_API_KEY"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": [
            {
              "client_id": "YOUR_CLIENT_ID.apps.googleusercontent.com",
              "client_type": 3
            }
          ]
        }
      }
    }
  ]
}
```

You can find these values in the `google-services.json` file you downloaded from Firebase Console.

**Note:** If you don't configure Firebase, the app will still build and run, but you won't have analytics or crash reporting for your brand.

### Step 7: Build and Test

```bash
./gradlew assembleNewBrandNameGoogleDebug
./gradlew installNewBrandNameGoogleDebug
```

## Simplified String Localization with Placeholders

The app uses a **placeholder system** for branded strings, which significantly reduces the work needed to support multiple languages in your rebranded app.

**How it works:** Strings that mention the app name use `%1$s` as a placeholder, which is automatically replaced with your `app_name` at runtime. For example:

```xml
<!-- In src/main/res/values/strings.xml -->
<string name="tutorial_welcome_title">Welcome to %1$s!</string>
<string name="bad_gateway_error">%1$s\'s servers are overloaded. Please try again.</string>
```

**What this means for you:** You only need to override `app_name` in each language your brand supports. All other branded strings will automatically use your app name.

```xml
<!-- src/newBrandName/res/values/strings.xml -->
<resources>
    <string name="app_name">New Brand Name</string>
</resources>

<!-- src/newBrandName/res/values-es/strings.xml -->
<resources>
    <string name="app_name">New Brand Name</string>
</resources>
```

**Optional overrides:** You may still need to override specific strings if your agency has different UI elements. For example, if your agency uses red stop markers instead of green:

```xml
<!-- src/newBrandName/res/values/strings.xml -->
<resources>
    <string name="app_name">New Brand Name</string>
    <!-- Override because our stop dots are red, not green -->
    <string name="tutorial_welcome_text">Tap on a stop (red dot on the map) to see arrival times.</string>
</resources>
```

See the `agencyX`, `agencyY`, and `kiedybus` sample flavors for working examples of this pattern.

## Configuration Options

We provide configuration options in the flavor `.gradle` files that allow you to choose default behaviors for your brand.

### Arrival Information Style

`ARRIVAL_INFO_STYLE` - Valid values are `0` and `1`:

* `0` (Style A) - The original OneBusAway presentation with small rows sorted by estimated arrival time
* `1` (Style B) - Groups arrival times by route and shows scheduled arrival times

Users can change the sorting style using the "Sort by" button regardless of the default.

### Fixed vs. Multi-region

`USE_FIXED_REGION` - Valid values are `true` and `false`:

* `false` - The app works across various regions defined in the Regions API (recommended for most brands)
* `true` - The app is fixed to the region information provided in the flavor configuration

### Geocoding Provider

`USE_PELIAS_GEOCODING` - Controls which service is used for searching origins and destinations in trip planning:

* `true` - Uses the [Pelias geocoder](https://github.com/pelias/pelias) (configured for [geocode.earth](https://geocode.earth/) by default). Requires a Pelias API key in `gradle.properties`:
  ```
  Pelias_newBrandName=YOUR_API_KEY
  ```
* `false` - Uses the [Google Places SDK](https://developers.google.com/places/android-sdk/intro). Requires a Google Maps Platform billing account.

## Examples

See the following flavor files for complete examples:

* `flavors/agencyX.gradle` - Multi-region brand with Style A arrivals
* `flavors/agencyY.gradle` - Fixed-region brand with Style B arrivals
* `flavors/kiedybus.gradle` - Multi-region brand with custom regions API

## Acknowledgements

When launching a rebranded version of OneBusAway, acknowledging that your app is based on the hard work of those contributing to the OneBusAway project is certainly appreciated.  However, please do not imply that the OneBusAway project or its contributors endorse the rebranded app, and please do not use the OneBusAway logo or color scheme in your rebranded app.
