package org.onebusaway.android.core;

import android.database.Cursor;

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

	public StopData(String id, String name, String dir, String uiName) {
		this.id = id;
		this.name = name;
		this.dir = dir;
		this.uiName = uiName;
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

    @Override
    public boolean equals(Object o) {
        if (o instanceof StopData) {
            StopData other = (StopData) o;
            return stringEquals(other.dir, dir) &&
                    stringEquals(other.id, id) &&
                    stringEquals(other.name, name) &&
                    stringEquals(other.uiName, uiName);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        hashCode = hashCode * 31 + dir == null ? 0 : dir.hashCode();
        hashCode = hashCode * 31 + id == null ? 0 : id.hashCode();
        hashCode = hashCode * 31 + name == null ? 0 : name.hashCode();
        hashCode = hashCode * 31 + uiName == null ? 0 : uiName.hashCode();
        return hashCode;
    }

    private boolean stringEquals(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return (s1 == null && s2 == null);
        }
        return s1.equals(s2);
    }
}
