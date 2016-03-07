package org.onebusaway.android.util;

import com.github.amlcurran.showcaseview.OnShowcaseEventListener;
import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.targets.Target;
import com.github.amlcurran.showcaseview.targets.ViewTarget;

import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.request.ObaArrivalInfoResponse;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.DrawableRes;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

/**
 * A class containing utility methods related to showing a tutorial to users for how to use various
 * OBA features, using the ShowcaseView library (https://github.com/amlcurran/ShowcaseView).
 */
public class ShowcaseViewUtils {

    public static final String TUTORIAL_WELCOME = ".tutorial_welcome";

    public static final String TUTORIAL_ARRIVAL_HEADER_ARRIVAL_INFO
            = ".tutorial_arrival_header_arrival_info";

    public static final String TUTORIAL_ARRIVAL_HEADER_STAR_STOP
            = ".tutorial_arrival_header_star_stop";

    public static final String TUTORIAL_ARRIVAL_HEADER_SLIDING_PANEL
            = ".tutorial_arrival_header_sliding_panel";

    public static final String TUTORIAL_ARRIVAL_INFO_MORE = ".tutorial_arrival_info_more";

    public static final String TUTORIAL_ARRIVAL_SORT = ".tutorial_arrival_sort";

    public static final String TUTORIAL_ARRIVAL_HEADER_STAR_ROUTE
            = ".tutorial_arrival_header_star_route";

    public static final String TUTORIAL_RECENT_STOPS_ROUTES = ".tutorial_recent_stops_routes";

    public static final String TUTORIAL_ARRIVAL_STYLE_B_SHOW_ROUTE
            = ".tutorial_arrival_style_b_show_route_items";

    public static final String TUTORIAL_VEHICLE_ICONS = ".tutorial_vehicle_icons";

    public static final String TUTORIAL_VEHICLE_INFO_WINDOW = ".tutorial_vehicle_info_window";

    public static final String TUTORIAL_STARRED_STOPS_SORT = ".tutorial_starred_stops_sort";

    public static final String TUTORIAL_STARRED_STOPS_SHORTCUT = ".tutorial_starred stops_shortcut";

    public static final String TUTORIAL_SHOW_ARRIVAL_IN_HEADER
            = ".tutorial_show_arrivals_in_header";

    public static final String TUTORIAL_ARRIVAL_ROUTE_FILTER = ".tutorial_arrival_route_filter";

    public static final String TUTORIAL_NIGHT_LIGHT = ".tutorial_night_light";

    private static ShowcaseView mShowcaseView;

    /**
     * Returns true if this API level supports the ShowcaseView library tutorials, false if it does
     * not
     *
     * @return true if this API level supports the ShowcaseView library tutorials, false if it does
     * not
     */
    public static boolean supportsShowcaseView() {
        // ShowcaseView only works on API Level >= 11
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
    }

