package org.onebusaway.android.core;

import android.database.Cursor;

public class StopData {

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
