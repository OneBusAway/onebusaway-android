package org.onebusaway.android.io.request.survey.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SubmitSurveyResponse {

    private final SurveyResponse surveyResponse;

    @JsonCreator
    public SubmitSurveyResponse(@JsonProperty("survey_response") SurveyResponse surveyResponse) {
        this.surveyResponse = surveyResponse;
    }

    public SurveyResponse getSurveyResponse() {
        return surveyResponse;
    }

    @Override
    public String toString() {
        return "SubmitSurveyResponse{" +
                "surveyResponse=" + surveyResponse +
                '}';
    }

    public static class SurveyResponse {

        private final String id;
        private final String updatePath;
        private final String userIdentifier;

        @JsonCreator
        public SurveyResponse(
                @JsonProperty("id") String id,
                @JsonProperty("update_path") String updatePath,
                @JsonProperty("user_identifier") String userIdentifier) {
            this.id = id;
            this.updatePath = updatePath;
            this.userIdentifier = userIdentifier;
        }

        public String getId() {
            return id;
        }

        public String getUpdatePath() {
            return updatePath;
        }

        public String getUserIdentifier() {
            return userIdentifier;
        }

        @Override
        public String toString() {
            return "SurveyResponse{" +
                    "id='" + id + '\'' +
                    ", updatePath='" + updatePath + '\'' +
                    ", userIdentifier='" + userIdentifier + '\'' +
                    '}';
        }
    }
}