    /**
     * Shows the tutorial for the specified tutorialType.  This method handles checking to see if
     * other ShowcaseViews are already being shown, as well as if this tutorial has already been
     * shown - if either of these cases are true, this method is a no-op.
     *
     * @param tutorialType type of tutorial to show, defined by the TUTORIAL_* constants in
     *                     ShowcaseViewUtils
     * @param activity     activity used to show the tutorial
     * @param response     The response that contains arrival info, or null if this is not available.
     *                     Some tutorials require that arrival info is showing - these tutorials
     *                     will only be displayed if arrival info is provided in this parameter.
     */
    public synchronized static void showTutorial(String tutorialType,
            final AppCompatActivity activity, final ObaArrivalInfoResponse response) {
        if (!supportsShowcaseView() || activity == null) {
            return;
        }
        if (isShowcaseViewShowing()
                && !tutorialType.equals(TUTORIAL_ARRIVAL_HEADER_SLIDING_PANEL)) {
            // Only tutorials that are chained (fired from listeners when another ShowcaseView
            // closes) should pass this point - otherwise, return
            return;
        }

        SharedPreferences settings = Application.getPrefs();
        boolean showedTutorial = settings.getBoolean(tutorialType, false);
        if (showedTutorial) {
            // If we've already shown this tutorial to the user, do nothing
            return;
        }

        Resources r = activity.getResources();
        OnShowcaseEventListener listener = null;
        boolean moveButtonLeft = false;

        String title;
        SpannableString text;
        Target target = Target.NONE;

        switch (tutorialType) {
            case TUTORIAL_WELCOME:
                title = r.getString(R.string.tutorial_welcome_title);
                text = new SpannableString(r.getString(R.string.tutorial_welcome_text));
                break;
            case TUTORIAL_ARRIVAL_HEADER_ARRIVAL_INFO:
                if (response == null) {
                    throw new IllegalArgumentException(
                            "ObaArrivalInfoResponse must be provided for the '" + tutorialType
                                    + "' tutorial type.");
                }
                if (response.getArrivalInfo().length < 1) {
                    // We need at least one arrival time
                    return;
                }
                title = r.getString(R.string.tutorial_arrival_header_arrival_info_title);
                text = new SpannableString(
                        r.getString(R.string.tutorial_arrival_header_arrival_info_text));
                target = new ViewTarget(R.id.eta_and_min, activity);
                moveButtonLeft = true;
                listener = new OnShowcaseEventListener() {
                    @Override
                    public void onShowcaseViewHide(ShowcaseView showcaseView) {
                        showTutorial(TUTORIAL_ARRIVAL_HEADER_SLIDING_PANEL, activity, response);
                    }

                    @Override
                    public void onShowcaseViewDidHide(ShowcaseView showcaseView) {
                    }

                    @Override
                    public void onShowcaseViewShow(ShowcaseView showcaseView) {
                    }
                };
                break;
            case TUTORIAL_ARRIVAL_HEADER_STAR_STOP:
                title = r.getString(R.string.tutorial_arrival_header_star_stop_title);
                text = new SpannableString(
                        r.getString(R.string.tutorial_arrival_header_star_stop_text));
                target = new ViewTarget(R.id.stop_favorite, activity);
                break;
            case TUTORIAL_ARRIVAL_HEADER_SLIDING_PANEL:
                title = r.getString(R.string.tutorial_arrival_header_sliding_panel_title);
                text = new SpannableString(
                        r.getString(R.string.tutorial_arrival_header_sliding_panel_text));
                target = new ViewTarget(R.id.expand_collapse, activity);
                moveButtonLeft = true;
                break;
            case TUTORIAL_ARRIVAL_INFO_MORE:
                if (response == null) {
                    throw new IllegalArgumentException(
                            "ObaArrivalInfoResponse must be provided for the '" + tutorialType
                                    + "' tutorial type.");
                }
                if (response.getArrivalInfo().length < 1) {
                    // We need at least one arrival time
                    return;
                }
                title = r.getString(R.string.tutorial_arrival_info_more_title);
                text = new SpannableString(
                        r.getString(R.string.tutorial_arrival_info_more_text));
                target = new ViewTarget(R.id.eta_more_vert, activity);
                moveButtonLeft = true;
                break;
            case TUTORIAL_ARRIVAL_SORT:
                title = r.getString(R.string.tutorial_arrival_sort_title);
                text = new SpannableString(r.getString(R.string.tutorial_arrival_sort_text));
                addIcon(activity, text, R.drawable.ic_action_content_sort);
                break;
            case TUTORIAL_ARRIVAL_HEADER_STAR_ROUTE:
                if (response == null) {
                    throw new IllegalArgumentException(
                            "ObaArrivalInfoResponse must be provided for the '" + tutorialType
                                    + "' tutorial type.");
                }
                if (response.getArrivalInfo().length < 1) {
                    // We need at least one arrival time
                    return;
                }
                title = r.getString(R.string.tutorial_arrival_header_star_route_title);
                text = new SpannableString(
                        r.getString(R.string.tutorial_arrival_header_star_route_text));
                target = new ViewTarget(R.id.eta_route_favorite, activity);
                break;
            case TUTORIAL_RECENT_STOPS_ROUTES:
                title = r.getString(R.string.tutorial_recent_stops_routes_title);
                text = new SpannableString(
                        r.getString(R.string.tutorial_recent_stops_routes_text));
                addIcon(activity, text, R.drawable.ic_navigation_more_vert);
                break;
            case TUTORIAL_ARRIVAL_STYLE_B_SHOW_ROUTE:
                if (response == null) {
                    throw new IllegalArgumentException(
                            "ObaArrivalInfoResponse must be provided for the '" + tutorialType
                                    + "' tutorial type.");
                }
                if (response.getArrivalInfo().length < 1) {
                    // We need at least one arrival time
                    return;
                }
                title = r.getString(R.string.tutorial_arrival_style_b_show_route_title);
                text = new SpannableString(
                        r.getString(R.string.tutorial_arrival_style_b_show_route_text));
                target = new ViewTarget(R.id.mapImageBtn, activity);
                moveButtonLeft = true;
                break;
            case TUTORIAL_VEHICLE_ICONS:
                title = r.getString(R.string.tutorial_vehicle_icons_title);
                text = new SpannableString(r.getString(R.string.tutorial_vehicle_icons_text));
                // No target, as we can't target map markers
                break;
            case TUTORIAL_VEHICLE_INFO_WINDOW:
                title = r.getString(R.string.tutorial_vehicle_info_window_title);
                text = new SpannableString(r.getString(R.string.tutorial_vehicle_info_window_text));
                // No target, as we can't target custom map info window contents
                break;
            case TUTORIAL_STARRED_STOPS_SORT:
                title = r.getString(R.string.tutorial_starred_stops_sort_title);
                text = new SpannableString(r.getString(R.string.tutorial_starred_stops_sort_text));
                addIcon(activity, text, R.drawable.ic_action_content_sort);
                break;
            case TUTORIAL_STARRED_STOPS_SHORTCUT:
                if (BuildConfig.FLAVOR_platform
                        .equalsIgnoreCase(BuildFlavorUtils.AMAZON_FLAVOR_PLATFORM)) {
                    // Amazon doesn't support shortcuts - see #419
                    return;
                }
                title = r.getString(R.string.tutorial_starred_stops_shortcut_title);
                text = new SpannableString(
                        r.getString(R.string.tutorial_starred_stops_shortcut_text));
                break;
            case TUTORIAL_SHOW_ARRIVAL_IN_HEADER:
                title = r.getString(R.string.tutorial_show_arrivals_in_header_title);
                text = new SpannableString(
                        r.getString(R.string.tutorial_show_arrivals_in_header_text));
                addIcon(activity, text, R.drawable.ic_navigation_more_vert);
                break;
            case TUTORIAL_ARRIVAL_ROUTE_FILTER:
                title = r.getString(R.string.tutorial_arrival_route_filter_title);
                text = new SpannableString(
                        r.getString(R.string.tutorial_arrival_route_filter_text));
                addIcon(activity, text, R.drawable.ic_navigation_more_vert);
                break;
            case TUTORIAL_NIGHT_LIGHT:
                title = r.getString(R.string.tutorial_night_light_title);
                text = new SpannableString(
                        r.getString(R.string.tutorial_night_light_text));
                addIcon(activity, text, R.drawable.ic_navigation_more_vert);
                break;
            default:
                throw new IllegalArgumentException(
                        "tutorialType must be one of the TUTORIAL_* constants in ShowcaseViewUtils");
        }

        mShowcaseView = new ShowcaseView.Builder(activity)
                .setTarget(target)
                .setStyle(R.style.CustomShowcaseTheme)
                .setContentTitle(title)
                .setContentText(text)
                .build();
        // If button should be positioned to the left, then set the parameters
        if (moveButtonLeft) {
            moveButtonLeft(activity, mShowcaseView);
        }
        if (listener != null) {
            mShowcaseView.setOnShowcaseEventListener(listener);
        }

        // Set the preference for this tutorial type so it doesn't show again
        PreferenceUtils.saveBoolean(tutorialType, true);
    }

