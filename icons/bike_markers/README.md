# Bikeshare icons
The images contained in this folder were used to generate the icons used for bike markers.

## bike_marker_small.svg
This image is displayed for all types of bikeshare (floating bikes and bike stations) when the map
zoom is under level 13.

It is converted into XML and loaded as BMP for use. To convert to XML use the Android Studio 
Vector Asset generator.
  
(instructions for Android Studio version 2.3.3)
- Right-click folder res/drawable
- Select New > Vector Asset
- On the dialog that opens:
    - Provide Path and Name and complete the action.
 
 ## bike_floating_marker_big.svg
This image is used for floating bikes when the map is on zoom level bigger than 13. It is used as PNG and was converted using the [Generic Icon 
Generator](https://romannurik.github.io/AndroidAssetStudio/icons-generic.html).

Parameters used:
- Padding: 0%
- Asset size: 32dp
- Asset padding: 0
- Color: (select transparent)

## bike_station_marker_big.svg
This image is used for bike stations when the map is on zoom level bigger than 13. It is also used 
as PNG. Follow the instructions on the previos section to create the PNGs.