# Adding new vehicle icons
The overall steps are as follows
- Copy the path of the vehicle into the svg with all the directions
- Run the powershell script LoadImage.ps1, with the specified vehicle type (ie 'funicular'). This will generate the different arrrow svg files to create the png files with
- Generate the png files of different sizes

## Copy the path in to the marker template
Make a copy of "marker_with_empty_smaller_all_inside.png". Replace the *empty* part of the file name with the name of the vehicle. ie "marker_with_funicular_smaller_all_inside.png". Within the file, there is a comment indicating where to paste a path within the document. You may need to use [Inkscape](https://inkscape.org/) or similar to move and resize the newly inserted path to place.

## LoadImage.ps1
Run this powershell script in the same folder as the newly created svg file to generate all the arrows
```sh
.\LoadImage.ps1 -veh 'funicular'
```

You can also add the vehicle type to the defaults within the LoadImage file for the $veh parameter

## Png files
Use the [Android Asset Studio Icon Generator](https://romannurik.github.io/AndroidAssetStudio/icons-generic.html#source.type=image&source.space.trim=1&source.space.pad=0&size=40&padding=0&color=000%2C100&name=ic_marker_with_boat_smaller_south_west_inside) to create the images of various sizes for the png. Size should be set to 40 dip and the color should be at 0%. Once the files are generated, you may paste them into the project.
