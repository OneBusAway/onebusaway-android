#!/bin/bash

#Should use soak-api.onebusaway.org, but not giving responses at the moment
host=http://api.onebusaway.org
version=2
key=TEST
qstring="key=$key&version=$version"
location=tests/res/raw
#proxy="-x localhost:8000"

curling() {
	local filen=$2
	[ "$#" -eq 1 ] && {
		# no file given, use the URL instead 
		# json extension gets added later
		filen=${1//.json/}

	  # strip out special characters to conform to valid resource names
		filen=${filen//[-#\\\/&=\?]/_}
		filen=${filen%\_}.json
	}

	echo "File: " ${filen}
	curl -o "${location}/${filen}" $proxy "${host}/api/where/${1}${qstring}"
}

rm tests/res/raw/*.json

# Filenames are code generated into R.raw, so no hyphens
curling "arrivals-and-departures-for-stop/1_75403.json?"
curling "schedule-for-stop/1_75403.json?"
curling "arrivals-and-departures-for-stop/1_29261.json?"
curling "arrivals-and-departures-for-stop/1_10020.json?"

curling "stops-for-location.json?lat=47.61&lon=-122.34&latSpan=0.020948&lonSpan=0.020598&" stops_for_location_downtown_seattle.json
#how long do tripIds remain valid?
curling "trip-details/1_18196913.json?"
curling "trip-details/1_18196913.json?includeTrip=false&" trip_details_1_18196913_no_trip.json
curling "trip-details/1_18196913.json?includeSchedule=false&" trip_details_1_18196913_no_schedule.json
curling "trip-details/1_18196913.json?includeStatus=false&" trip_details_1_18196913_no_status.json
curling "trip-details/1_18196913.json?includeStatus=false&" trip_details_1_18196913_no_status.json
curling "trip/1_18196913.json?" trip_1_18196913.json

echo
echo "-----"
echo "Please refresh SeattleBusBotTests project and re-run the unit tests."
echo "-----"