    /**
     * Returns true if the ShowcaseView is currently showing, false if it is not
     *
     * @return true if the ShowcaseView is currently showing, false if it is not
     */
    public static boolean isShowcaseViewShowing() {
        return mShowcaseView != null && mShowcaseView.isShowing();
    }

    /**
     * Adds the provided icon to the right side of the provided SpannableString
     *
     * @param text             SpannableString to add sort icon to
     * @param drawableResource ID of the drawable resource to add to the right side of the
     *                         SpannableString
     */
    private static void addIcon(Context context, SpannableString text,
            @DrawableRes int drawableResource) {
        Drawable d = ResourcesCompat.getDrawable(context.getResources(),
                drawableResource, context.getTheme());
        d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        d.setColorFilter(context.getResources().getColor(R.color.header_text_color),
                PorterDuff.Mode.SRC_IN);
        ImageSpan imageSpan = new ImageSpan(d, ImageSpan.ALIGN_BOTTOM);
        text.setSpan(imageSpan, text.length() - 1, text.length(),
                Spannable.SPAN_INCLUSIVE_INCLUSIVE);
    }

    /**
     * Moves the button to acknowledge the ShowcaseView to be left aligned
     *
     * @param v ShowcaseView for which the button should be left aligned
     */
    private static void moveButtonLeft(Context context, ShowcaseView v) {
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        int p = UIUtils.dpToPixels(context, 12);
        lp.setMargins(p, p, p, p);
        v.setButtonPosition(lp);
    }

    /**
     * Resets all tutorials so they are shown to the user again
     */
    public static void resetAllTutorials() {
        PreferenceUtils.saveBoolean(TUTORIAL_WELCOME, false);
        PreferenceUtils.saveBoolean(TUTORIAL_ARRIVAL_HEADER_ARRIVAL_INFO, false);
        PreferenceUtils.saveBoolean(TUTORIAL_ARRIVAL_HEADER_STAR_STOP, false);
        PreferenceUtils.saveBoolean(TUTORIAL_ARRIVAL_HEADER_SLIDING_PANEL, false);
        PreferenceUtils.saveBoolean(TUTORIAL_ARRIVAL_INFO_MORE, false);
        PreferenceUtils.saveBoolean(TUTORIAL_ARRIVAL_SORT, false);
        PreferenceUtils.saveBoolean(TUTORIAL_ARRIVAL_HEADER_STAR_ROUTE, false);
        PreferenceUtils.saveBoolean(TUTORIAL_RECENT_STOPS_ROUTES, false);
        PreferenceUtils.saveBoolean(TUTORIAL_ARRIVAL_STYLE_B_SHOW_ROUTE, false);
        PreferenceUtils.saveBoolean(TUTORIAL_VEHICLE_ICONS, false);
        PreferenceUtils.saveBoolean(TUTORIAL_VEHICLE_INFO_WINDOW, false);
        PreferenceUtils.saveBoolean(TUTORIAL_STARRED_STOPS_SORT, false);
        PreferenceUtils.saveBoolean(TUTORIAL_STARRED_STOPS_SHORTCUT, false);
    }
}
