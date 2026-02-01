package org.onebusaway.android.io.request.survey;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.request.RequestBase;
import org.onebusaway.android.io.request.survey.model.StudyResponse;
import org.onebusaway.android.ui.survey.SurveyPreferences;

import java.util.concurrent.Callable;

/**
 * Represents a request to fetch study data.
 */

public final class ObaStudyRequest extends RequestBase implements Callable<StudyResponse> {

    private ObaStudyRequest(Uri uri) {
        super(uri);
    }

    public static class Builder {

        private static Uri URI = null;

        public Builder(Context context) {
            ObaRegion region = Application.get().getCurrentRegion();
            if (region == null) return;
            String baseUrl = region.getSidecarBaseUrl();
            if(baseUrl == null) return;
            String studyAPIURL = baseUrl + Application.get().getResources().getString(R.string.studies_api_endpoint);
            studyAPIURL = studyAPIURL.replace("regionID", String.valueOf(Application.get().getCurrentRegion().getId()));
            URI = Uri.parse(studyAPIURL).buildUpon().appendQueryParameter("user_id", SurveyPreferences.getUserUUID(context)).build();
        }

        public ObaStudyRequest build() {
            return new ObaStudyRequest(URI);
        }
    }

    public static ObaStudyRequest newRequest(Context context) {
        return new Builder(context).build();
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
