<#
.SYNOPSIS

Generate the various direction arrows from a single svg file containing all the directions

.PARAMETER veh

The vehicles to generate the svg icons for. The default is to generate the icons for the subway, tram, boat, train, bus if parameter is omitted
.EXAMPLE

To generate the default vehicle set of arrow images
.\LoadImage.ps1

.EXAMPLE

This will search for ".\marker_with_funicular_smaller_all_inside.svg" in the current directory and generate the 8 directions and no direction icons.
.\LoadImage.ps1 -veh funicular

#>

param (
[string[]] $veh = @('subway', 'tram', 'boat', 'train', 'bus')
)

function select-arrow
{
	param ($v, $xml, $dir, $id)
	$newImage = $xml.clone()
	$newImage.svg.path | % {
		if ($_.id -like 'path3021*' -and -not ($_.id -like $id))
		{
			$_.ParentNode.RemoveChild($_)
		}
	}
	$newImage.OuterXml | Out-File ".\marker_with_$($v)_smaller_$($dir)_inside.svg" -encoding ASCII
}

$veh | %{

	[xml] $withArrows = Get-Content ".\marker_with_$($_)_smaller_all_inside.svg"

	select-arrow $_ $withArrows 'none' '-----'

	select-arrow $_ $withArrows 'north' 'path3021-1-4'

	select-arrow $_ $withArrows 'north_west' 'path3021-1-7-4-8'

	select-arrow $_ $withArrows 'north_east' 'path3021-1-7-4'

	select-arrow $_ $withArrows 'south_east' 'path3021-1-7'

	select-arrow $_ $withArrows 'east' 'path3021-1-4-0'

	select-arrow $_ $withArrows 'south' 'path3021-1'

	select-arrow $_ $withArrows 'south_west' 'path3021'

	select-arrow $_ $withArrows 'west' 'path3021-1-4-0-9'
}
