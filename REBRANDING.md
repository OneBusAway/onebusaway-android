# Rebranding OneBusAway Android for Your City

There are two ways to deploy OneBusAway Android in your city:

1. **Join the OneBusAway [multi-region project](https://github.com/OneBusAway/onebusaway/wiki/Multi-Region)** - The easiest way to get started - simply set up your own OneBusAway server with your own transit data, and get added to *all* the OneBusAway apps!  See [this page](https://github.com/OneBusAway/onebusaway/wiki/Multi-Region) for details.
2. **Deploy a rebranded version of OneBusAway Android as your own app on Google Play** - Requires a bit more maintenance, but it allows you to set up your own app on Google Play / Amazon App Store based on the OneBusAway Android source code, and with your brand name and colors.  This page discusses this option in detail.

## Rebranding Using Gradle Build Flavors

We use [Gradle build flavors](http://developer.android.com/tools/building/configuring-gradle.html#workBuildVariants) to enable a number of different build variants of OneBusAway Android, configured via the `build.gradle` file.

We have two Gradle "platform" flavor dimensions:

* **google** = Normal Google Play release
* **amazon** = Amazon Fire Phone release

...and three Gradle "brand" flavor dimensions:

* **oba** = Original OneBusAway brand
* **agencyX** = A sample rebranded version of OneBusAway for a fictitious "Agency X"
* **agencyY** = A sample rebranded version of OneBusAway for a fictitious "Agency Y"

Here's where you can download the apps for each of these brands on Google Play:

* [OneBusAway](https://play.google.com/store/apps/details?id=com.joulespersecond.seattlebusbot)
* [Agency X](https://play.google.com/store/apps/details?id=org.agencyx.android)
* [Agency Y](https://play.google.com/store/apps/details?id=org.agencyy.android)

And here are screenshots for these 3 brands:

<img src="https://cloud.githubusercontent.com/assets/928045/23876835/a6ceb718-0815-11e7-866a-5daef01d0a08.png" width="496" height="281" align=center />

Each of the 3 brands are deployed as an independent app on Google Play (using the **google** platform flavor) and the Amazon App Store (using the **amazon** platform flavor).

When building the project, this results in a total of 2 platforms * 3 brands = 6 core build variants.  Each of these core variants also has a debug/release build type - the end result is that you'll have 12 build variants to choose from within Android Studio or on the command line.

To build a variant, you need to combine the platform flavor with the brand flavor.  For example, the original OneBusAway brand for the Google platform can be build with:

`gradlew installObaGoogleDebug`

## Creating a New Brand

First, we recommend that you review the two sample brands, `agencyX` and `agencyY`, to see how brands are implemented.

Here are the high-level steps to add a new brand, for a new brand name `newBrandName`:

1. Add a new brand build flavor dimension to the `productFlavors { }` block in `build.gradle`
2. Create a new folder `src/newBrandName/res` with the appropriate resource files (or copy from one of the samples - `src/agencyX` or `src/agencyY`)
3. Edit resource files in `src/newBrandName/res` subfolders with your brand information (app name, etc.)
4. Add your own launcher icons for the `res/mipmap-*` folders - see the online tool at http://romannurik.github.io/AndroidAssetStudio/icons-launcher.html or [Image Asset Studio](https://developer.android.com/studio/write/image-asset-studio.html) to generate the icons from an image

For Step 1, you new entry in `build.gradle` will look something like:

    newBrandName {
        dimension "brand"  // This line is always the same for all brands - it tells Gradle that this entry is for a brand
        applicationId "com.newbrandname.android" // This is the unique identifier for the listing of this brand on Google Play (typically its your unique domain name in reverse) - you would find this app at https://play.google.com/store/apps/details?id=com.newbrandname.android
        manifestPlaceholders = [databaseAuthority: applicationId.toString() + '.provider']  // This line is always the same for all brands - it tells Gradle how to set up the database authority field for the brand
        ...
    }

For Step 3, you can override any entries in any `.xml` resource file in the `/src/main/res` folder with your own version in your `src/newBrandName/res`.  All values from `src/main/res` files are automatically inherited if they are not overwritten.

Typical resource values that you would override in a new brand include:

 * `app_name` in `src/newBrandName/res/values/strings.xml` - your app name, "New Brand Name"
 * `apiv2_key` in `src/newBrandName/res/values/do_not_translate.xml` - your Android Maps API v2 key (see [this page](https://developers.google.com/maps/documentation/android/start#get-key) to get your key)
 * app theme colors (`theme_primary`, `theme_primary_dark`, `theme_muted`, `theme_accent`) in `src/newBrandName/res/values/colors.xml` - the default colors for the Action Bar, etc.  For example, the Agency X brand specifies red theme colors, while Agency Y brand specifies blue theme colors.
 * `stop_info_ontime` in `src/newBrandName/res/values/colors.xml` - the OneBusAway brand uses its green theme color for the "on-time" arrival color.  If your theme color isn't green, you need to specify green for this "on-time" arrival color - we suggest `#4CAF50`
 * `ga_trackingId` in `src/newBrandName/res/values/app_tracker.xml` and `src/newBrandName/res/values/global_tracker.xml` - your Google Analytics tracker IDs.  We allow for two trackers to offer both unrolled (free) and rolled-up reporting (for paid accounts) - see [this discussion](https://github.com/OneBusAway/onebusaway-android/issues/105#issuecomment-71862899) for details.

If you want to implement custom code, we recommend that you create a base abstract class, and split the implementation differences into subclasses that can be selected at runtime by different brands based on configuration options in `build.gradle`.  See `org.onebusaway.android.ui.ArrivalsListAdapterBase` for a sample base class, and `ArrivalsListAdapterStyleA` and `ArrivalsListAdapterStyleB` for examples of how different presentations of arrival times can be used in different brands, and see the following section for how these options are specified in `build.gradle`.

### Configuration Options

We provide a few configuration options in `build.gradle` brand flavor definitions that allows you to choose some default behaviors of your brand.  Other customization is possible - if you'd like to see new customization options that aren't listed below, please reach out to us on the [OneBusAway Developers Google Group](https://groups.google.com/forum/#!forum/onebusaway-developers).

**Arrival Information**

Valid values are `0` and `1` - The default way that estimated arrival information is shown to the user.  There are two options, as defined in `BuildFlavorConstants`:
    * `ARRIVAL_INFO_STYLE_A = 0` - The original OneBusAway presentation of arrival info to the user, with small rows sorted by estimated arrival time
    * `ARRIVAL_INFO_STYLE_B = 1` - The presentation of arrival info created by York Region Transit/VIVA for their forked version of OBA, which groups arrival times by route, and shows scheduled arrival times - see [their apps here](http://www.yorkregiontransit.com/en/ridingwithus/apps.asp)

No matter which default is defined, users can change the sorting style by using the "Sort by" button in the action bar.

<img src="https://cloud.githubusercontent.com/assets/928045/8015719/0681c99a-0ba9-11e5-8f7b-9116f9bcc773.png" width="384" height="362" align=center />

**Fixed vs. Multi-region**

`USE_FIXED_REGION` - Valid values are `true` and `false` - If true, then the app will be fixed to the region information provided for this brand dimension in the `build.gradle`.  If false, then the app will function with the normal multi-region process, and work across various regions defined in the Regions API.  This value is false for the original OneBusAway brand so it supports multi-region.

## Examples

Here's an example of how configuration options are set up for the sample agency "Agency X" in `build.gradle`:

    agencyX {
        dimension "brand"  // This line is always the same for all brands - it tells Gradle that this entry is for a brand
        applicationId "org.agencyx.android" // This is the unique identifier for the listing of this brand on Google Play (typically its your unique domain name in reverse) - you can find this app at https://play.google.com/store/apps/details?id=org.agencyx.android
        manifestPlaceholders = [databaseAuthority: applicationId.toString() + '.provider']  // This line is always the same for all brands - it tells Gradle how to set up the database authority field for the brand
        buildConfigField "int", "ARRIVAL_INFO_STYLE", "0"  // Use the original OneBusAway presentation of arrival times (i.e., Style A) as the default
        buildConfigField "boolean", "USE_FIXED_REGION", "false" // Use the multi-region feature of OneBusAway to select the closest region
        ...
    }

So, Agency X has elected to stick with the normal OneBusAway arrival time presentation as the default and allow region roaming.  This app behaves much like the original OneBusAway app, except it has its own name and colors.

The Agency Y sample has chosen different options - they are using `ARRIVAL_INFO_STYLE_B`, and have their app fixed to the region info provided in their brand flavor dimension entry:

    agencyY {
        dimension "brand" // Always the same for all brands
        applicationId "org.agencyy.android" // Unique listing of this brand on app store
        manifestPlaceholders = [databaseAuthority: applicationId.toString() + '.provider'] // Always the same for all brands
        buildConfigField "int", "ARRIVAL_INFO_STYLE", "1" // Use the York Region Transit presentation of arrival times (i.e., Style B) by default
        buildConfigField "boolean", "USE_FIXED_REGION", "true" // Does not support multi-region - see the following fields for the region info that will be used
        // Fixed region info that the app will use
        buildConfigField "String", "FIXED_REGION_NAME", "\"Agency Y\""
        buildConfigField "String", "FIXED_REGION_OBA_BASE_URL",
                "\"http://api.tampa.onebusaway.org/api\""
        buildConfigField "String", "FIXED_REGION_SIRI_BASE_URL", "null"
        buildConfigField "double", "FIXED_REGION_BOUNDS_LAT", "27.976910500000002"
        buildConfigField "double", "FIXED_REGION_BOUNDS_LON", "-82.445851"
        buildConfigField "double", "FIXED_REGION_BOUNDS_LAT_SPAN", "0.5424609999999994"
        buildConfigField "double", "FIXED_REGION_BOUNDS_LON_SPAN", "0.576357999999999"
        buildConfigField "String", "FIXED_REGION_LANG", "\"en_US\""
        buildConfigField "String", "FIXED_REGION_CONTACT_EMAIL", "\"onebusaway@gohart.org\""
        buildConfigField "boolean", "FIXED_REGION_SUPPORTS_OBA_DISCOVERY_APIS", "true"
        buildConfigField "boolean", "FIXED_REGION_SUPPORTS_OBA_REALTIME_APIS", "true"
        buildConfigField "boolean", "FIXED_REGION_SUPPORTS_SIRI_REALTIME_APIS", "false"
        buildConfigField "String", "FIXED_REGION_TWITTER_URL",
                "\"http://mobile.twitter.com/OBA_tampa\""
        buildConfigField "String", "FIXED_REGION_STOP_INFO_URL", "null"
    }

Note that all brands need to supply the `FIXED_REGION_...` fields in their flavor dimension in `build.gradle` so the project will compile, although these values are only used if `USE_FIXED_REGION` is set to true.
