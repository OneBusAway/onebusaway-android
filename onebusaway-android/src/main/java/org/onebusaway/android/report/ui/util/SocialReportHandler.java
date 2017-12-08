/*
* Copyright (c) 2017 Microsoft Corporation.
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
package org.onebusaway.android.report.ui.util;

import com.microsoft.embeddedsocial.sdk.IReportHandler;
import com.microsoft.embeddedsocial.server.model.view.TopicView;

import org.onebusaway.android.R;
import org.onebusaway.android.report.ui.InfrastructureIssueActivity;
import org.onebusaway.android.report.ui.ReportActivity;

import android.content.Context;
import android.content.Intent;

public class SocialReportHandler implements IReportHandler {
    @Override
    public void generateReport(Context context, TopicView topic) {
        Category category = Category.fromValue(topic.getTopicCategory());

        Intent intent = new Intent(context, InfrastructureIssueActivity.class);

        int serviceKeyword = 0;

        if (category == Category.ROUTE) {
            serviceKeyword = R.string.ri_selected_service_trip;
        } else if (category == Category.STOP) {
            serviceKeyword = R.string.ri_selected_service_stop;
        } else {
            // open default report handler
            intent = new Intent(context, ReportActivity.class);
            context.startActivity(intent);
        }

        InfrastructureIssueActivity.startWithService(context, intent, context.getString(serviceKeyword));
    }

    @Override
    public String getDisplayString(Context context, TopicView topic) {
        Category category = Category.fromValue(topic.getTopicCategory());

        if (category == Category.ROUTE) {
            return context.getString(R.string.bus_options_menu_report_trip_problem);
        } else if (category == Category.STOP) {
            return context.getString(R.string.stop_info_option_report_problem);
        }

        return null;
    }

    enum Category {
        ROUTE("Routes"),
        STOP("Stops");

        private String value;

        Category(String value) {
            this.value = value;
        }

        public String toValue() {
            return this.value;
        }

        public static Category fromValue(String value) {
            Category[] items = Category.values();
            for (Category item : items) {
                if (item.value.equals(value)) {
                    return item;
                }
            }
            return null;
        }
    }
}
