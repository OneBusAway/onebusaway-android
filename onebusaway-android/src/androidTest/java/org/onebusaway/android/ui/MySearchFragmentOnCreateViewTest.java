/*
 * Copyright (C) 2026 OneBusAway
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
package org.onebusaway.android.ui;

import android.app.Instrumentation;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.onebusaway.android.R;

import java.util.concurrent.atomic.AtomicReference;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import static junit.framework.Assert.assertNotNull;

/**
 * Regression test for issue #1564 — launcher shortcuts crash because
 * {@link MySearchStopsFragment} and {@link MySearchRoutesFragment} returned a
 * null view when their container was null. ViewPager2's FragmentStateAdapter
 * always passes a null container, so any tabbed activity hosting these
 * fragments crashed with "Content view not yet created" when the fragment's
 * lifecycle advanced.
 */
@RunWith(AndroidJUnit4.class)
public class MySearchFragmentOnCreateViewTest {

    private Instrumentation mInstrumentation;

    private LayoutInflater mInflater;

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        Context target = mInstrumentation.getTargetContext();
        Context themed = new ContextThemeWrapper(target, R.style.Theme_OneBusAway_NoActionBar);
        mInflater = LayoutInflater.from(themed);
    }

    @Test
    public void mySearchStopsFragment_onCreateView_returnsViewWhenContainerNull() {
        View view = inflateOnMainThread(MySearchStopsFragment.class, null);
        assertNotNull(
                "MySearchStopsFragment must inflate a view when ViewPager2 passes a null container",
                view);
    }

    @Test
    public void mySearchRoutesFragment_onCreateView_returnsViewWhenContainerNull() {
        View view = inflateOnMainThread(MySearchRoutesFragment.class, null);
        assertNotNull(
                "MySearchRoutesFragment must inflate a view when ViewPager2 passes a null container",
                view);
    }

    @Test
    public void mySearchStopsFragment_onCreateView_returnsViewWhenContainerNonNull() {
        View view = inflateOnMainThread(MySearchStopsFragment.class, newContainer());
        assertNotNull(
                "MySearchStopsFragment must inflate a view when hosted by a FragmentContainerView",
                view);
    }

    @Test
    public void mySearchRoutesFragment_onCreateView_returnsViewWhenContainerNonNull() {
        View view = inflateOnMainThread(MySearchRoutesFragment.class, newContainer());
        assertNotNull(
                "MySearchRoutesFragment must inflate a view when hosted by a FragmentContainerView",
                view);
    }

    private ViewGroup newContainer() {
        final AtomicReference<ViewGroup> ref = new AtomicReference<>();
        mInstrumentation.runOnMainSync(() -> ref.set(new FrameLayout(mInflater.getContext())));
        return ref.get();
    }

    /**
     * Construct the fragment and call {@code onCreateView} on the main thread. Both steps need a
     * Looper — the fragments extend {@link ListFragment}, which instantiates a {@link
     * android.os.Handler} in its constructor.
     */
    private <T extends MySearchFragmentBase> View inflateOnMainThread(final Class<T> fragmentClass,
            final ViewGroup container) {
        final AtomicReference<View> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        mInstrumentation.runOnMainSync(() -> {
            try {
                T fragment = fragmentClass.getDeclaredConstructor().newInstance();
                result.set(fragment.onCreateView(mInflater, container, null));
            } catch (Throwable t) {
                error.set(t);
            }
        });
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
        return result.get();
    }
}
