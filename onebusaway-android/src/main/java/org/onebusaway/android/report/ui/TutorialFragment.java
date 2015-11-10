/*
* Copyright (C) 2014 University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.report.ui;

import org.onebusaway.android.R;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.report.ui.adapter.ImageAdapter;

import android.content.res.TypedArray;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * TutorialFragment is a general fragment that shows tutorial images
 * from given image and string resources
 *
 * Example usage:
 *
 * TutorialFragment tutorialFragment = new TutorialFragment();
 * Bundle bundle = new Bundle();
 * // Set string resources from arrays
 * bundle.putInt(TutorialFragment.STRING_RESOURCE_ID, R.array.report_stop_issue_tutorial_desc);
 * * // Set image resources from arrays
 * bundle.putInt(TutorialFragment.IMAGE_RESOURCE_ID, R.array.report_stop_issue_tutorial_images);
 * tutorialFragment.setArguments(bundle);
 *
 *
 */
public class TutorialFragment extends BaseReportFragment implements
        View.OnClickListener, ViewPager.OnPageChangeListener {

    public static final String STRING_RESOURCE_ID = "stringResource";
    public static final String IMAGE_RESOURCE_ID = "imageResource";

    int[] images;
    private Button pagerDone;
    private Button pagerPrev;
    private Button pagerNext;
    private ViewPager viewPager;
    int index = 0;

    /**
     * Array of resources
     */
    private int stringArrayResourceId;

    private int imageArrayResourceId;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        stringArrayResourceId = getArguments().getInt(STRING_RESOURCE_ID, -1);

        imageArrayResourceId = getArguments().getInt(IMAGE_RESOURCE_ID, -1);

        return inflater.inflate(R.layout.report_stop_problem_tutorial, null, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        ObaAnalytics.reportFragmentStart(this);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        TypedArray typedArray = getResources().obtainTypedArray(imageArrayResourceId);
        String[] texts = getResources().getStringArray(stringArrayResourceId);
        images = new int[typedArray.length()];
        for (int i = 0; i < typedArray.length(); i++) {
            images[i] = typedArray.getResourceId(i, -1);
        }

        updatePagerIndicator(0, images.length);

        viewPager = (ViewPager) findViewById(R.id.pager);
        PagerAdapter adapter = new ImageAdapter(getActivity(), images, texts);
        viewPager.setOffscreenPageLimit(3);
        viewPager.setAdapter(adapter);
        viewPager.addOnPageChangeListener(this);
        pagerDone = ((Button) findViewById(R.id.pager_button_done));
        pagerNext = ((Button) findViewById(R.id.pager_button_next));
        pagerPrev = ((Button) findViewById(R.id.pager_button_prev));
        pagerDone.setOnClickListener(this);
        pagerPrev.setOnClickListener(this);
        pagerNext.setOnClickListener(this);
    }

    @Override
    public void onPageScrolled(int i, float v, int i2) {

    }

    @Override
    public void onPageSelected(int i) {
        index = i;
        updatePagerIndicator(i, images.length);
        updateNavigationButtons(i, images.length);
        if (i == images.length - 1)
            pagerDone.setEnabled(true);
    }

    @Override
    public void onPageScrollStateChanged(int i) {

    }

    @Override
    public void onClick(View view) {
        if (view == pagerDone) {
            getActivity().onBackPressed();
        } else if (view == pagerNext) {
            viewPager.setCurrentItem(++index,true);
        } else if (view == pagerPrev) {
            viewPager.setCurrentItem(--index,true);
        }
    }

    private void updatePagerIndicator(int position, int size) {
        LinearLayout linear = (LinearLayout) findViewById(R.id.pager_indicator);
        linear.removeAllViewsInLayout();

        for (int i = 0; i < size; i++) {
            ImageView iw = new ImageView(getActivity());
            if (position == i) {
                iw.setImageResource(R.drawable.pager_dot_hover);
            }
            else {
                iw.setImageResource(R.drawable.pager_dot);
            }

            iw.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) iw.getLayoutParams();
            params.setMargins(5, 0, 0, 0);
            iw.setLayoutParams(params);

            linear.addView(iw);
        }
    }

    private void updateNavigationButtons(int i, int size) {
        if (i != size - 1 && i == 0){
            pagerPrev.setVisibility(View.GONE);
            pagerDone.setVisibility(View.GONE);
            pagerNext.setVisibility(View.VISIBLE);
        } else if (i == size - 1){
            pagerPrev.setVisibility(View.VISIBLE);
            pagerDone.setVisibility(View.VISIBLE);
            pagerNext.setVisibility(View.GONE);
        } else {
            pagerPrev.setVisibility(View.VISIBLE);
            pagerDone.setVisibility(View.GONE);
            pagerNext.setVisibility(View.VISIBLE);
        }
    }
}
