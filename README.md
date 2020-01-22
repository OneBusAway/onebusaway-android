# OneBusAway for Android [![Build Status](https://travis-ci.org/OneBusAway/onebusaway-android.svg?branch=master)](https://travis-ci.org/OneBusAway/onebusaway-android) [![Join the OneBusAway chat](https://onebusaway.herokuapp.com/badge.svg)](https://onebusaway.herokuapp.com/)

This is the official Android / Fire Phone app for [OneBusAway](https://onebusaway.org/), a project of the non-profit [Open Transit Software Foundation](https://opentransitsoftwarefoundation.org/)!

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
      alt="Get it on Google Play"
      height="80">](https://play.google.com/store/apps/details?id=com.joulespersecond.seattlebusbot)

[<img src="https://images-na.ssl-images-amazon.com/images/G/01/mobile-apps/devportal2/res/images/amazon-underground-app-us-black.png"
      alt="Amazon App Store"
      height="60">](http://www.amazon.com/gp/mas/dl/android?p=com.joulespersecond.seattlebusbot)

<image src="https://user-images.githubusercontent.com/928045/29891146-14ee4f44-8d98-11e7-999b-63f5d2ece916.gif" width="240" height="427" align=right />

OneBusAway for Android provides:

1. Real-time arrival/departure information for public transit
2. A browse-able map of nearby stops
3. A list of favorite bus stops
4. Reminders to notify you when your bus is arriving or departing
5. The ability to search for nearby stops or routes
6. Real-time multimodal trip planning, using real-time transit and bike share information (requires a regional [OpenTripPlanner](http://www.opentripplanner.org/) server)
6. Bike share map layer, which includes real-time availability information for floating bikes and bike rack capacity (requires a regional [OpenTripPlanner](http://www.opentripplanner.org/) server)
7. Issue reporting to any Open311-compliant issue management system (see [this page](ISSUE_REPORTING.md) for details)

OneBusAway for Android automatically keeps track of your most used stops and routes, and allows you to put shortcuts on your phone's home screen for any stop or route you choose.

## Alpha and Beta Testing

Get early access to new OneBusAway Android versions, and help us squash bugs! See our [Testing Guide](https://github.com/OneBusAway/onebusaway-android/blob/master/BETA_TESTING.md) for details.

## Build Setup

Want to build the project yourself and test some changes?  See our [build documentation](BUILD.md).

## Contributing

We welcome contributions to the project! Please see our [Contributing Guide](https://github.com/OneBusAway/onebusaway-android/blob/master/.github/CONTRIBUTING.md) for details, including Code Style Guidelines and Template.

## System Architecture

Curious what servers power certain features in OneBusAway Android?  Check out the [System Architecture page](SYSTEM_ARCHITECTURE.md).

## Deploying OneBusAway Android in Your City

There are two ways to deploy OneBusAway Android in your city:

1. **Join the OneBusAway [multi-region project](https://github.com/OneBusAway/onebusaway/wiki/Multi-Region)** - The easiest way to get started - simply set up your own OneBusAway server with your own transit data, and get added to the OneBusAway apps!  See [this page](https://github.com/OneBusAway/onebusaway/wiki/Multi-Region) for details.
2. **Deploy a rebranded version of OneBusAway Android as your own app on Google Play** - Requires a bit more maintenance, but it allows you to set up your own app on Google Play / Amazon App Store based on the OneBusAway Android source code. See [rebranding page](https://github.com/OneBusAway/onebusaway-android/blob/master/REBRANDING.md) for details.

## Testing Your Own OneBusAway/OpenTripPlanner servers

Did you just set up your own [OneBusAway](https://github.com/OneBusAway/onebusaway-application-modules/wiki) and/or [OpenTripPlanner](http://www.opentripplanner.org/) server?  You can test both in this app without compiling any Android code.  Just download the app from [Google Play](https://play.google.com/store/apps/details?id=com.joulespersecond.seattlebusbot), and see our [Custom Server Setup Guide](CUSTOM_SERVERS.md) for details.

## Permissions

In order to support certain features in OneBusAway, we need to request various permissions to access information on your device.  See an explanation of why each permission is needed [here](PERMISSIONS.md).

## Troubleshooting

Things not going well building the project?  See our [Troubleshooting](TROUBLESHOOTING.md) section.  If you're a user of the app, check out our [FAQ](FAQ.md).

## OneBusAway Project

Want to learn more about the [OneBusAway project](https://onebusaway.org/), a project of the non-profit [Open Transit Software Foundation](https://opentransitsoftwarefoundation.org/)? [Read up on the entire Application Suite](https://github.com/OneBusAway/onebusaway-application-modules) and/or [learn more about the mobile apps](https://github.com/OneBusAway/onebusaway-application-modules/wiki/Mobile-App-Design-Considerations).
