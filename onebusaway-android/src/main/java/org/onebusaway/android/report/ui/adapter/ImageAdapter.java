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
package org.onebusaway.android.report.ui.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.onebusaway.android.R;

/**
 * @author Cagri Cetin
 */
public class ImageAdapter extends PagerAdapter {
    // Declare Variables
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
        return view == ((LinearLayout) object);
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

        Drawable drawable = context.getResources().getDrawable(images[position]);
        Bitmap btm = BitmapFactory.decodeResource(context.getResources(), images[position]);
//        btm = scaleImageToFit(btm);
        imageView.setImageBitmap(btm);
        ((ViewPager) container).addView(itemView);

        return itemView;
    }

    private Spanned convertStringToHtml(String text) {
        return Html.fromHtml("<body style=\"text-align:center; \">" + text + "</body>");
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        ((ViewPager) container).removeView((RelativeLayout) object);
    }

    private Bitmap scaleImageToFit(Bitmap image) {
        int nh = (int) (image.getHeight() * (512.0 / image.getWidth()));
        Bitmap scaled = Bitmap.createScaledBitmap(image, 512, nh, true);
        return scaled;
    }

}