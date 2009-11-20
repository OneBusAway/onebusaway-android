package com.joulespersecond.seattlebusbot;

import android.app.Activity;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.widget.TextView;

final class UIHelp {
    private static final String TAG = "UIHelp";
    
    public static void setChildClickable(Activity parent, int id, ClickableSpan span) {
        TextView v = (TextView)parent.findViewById(id);
        Spannable text = (Spannable)v.getText();
        text.setSpan(span, 0, text.length(), 0);
        v.setMovementMethod(LinkMovementMethod.getInstance());
    }
    
    public static final int getStopDirectionText(String direction) {
        if (direction.equals("N")) {
            return R.string.direction_n;
        } else if (direction.equals("NW")) {
            return R.string.direction_nw;                    
        } else if (direction.equals("W")) {
            return R.string.direction_w;                    
        } else if (direction.equals("SW")) {
            return R.string.direction_sw;    
        } else if (direction.equals("S")) {
            return R.string.direction_s;    
        } else if (direction.equals("SE")) {
            return R.string.direction_se;    
        } else if (direction.equals("E")) {
            return R.string.direction_e;    
        } else if (direction.equals("NE")) {
            return R.string.direction_ne;                             
        } else {
            Log.v(TAG, "Unknown direction: " + direction);
            return R.string.direction_n;
        }    
    }
}
