package org.onebusaway.android.io.request.survey;

import android.net.Uri;

import androidx.annotation.NonNull;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.request.RequestBase;
import org.onebusaway.android.io.request.survey.model.StudyResponse;

import java.util.concurrent.Callable;


public final class ObaStudyRequest extends RequestBase implements Callable<StudyResponse> {

    private ObaStudyRequest(Uri uri) {
        super(uri);
    }

    public static class Builder {

        private static Uri URI = null;

        public Builder() {
            String studyAPIURL = Application.get().getResources().getString(R.string.studies_api_url);
            studyAPIURL = studyAPIURL.replace("regionID", String.valueOf(Application.get().getCurrentRegion().getId()));
            URI = Uri.parse(studyAPIURL);
        }

        public ObaStudyRequest build() {
            return new ObaStudyRequest(URI);
        }
    }

    public static ObaStudyRequest newRequest() {
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
