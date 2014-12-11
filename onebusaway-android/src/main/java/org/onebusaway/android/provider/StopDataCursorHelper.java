package org.onebusaway.android.provider;

import android.database.Cursor;

import org.onebusaway.android.core.StopData;
import org.onebusaway.android.ui.QueryUtils;

public final class StopDataCursorHelper {
    public static StopData createStopData(Cursor c, int position) {
        return new StopData(
                c,
                position,
                QueryUtils.StopList.Columns.COL_ID,
                QueryUtils.StopList.Columns.COL_DIRECTION,
                QueryUtils.StopList.Columns.COL_NAME,
                QueryUtils.StopList.Columns.COL_UI_NAME);
    }
}