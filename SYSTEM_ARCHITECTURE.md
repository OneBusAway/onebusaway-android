# System architecture

OneBusAway Android is a client application that requires real-time server access in order to provide information to users.

OneBusAway uses a [multi-region architecture](https://github.com/OneBusAway/onebusaway/wiki/Multi-Region) where each region (e.g., Tampa, Seattle) is responsible for running their own open-source OneBusAway server (the [onebusaway-application-modules project](https://github.com/OneBusAway/onebusaway-application-modules)). 

The OBA Android app uses the [Regions API](http://regions.onebusaway.org/regions-v3.json) to discover regions and their server API endpoints, based on the real-time location of the device and the region geographic boundaries available in the Regions API response.

Each region can run one or more servers (details in next section) to add features to the OneBusAway Android app.  The following sections present three different regional deployment examples:
1. A simple deployment that uses only the OneBusAway server
2. A deployment that adds trip planning and bike share information
3. A deployment that adds issue reporting via an Open311 server

Note that you can have all three of the above active at once - the Tampa Bay region currently uses all three.

The below system architecture diagrams come from [this Google Drawing](https://docs.google.com/drawings/d/1Z4D8n1PPI7U-G1VgYxehsvyGeVBv6mveGTkgfSbKhCo/edit?usp=sharing).

## Simple deployment

The simplest way to get started is to deploy a OneBusAway server, which will give you estimated arrival times and server alerts in the app.  Here's the basic system architecture diagram for this configuration:

![onebusaway system architecture-basic](https://user-images.githubusercontent.com/928045/32296017-5dd339e4-bf21-11e7-962c-327cf071b5ba.png)

## Add trip planning and/or bike share *(Optional)*

If you want to add trip planning and/or bike share information, you'll need to set up an OpenTripPlanner server.  See [this article](https://medium.com/@sjbarbeau/bike-share-launches-in-onebusaway-3452c08c0ed) for details about the bike share features.  Here's the system architecture for a region that's added trip planning and bike share information (e.g., Tampa Bay):

![onebusaway system architecture-otp](https://user-images.githubusercontent.com/928045/32296042-69aa4726-bf21-11e7-8123-1f80c17fee4c.png)

## Add issue reporting via Open311-compliant system *(Optional)*

If you use an issue management system that supports the Open311 standard, you can have OneBusAway Android submit problem reports to this system, including metadata from the stop/trip they are reporting the problem about as well as an attached picture.  See [this presentation](https://www.slideshare.net/sjbarbeau/2017-seeclickfix-workshop-closing-the-loop-improving-transit-through-crowdsourced-information) for details about the enhanced issue reporting feature.  Here's the system architecture for a region that's added issue reporting via Open311 (e.g., Tampa Bay):

![onebusaway system architecture-open311](https://user-images.githubusercontent.com/928045/32296055-721a068a-bf21-11e7-922d-f167118d2390.png)

## Configure your own servers

#### OneBusAway and OpenTripPlanner

See the [Getting Started](https://github.com/OneBusAway/onebusaway-application-modules#getting-started) section of the [**onebusaway-application-modules**](https://github.com/OneBusAway/onebusaway-application-modules) project to set up your own OneBusAway server - this is a good place to start, and will give you arrival estimates and service alerts in the OneBusAway Android app.

After you have the OneBusAway server set up, see the [Custom Servers](CUSTOM_SERVERS.md) documentation in this project to see how you can test OneBusAway Android with your new OBA server.

After you've tested arrival information, if you want to add trip planning and/or bike share information, see the [Basic Usage](http://docs.opentripplanner.org/en/latest/Basic-Usage/) documentation for setting up an OpenTripPlanner server.

When you have this set up, see the [Custom Servers](CUSTOM_SERVERS.md#opentripplanner-api-server) documentation again to point your OBA Android app to your new OTP server.

#### Open311

Currently we don't have a way to easily plug an Open311 server URL directly into the OBA Android app UI to test, but [let us know](https://github.com/OneBusAway/onebusaway/wiki/Contact-Us) if you'd like to test it out and we'll help out.