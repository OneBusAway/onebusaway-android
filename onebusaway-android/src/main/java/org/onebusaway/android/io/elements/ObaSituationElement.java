/*
 * Copyright (C) 2010-2012 Paul Watts (paulcwatts@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.io.elements;

import java.util.Arrays;
import java.util.List;

public final class ObaSituationElement implements ObaSituation {

    public static final ObaSituationElement EMPTY_OBJECT = new ObaSituationElement();

    public static final ObaSituationElement[] EMPTY_ARRAY = new ObaSituationElement[]{};

    public static final class Text {

        private final String value;
        //private final String lang;

        Text() {
            value = "";
            //lang = "";
        }

        public String getValue() {
            return value;
        }
    }

    public static final class AllAffectsElement implements AllAffects {

        public static final AllAffectsElement EMPTY_OBJECT = new AllAffectsElement();

        public static final AllAffectsElement[] EMPTY_ARRAY = new AllAffectsElement[]{};

        private final String directionId;

        private final String stopId;

        private final String tripId;

        private final String applicationId;

        private final String routeId;

        private final String agencyId;

        AllAffectsElement() {
            directionId = "";
            stopId = "";
            tripId = "";
            applicationId = "";
            routeId = "";
            agencyId = "";
        }

        @Override
        public String getDirectionId() {
            return directionId;
        }

        @Override
        public String getStopId() {
            return stopId;
        }

        @Override
        public String getTripId() {
            return tripId;
        }

        @Override
        public String getApplicationId() {
            return applicationId;
        }

        @Override
        public String getRouteId() {
            return routeId;
        }

        @Override
        public String getAgencyId() {
            return agencyId;
        }
    }

    public static final class ConditionDetailsElement
            implements ConditionDetails {

        private final String[] diversionStopIds;

        private final ObaShapeElement diversionPath;

        ConditionDetailsElement() {
            diversionStopIds = null;
            diversionPath = null;
        }

        @Override
        public ObaShape getDiversionPath() {
            return diversionPath;
        }

        @Override
        public List<String> getDiversionStopIds() {
            return Arrays.asList(diversionStopIds);
        }

    }

    public static final class ConsequenceElement implements Consequence {

        public static final ConsequenceElement[] EMPTY_ARRAY = new ConsequenceElement[]{};

        private final String condition;

        private final ConditionDetailsElement conditionDetails;

        ConsequenceElement() {
            condition = "";
            conditionDetails = null;
        }

        @Override
        public String getCondition() {
            return condition;
        }

        @Override
        public ConditionDetails getDetails() {
            return conditionDetails;
        }
    }

    public static final class ActiveWindowElement implements ActiveWindow {

        public static final ActiveWindowElement[] EMPTY_ARRAY = new ActiveWindowElement[]{};

        private long to, from;

        ActiveWindowElement() {
            to = 0;
            from = 0;
        }

        public long getTo() {return to;}
        public long getFrom() {return from;}
    }

    private final String id;

    private final Text summary;

    private final Text description;

    private final Text advice;

    private final String reason;

    //private final String securityAlert;
    private final long creationTime;

    private final AllAffectsElement[] allAffects;

    private final ConsequenceElement[] consequences;

    private final String severity;

    private final ActiveWindowElement[] activeWindows;

    private final Text url;

    ObaSituationElement() {
        id = "";
        summary = null;
        description = null;
        advice = null;
        reason = null;
        //securityAlert = null;
        creationTime = 0;
        allAffects = AllAffectsElement.EMPTY_ARRAY;
        consequences = ConsequenceElement.EMPTY_ARRAY;
        activeWindows = ActiveWindowElement.EMPTY_ARRAY;
        severity = "";
        url = null;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getSummary() {
        return (summary != null) ? summary.getValue() : null;
    }

    @Override
    public String getDescription() {
        return (description != null) ? description.getValue() : null;
    }

    @Override
    public String getAdvice() {
        return (advice != null) ? advice.getValue() : null;
    }

    @Override
    public String getReason() {
        return reason;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public AllAffects[] getAllAffects() {
        return allAffects;
    }

    @Override
    public Consequence[] getConsequences() {
        return consequences;
    }

    @Override
    public String getSeverity() {
        return severity;
    }

    @Override
    public ActiveWindow[] getActiveWindows() { return activeWindows; }

    @Override
    public String getUrl() {
        return (url != null) ? url.getValue() : null;
    }
}
