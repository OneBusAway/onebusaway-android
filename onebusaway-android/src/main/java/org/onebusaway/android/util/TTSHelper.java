package org.onebusaway.android.util;

import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import org.onebusaway.android.app.Application;

import java.util.Locale;

/**
 * Created by azizmb on 2/19/16.
 */
public class TTSHelper implements TextToSpeech.OnInitListener {
    private TextToSpeech tts;
    private String Message;

    public TTSHelper(String msg)
    {
        Message = msg;
        tts = new TextToSpeech(Application.get().getApplicationContext(), this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.getDefault());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts.speak(Message, TextToSpeech.QUEUE_FLUSH, null, "TRIPMESSAGE");
            } else {
                tts.speak(Message, TextToSpeech.QUEUE_FLUSH, null);
            }
        }
    }
}
