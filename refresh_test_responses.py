#!/usr/bin/env python

import os.path
import json
import urllib
import urlparse

RAWPATH = 'onebusaway-android\src\instrumentTest\res\raw'
URIMAP = os.path.join(RAWPATH, 'urimap.json')
API_HOST = 'api.onebusaway.org'
API_PARAMS = [('key', 'TEST'), ('version', '2')]


def _real_url(url):
    parsed = urlparse.urlparse(url)
    # Append any query parameters
    qs = urlparse.parse_qsl(parsed.query)
    qs = urllib.urlencode(qs + API_PARAMS)
    # Put everything back together
    result = urlparse.urlunparse(('http', API_HOST, parsed.path, '', qs, ''))
    return result


def main():
    # Read the URI Map, and construct a real URL out of the mock URL.
    urimap = json.load(open(URIMAP))
    uris = urimap['uris']
    for path, dest in uris.iteritems():
        url = _real_url(path)
        if not dest.startswith('__'):
            destfile = os.path.join(RAWPATH, dest + '.json')
            print destfile
            urllib.urlretrieve(url, destfile)

    print """
-----
Responses updated with the latest server data.
Go back to Android Studio, refresh the onebusaway-android project and re-run the unit tests.
-----
    """


if __name__ == '__main__':
    main()
