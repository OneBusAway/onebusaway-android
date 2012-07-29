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
	curl -sSq -o "${location}/${filen}" $proxy "${host}/api/where/${1}${qstring}"
}

# Filenames are code generated into R.raw, so no hyphens
curling "agencies-with-coverage.json?"

curling "agency/1.json?"

curling "arrivals-and-departures-for-stop/1_75403.json?"
curling "arrivals-and-departures-for-stop/1_29261.json?"
curling "arrivals-and-departures-for-stop/1_10020.json?"

curling "current-time.json?"

curling "route/1_10.json?"

curling "routes-for-location.json?lat=47.61098&lon=-122.33845&" routes_for_location_downtown_seattle1.json
curling "routes-for-location.json?lat=48.85808&lon=2.29498&" routes_for_location_outofrange.json
curling "routes-for-location.json?lat=47.25331&lon=-122.4404&query=11&" routes_for_location_query.json
curling "routes-for-location.json?lat=47.25331&lon=-122.4404&query=112423&" routes_for_location_queryfail.json

curling "route-ids-for-agency/40.json?"

curling "schedule-for-stop/1_75403.json?"
curling "schedule-for-stop/1_75403.json?date=2012-07-30&" schedule_for_stop_1_75403_with_date.json

curling "shape/1_40046045.json?"

curling "stop/1_29261.json?"

curling "stop-ids-for-agency/40.json?"

curling "stops-for-location.json?lat=47.61098&lon=-122.33845&" stops_for_location_downtown_seattle1.json
curling "stops-for-location.json?lat=47.61&lon=-122.34&latSpan=0.020948&lonSpan=0.020598&" stops_for_location_downtown_seattle.json
curling "stops-for-location.json?lat=48.85808&lon=2.29498&" stops_for_location_outofrange.json
curling "stops-for-location.json?lat=47.25331&lon=-122.4404&query=26&" stops_for_location_query.json
curling "stops-for-location.json?lat=47.25331&lon=-122.4404&query=112423&" stops_for_location_queryfail.json

curling "stops-for-route/1_44.json?"
curling "stops-for-route/1_44.json?includePolylines=false&" stops_for_route_1_44_noshapes.json

curling "trip-details/1_18196913.json?"
curling "trip-details/1_18196913.json?includeTrip=false&" trip_details_1_18196913_no_trip.json
curling "trip-details/1_18196913.json?includeSchedule=false&" trip_details_1_18196913_no_schedule.json
curling "trip-details/1_18196913.json?includeStatus=false&" trip_details_1_18196913_no_status.json
curling "trip-details/1_18196913.json?includeStatus=false&" trip_details_1_18196913_no_status.json
curling "trip/1_18196913.json?" trip_1_18196913.json

curling "trips-for-location.json?lat=47.653&lon=-122.307&" trips_for_location_test1.json
curling "trips-for-location.json?lat=48.85808&lon=2.29498&" trips_for_location_outofrange.json

curling "stop/404test.json?" test_404_1.json
curling "stop/1_29261.xml?" test_badjson.json

echo
echo '-----'
echo 'Please refresh SeattleBusBotTests project and re-run the unit tests.'
echo '-----'