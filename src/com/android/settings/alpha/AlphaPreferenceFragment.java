/*
 * SPDX-FileCopyrightText: 2026 AlphaDroid
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.alpha;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.widget.LayoutPreference;

import java.util.ArrayList;
import java.util.Arrays;

public abstract class AlphaPreferenceFragment extends SettingsPreferenceFragment {

    private static final String PAGE_VIEWER_SELECTION_INDEX = "page_viewer_selection_index";

    private static final int DOT_INDICATOR_SIZE = 12;
    private static final int DOT_INDICATOR_LEFT_PADDING = 6;
    private static final int DOT_INDICATOR_RIGHT_PADDING = 6;

    private View mViewArrowPrevious;
    private View mViewArrowNext;
    private ViewPager mViewPager;

    private ArrayList<View> mPageList;
    private ImageView[] mDotIndicators;
    private View[] mViewPagerImages;

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mViewPager != null) {
            outState.putInt(PAGE_VIEWER_SELECTION_INDEX, mViewPager.getCurrentItem());
        }
    }

    /**
     * Call this from your child fragment's onCreate/onCreatePreferences
     * after finding the LayoutPreference from the screen.
     */
    protected void configurePreviewPager(LayoutPreference preview, Bundle savedInstanceState) {
        if (preview == null) return;

        preview.setSelectable(false);
        final ArrayList<Integer> layoutList = getPreviewLayouts();

        mViewPager = preview.findViewById(R.id.viewpager);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        mViewPagerImages = new View[layoutList.size()];
        for (int idx = 0; idx < layoutList.size(); idx++) {
            mViewPagerImages[idx] = inflater.inflate(layoutList.get(idx), null /* root */);
        }

        mPageList = new ArrayList<>(Arrays.asList(mViewPagerImages));
        mViewPager.setAdapter(new ColorPagerAdapter(mPageList));

        mViewArrowPrevious = preview.findViewById(R.id.arrow_previous);
        if (mViewArrowPrevious != null) {
            mViewArrowPrevious.setOnClickListener(v -> {
                final int previousPos = mViewPager.getCurrentItem() - 1;
                mViewPager.setCurrentItem(previousPos, true);
            });
        }

        mViewArrowNext = preview.findViewById(R.id.arrow_next);
        if (mViewArrowNext != null) {
            mViewArrowNext.setOnClickListener(v -> {
                final int nextPos = mViewPager.getCurrentItem() + 1;
                mViewPager.setCurrentItem(nextPos, true);
            });
        }

        mViewPager.addOnPageChangeListener(createPageListener());

        final ViewGroup viewGroup = preview.findViewById(R.id.viewGroup);
        if (viewGroup != null) {
            mDotIndicators = new ImageView[mPageList.size()];
            for (int i = 0; i < mPageList.size(); i++) {
                final ImageView imageView = new ImageView(getContext());
                final ViewGroup.MarginLayoutParams lp =
                        new ViewGroup.MarginLayoutParams(DOT_INDICATOR_SIZE, DOT_INDICATOR_SIZE);
                lp.setMargins(DOT_INDICATOR_LEFT_PADDING, 0, DOT_INDICATOR_RIGHT_PADDING, 0);
                imageView.setLayoutParams(lp);
                mDotIndicators[i] = imageView;
                viewGroup.addView(mDotIndicators[i]);
            }
        }

        int startPosition = 0;
        if (savedInstanceState != null) {
            startPosition = savedInstanceState.getInt(PAGE_VIEWER_SELECTION_INDEX, 0);
            mViewPager.setCurrentItem(startPosition);
        }
        updateIndicator(startPosition);
    }

    /**
     * Override this in child fragments if you need different layout files for the ViewPager.
     */
    protected ArrayList<Integer> getPreviewLayouts() {
        return new ArrayList<>(
                Arrays.asList(
                        R.layout.color_mode_view1,
                        R.layout.color_mode_view2,
                        R.layout.color_mode_view3));
    }

    private ViewPager.OnPageChangeListener createPageListener() {
        return new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                if (positionOffset == 0) {
                    if (mViewPagerImages != null && position < mViewPagerImages.length) {
                        mViewPagerImages[position].setContentDescription(
                                getContext().getString(R.string.colors_viewpager_content_description));
                    }
                    updateIndicator(position);
                }
            }

            @Override
            public void onPageSelected(int position) {}

            @Override
            public void onPageScrollStateChanged(int state) {}
        };
    }

    private void updateIndicator(int position) {
        if (mDotIndicators == null || mPageList == null) return;

        for (int i = 0; i < mPageList.size(); i++) {
            if (position == i) {
                mDotIndicators[i].setBackgroundResource(R.drawable.ic_color_page_indicator_focused);
            } else {
                mDotIndicators[i].setBackgroundResource(R.drawable.ic_color_page_indicator_unfocused);
            }
        }

        if (mViewArrowPrevious != null && mViewArrowNext != null) {
            mViewArrowPrevious.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
            mViewArrowNext.setVisibility(position == (mPageList.size() - 1) ? View.INVISIBLE : View.VISIBLE);
        }
    }

    private static class ColorPagerAdapter extends PagerAdapter {
        private final ArrayList<View> mPageViewList;

        ColorPagerAdapter(ArrayList<View> pageViewList) {
            mPageViewList = pageViewList;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            if (position < mPageViewList.size() && mPageViewList.get(position) != null) {
                container.removeView(mPageViewList.get(position));
            }
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            View view = mPageViewList.get(position);
            ViewPager.LayoutParams params = new ViewPager.LayoutParams();
            params.width = ViewPager.LayoutParams.MATCH_PARENT;
            params.height = ViewPager.LayoutParams.MATCH_PARENT;

            if (view.getParent() != null) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
            container.addView(view, params);
            return view;
        }

        @Override
        public int getCount() {
            return mPageViewList.size();
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return object == view;
        }
    }
}