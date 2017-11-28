# Testing with custom OneBusAway and OpenTripPlanner servers

We support testing custom [OneBusAway](https://github.com/OneBusAway/onebusaway-application-modules/wiki) and [OpenTripPlanner](http://www.opentripplanner.org/) servers in OneBusAway Android.  You can use
this feature to try out a new test server that you've set up, but aren't ready to publicize in the [Regions API](https://github.com/OneBusAway/onebusaway/wiki/Multi-Region) yet.

Before doing anything, check out the [system architecture diagrams](SYSTEM_ARCHITECTURE.md) to understand how OBA Android communicates with other servers. and what features they provide.
 
## Configuration

In the app, go to "Settings->Advanced".  You should see a screen like:

![image](https://cloud.githubusercontent.com/assets/928045/17785706/dc7f5d0e-654f-11e6-90d7-79792d31c4f8.png)

You can use the following directions to add a custom OneBusAway API server and/or a custom OpenTripPlanner
server.  After entering the server name and path, hit the back button twice to exit the Settings screen, and the app will re-initialize with the new URL(s).

### OneBusAway API Server

You can enter a server URL in a few different formats, including:

* `example.onebusaway.org`
* `example.onebusaway.org/onebusaway-api-webapp` (if you deployed to the default path)
* `http://example.onebusaway.org`
* `http://example.onebusaway.org/onebusaway-api-webapp`

### OpenTripPlanner API Server

You can enter a server URL in a few different formats, including:

* `example.opentripplanner.org/otp`
* `http://example.opentripplanner.org/otp`

Note that if your server is using SSL/HTTPS, you currently need to enter the entire URL:

* `https://example.opentripplanner.org/otp`

For older OpenTripPlanner servers (circa pre-v0.19.0) that don't include "/otp/routers/default" in the path, you can 
include the path up until the "plan" endpoint.

For example, if your server "plan" endpoint is at `http://example.opentripplanner.org/tripplanner/plan?...`, then you can enter the following as the custom OTP URL:

* `example.opentripplanner.org/tripplanner/`