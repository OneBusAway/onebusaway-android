package org.onebusaway.android.util;

import com.google.android.gms.wearable.DataMap;

import org.onebusaway.android.core.StopData;

public class StopDataUtil {

	public static DataMap toDataMap(StopData stopData) {
		DataMap dataMap = new DataMap();
		dataMap.putString(StopData.Keys.ID.getValue(), stopData.getId());
		dataMap.putString(StopData.Keys.DIR.getValue(), stopData.getDir());
		dataMap.putString(StopData.Keys.NAME.getValue(), stopData.getName());
		dataMap.putString(StopData.Keys.UI_NAME.getValue(), stopData.getUiName());
		return dataMap;
	}

	public static StopData toStopData(DataMap dataMap) {
		String id = dataMap.getString(StopData.Keys.ID.getValue());
		String dir = dataMap.getString(StopData.Keys.DIR.getValue());
		String name = dataMap.getString(StopData.Keys.NAME.getValue());
		String uiName = dataMap.getString(StopData.Keys.UI_NAME.getValue());
		return new StopData(id, dir, name, uiName);
	}
}