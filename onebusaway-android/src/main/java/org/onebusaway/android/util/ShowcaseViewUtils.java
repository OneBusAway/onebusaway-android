package org.onebusaway.android.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.request.ObaArrivalInfoResponse;

import uk.co.deanwild.materialshowcaseview.MaterialShowcaseView;

public class ShowcaseViewUtils {

    public static final String TUTORIAL_WELCOME = ".tutorial_welcome";
    public static final String TUTORIAL_ARRIVAL_HEADER_ARRIVAL_INFO = ".tutorial_arrival_header_arrival_info";
    public static final String TUTORIAL_ARRIVAL_HEADER_SLIDING_PANEL = ".tutorial_arrival_header_sliding_panel";
    public static final String TUTORIAL_ARRIVAL_SORT = ".tutorial_arrival_sort";
    public static final String TUTORIAL_ARRIVAL_HEADER_STAR_ROUTE = ".tutorial_arrival_header_star_route";
    public static final String TUTORIAL_RECENT_STOPS_ROUTES = ".tutorial_recent_stops_routes";
    public static final String TUTORIAL_STARRED_STOPS_SORT = ".tutorial_starred_stops_sort";
    public static final String TUTORIAL_TRIP_PLAN_GEOCODER = ".tutorial_trip_plan_geocoder";

    private static MaterialShowcaseView mShowcaseView;

    public synchronized static void showTutorial(String tutorialType,
                                                 final AppCompatActivity activity,
                                                 final ObaArrivalInfoResponse response,
                                                 boolean alwaysShow) {

        if (activity == null) {
            return;
        }

        SharedPreferences settings = Application.getPrefs();

        boolean showTutorials = settings.getBoolean(
                activity.getString(R.string.preference_key_show_tutorial_screens), true);

        if (!showTutorials && !alwaysShow) {
            return;
        }

        boolean showedThisTutorial = settings.getBoolean(tutorialType, false);
        if (showedThisTutorial) {
            return;
        }

        if (giveUserTutorialBreak(activity, tutorialType)) {
            return;
        }

        Resources r = activity.getResources();

        String title;
        SpannableString text;
        View targetView = null;

        String appName = r.getString(R.string.app_name);

        switch (tutorialType) {

            case TUTORIAL_WELCOME:
                title = r.getString(R.string.tutorial_welcome_title, appName);
                text = new SpannableString(r.getString(R.string.tutorial_welcome_text));
                break;

            case TUTORIAL_ARRIVAL_HEADER_ARRIVAL_INFO:
                if (response == null || response.getArrivalInfo().length < 1) return;

                title = r.getString(R.string.tutorial_arrival_header_arrival_info_title);
                text = new SpannableString(
                        r.getString(R.string.tutorial_arrival_header_arrival_info_text));
                targetView = activity.findViewById(R.id.eta_and_min);
                break;

            case TUTORIAL_ARRIVAL_HEADER_SLIDING_PANEL:
                title = r.getString(R.string.tutorial_arrival_header_sliding_panel_title);
                text = new SpannableString(
                        r.getString(R.string.tutorial_arrival_header_sliding_panel_text));
                targetView = activity.findViewById(R.id.expand_collapse);
                break;

            case TUTORIAL_ARRIVAL_SORT:
                title = r.getString(R.string.tutorial_arrival_sort_title);
                text = new SpannableString(r.getString(R.string.tutorial_arrival_sort_text));
                addIcon(activity, text, R.drawable.ic_action_content_sort);
                break;

            case TUTORIAL_ARRIVAL_HEADER_STAR_ROUTE:
                if (response == null || response.getArrivalInfo().length < 1) return;

                title = r.getString(R.string.tutorial_arrival_header_star_route_title);
                text = new SpannableString(
                        r.getString(R.string.tutorial_arrival_header_star_route_text));
                targetView = activity.findViewById(R.id.eta_route_favorite);
                break;

            case TUTORIAL_RECENT_STOPS_ROUTES:
                title = r.getString(R.string.tutorial_recent_stops_routes_title);
                text = new SpannableString(
                        r.getString(R.string.tutorial_recent_stops_routes_text));
                addIcon(activity, text, R.drawable.ic_navigation_more_vert);
                break;

            case TUTORIAL_STARRED_STOPS_SORT:
                title = r.getString(R.string.tutorial_starred_stops_sort_title);
                text = new SpannableString(
                        r.getString(R.string.tutorial_starred_stops_sort_text));
                addIcon(activity, text, R.drawable.ic_action_content_sort);
                break;

            case TUTORIAL_TRIP_PLAN_GEOCODER:
                title = r.getString(R.string.tutorial_trip_plan_geocoder_title);
                text = new SpannableString(
                        r.getString(R.string.tutorial_trip_plan_geocoder_text));
                targetView = activity.findViewById(R.id.toAddressTextArea);
                break;

            default:
                return;
        }

        MaterialShowcaseView.Builder builder =
                new MaterialShowcaseView.Builder(activity)
                        .setTitleText(title)
                        .setContentText(text.toString())
                        .setDismissText(activity.getString(R.string.ok));

        if (targetView != null) {
            builder.setTarget(targetView);
        }

        mShowcaseView = builder.build();
        mShowcaseView.show(activity);

        doNotShowTutorial(tutorialType);
    }

    public static boolean isShowcaseViewShowing() {
        return mShowcaseView != null;
    }

    public static void hideShowcaseView() {
        if (mShowcaseView != null) {
            mShowcaseView.dismiss();
        }
    }

    private static boolean giveUserTutorialBreak(Context context, String tutorialType) {
        final String TUTORIAL_COUNTER = context.getString(R.string.preference_key_tutorial_counter);

        int counter = Application.getPrefs().getInt(TUTORIAL_COUNTER, 0);
        counter++;

        PreferenceUtils.saveInt(TUTORIAL_COUNTER, counter);

        return !(counter % 10 == 0);
    }

    public static void doNotShowTutorial(String tutorialType) {
        PreferenceUtils.saveBoolean(tutorialType, true);
    }

    private static void addIcon(Context context, SpannableString text,
                                @DrawableRes int drawableResource) {

        Drawable d = ResourcesCompat.getDrawable(context.getResources(),
                drawableResource, context.getTheme());

        if (d == null) return;

        d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());

        d.setColorFilter(context.getResources().getColor(R.color.header_text_color),
                PorterDuff.Mode.SRC_IN);

        ImageSpan imageSpan = new ImageSpan(d, ImageSpan.ALIGN_BOTTOM);

        text.setSpan(imageSpan, text.length() - 1, text.length(),
                Spannable.SPAN_INCLUSIVE_INCLUSIVE);
    }
}