package org.onebusaway.android.io.request.survey;

import android.net.Uri;

import androidx.annotation.NonNull;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.request.RequestBase;
import org.onebusaway.android.ui.survey.model.StudyResponse;

import java.util.concurrent.Callable;


public final class ObaSurveyRequest extends RequestBase implements Callable<StudyResponse> {

    private ObaSurveyRequest(Uri uri) {
        super(uri);
    }

    public static class Builder {

        private static Uri URI = null;

        public Builder() {
            String weatherAPIURL = Application.get().getResources().getString(R.string.survey_api_url);
            URI = Uri.parse(weatherAPIURL);
        }

        public ObaSurveyRequest build() {
            return new ObaSurveyRequest(URI);
        }
    }

    public static ObaSurveyRequest newRequest() {
        return new Builder().build();
    }

    @Override
    public StudyResponse call() {
        return call(StudyResponse.class);
    }

    @NonNull
    @Override
    public String toString() {
        return "ObaSurveyRequest [mUri=" + mUri + "]";
    }
}
