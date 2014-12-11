package org.onebusaway.android.core;

import android.database.Cursor;

import com.google.android.gms.wearable.DataMap;

public class StopData {

    public enum Keys {
        ID("id"),
        DIR("dir"),
        NAME("name"),
        UI_NAME("uiName");

        private String value;

        public String getValue() {
            return value;
        }

        Keys(String value) {
            this.value = value;
        }
    }

    private final String id;

    private final String name;

    private final String dir;

    private final String uiName;

    public StopData(Cursor c, int row, int colId, int colDirection, int colName, int colUiName) {
        c.moveToPosition(row);
        id = c.getString(colId);
        dir = c.getString(colDirection);
        name = c.getString(colName);
        uiName = c.getString(colUiName);
    }

    public StopData(DataMap dataMap) {
        id = dataMap.getString(Keys.ID.getValue());
        dir = dataMap.getString(Keys.DIR.getValue());
        name = dataMap.getString(Keys.NAME.getValue());
        uiName = dataMap.getString(Keys.UI_NAME.getValue());
    }

    public DataMap toDataMap() {
        DataMap dataMap = new DataMap();
        dataMap.putString(StopData.Keys.ID.getValue(), id);
        dataMap.putString(StopData.Keys.DIR.getValue(), dir);
        dataMap.putString(StopData.Keys.NAME.getValue(), name);
        dataMap.putString(StopData.Keys.UI_NAME.getValue(), uiName);
        return dataMap;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDir() {
        return dir;
    }

    public String getUiName() {
        return uiName;
    }
}
