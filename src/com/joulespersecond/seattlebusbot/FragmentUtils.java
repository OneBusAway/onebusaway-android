package com.joulespersecond.seattlebusbot;

import android.content.Intent;
import android.os.Bundle;

final class FragmentUtils {
    static final String URI = "uri";

    static Bundle getIntentArgs(Intent intent) {
        Bundle args = intent.getExtras();
        if (args != null) {
            args = (Bundle)args.clone();
        } else {
            args = new Bundle();
        }
        args.putParcelable(URI, intent.getData());
        return args;
    }
}
