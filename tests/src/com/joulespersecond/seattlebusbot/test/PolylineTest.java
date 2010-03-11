package com.joulespersecond.seattlebusbot.test;

import java.util.List;

import android.test.AndroidTestCase;

import com.google.android.maps.GeoPoint;
import com.joulespersecond.oba.ObaPolyline;

public class PolylineTest extends AndroidTestCase {

	public void testDecodeLines() {
		List<GeoPoint> list = ObaPolyline.decodeLine("_p~iF~ps|U", 1);
		assertNotNull(list);
		assertEquals(list.size(), 1);
		GeoPoint pt = list.get(0);
		assertEquals(pt.getLatitudeE6(), 38500000);
		assertEquals(pt.getLongitudeE6(), -120200000);

		list = ObaPolyline.decodeLine("_p~iF~ps|U_ulLnnqC", 2);
		assertNotNull(list);
		assertEquals(list.size(), 2);
	    pt = list.get(0);
		assertEquals(pt.getLatitudeE6(), 38500000);
		assertEquals(pt.getLongitudeE6(), -120200000);
		pt = list.get(1);
		assertEquals(pt.getLatitudeE6(), 40700000);
		assertEquals(pt.getLongitudeE6(), -120950000);

		list = ObaPolyline.decodeLine("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 3);
		assertNotNull(list);
		assertEquals(list.size(), 3);
		pt = list.get(2);
		assertEquals(pt.getLatitudeE6(), 43252000);
		assertEquals(pt.getLongitudeE6(), -126453000);
	}
	public void testDecodeLevels() {
		List<Integer> list = ObaPolyline.decodeLevels("mD", 1);
		assertNotNull(list);
		assertEquals(list.size(), 1);
		Integer i = list.get(0);
		assertEquals((int)i, 174);

		list = ObaPolyline.decodeLevels("BBBB", 4);
		assertNotNull(list);
		assertEquals(list.size(), 4);
		assertEquals((int)list.get(0), 3);
		assertEquals((int)list.get(1), 3);
		assertEquals((int)list.get(2), 3);
		assertEquals((int)list.get(3), 3);

	}
}
