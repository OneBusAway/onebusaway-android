#OneBusAway
Official Website: [OneBusAway](http://www.onebusaway.org/)

Download Android app: [PlayStore](https://play.google.com/store/apps/details?id=com.joulespersecond.seattlebusbot)

##Introduction

OneBusAway is a free application available for several platforms which goal is to offer accurate real-time information for public transports.

It contains a browsable map in order to make routes planning easily.

The notification system allows the user to be constantly reminded of nearby stops and/or transports.

It is still a project in development with several collaborators with the willing to make it a worth using app.

##Architecture Overview

* Client-Server architecture;
* Back-end server serves data throughout multiple transit agencies;
* different user interfaces(Web, Phone, API's) as multiple distinct processes;

##

![alt text](https://github.com/OneBusAway/onebusaway-application-modules/wiki/ArchitectureDiagram.png "Diagram")

##

The user interface services are separated into distinct instances for increase of performance and reliability. Each instance can get data from different agencies, even when one of them is offline.

The data is organized into an optimited graph to support faster trips of information and respond faster to operations. 

Distinct agencies get updates independently from each other. Changing data of an agency(stop) doesn't require to process all the data from the other agencies. Therefore, this partitioned scheme decreases the time needed in processing. It is essencial when working on relatively large amount of information.

##

The user interface services each get an instance of TransitDataService interface which is implemented by federation module. It is responsible for:

    schedule computation;
    real-time arrival handling;
    trip-planning.
