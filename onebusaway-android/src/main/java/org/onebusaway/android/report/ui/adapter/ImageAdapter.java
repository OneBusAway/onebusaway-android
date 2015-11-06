/*
* Copyright (C) 2015 University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.report.ui.adapter;

import org.onebusaway.android.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.view.PagerAdapter;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class ImageAdapter extends PagerAdapter {
    Context context;
    int[] images;
    String[] texts;
    LayoutInflater inflater;

    public ImageAdapter(Context context, int[] images, String[] texts) {
        this.context = context;
        this.images = images;
        this.texts = texts;
    }

    @Override
    public int getCount() {
        return images.length;
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == object;
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        ImageView imageView;

        inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View itemView = inflater.inflate(R.layout.report_stop_problem_tutorial_item, container,
                false);

        imageView = (ImageView) itemView.findViewById(R.id.rsp_imageView);

        Spanned html = convertStringToHtml(texts[position]);
        ((TextView) itemView.findViewById(R.id.rsp_textView)).setText(html);

        Bitmap btm = BitmapFactory.decodeResource(context.getResources(), images[position]);
        btm = scaleImageIfNecessary(btm);
        imageView.setImageBitmap(btm);
        container.addView(itemView);

        return itemView;
    }

    private Bitmap scaleImageIfNecessary(Bitmap btm) {
        // 4K screen sizes
        int maxWidth = 1440;
        int maxHeight = 2560;

        int imageWidth = btm.getWidth();
        int imageHeight = btm.getHeight();

        // Calculate scale ratio
        double scaleRatioDouble = (double) imageHeight / maxHeight;
        int scaleRatio = (int) Math.ceil(scaleRatioDouble);

        if (scaleRatio == 1 || imageHeight * imageWidth < maxHeight * maxWidth) {
            // Don't scale image if it is not necessary
            return btm;
        } else {
            return Bitmap.createScaledBitmap(btm, btm.getWidth() / scaleRatio,
                    btm.getHeight() / scaleRatio, true);
        }
    }

    private Spanned convertStringToHtml(String text) {
        return Html.fromHtml("<body style=\"text-align:center; \">" + text + "</body>");
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((RelativeLayout) object);
    }
}