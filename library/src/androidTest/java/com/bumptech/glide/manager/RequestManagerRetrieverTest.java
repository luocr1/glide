package com.bumptech.glide.manager;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.tests.BackgroundUtil;
import com.bumptech.glide.tests.GlideShadowLooper;
import com.bumptech.glide.tests.Util;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ActivityController;

import static com.bumptech.glide.tests.BackgroundUtil.testInBackground;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, emulateSdk = 18, shadows = GlideShadowLooper.class)
public class RequestManagerRetrieverTest {
    private static final String PARENT_TAG = "parent";
    private RetrieverHarness[] harnesses;
    private RequestManagerRetriever retriever;
    private int initialSdkVersion;

    @Before
    public void setUp() {
        retriever = new RequestManagerRetriever();

        harnesses = new RetrieverHarness[] { new DefaultRetrieverHarness(), new SupportRetrieverHarness() };

        initialSdkVersion = Build.VERSION.SDK_INT;
        Util.setSdkVersionInt(18);
    }

    @After
    public void tearDown() {
        Util.setSdkVersionInt(initialSdkVersion);

        Robolectric.shadowOf(Looper.getMainLooper()).runToEndOfTasks();
        assertThat(retriever.pendingRequestManagerFragments.entrySet(), empty());
        assertThat(retriever.pendingSupportRequestManagerFragments.entrySet(), empty());
    }

    @Test
    public void testCreatesNewFragmentIfNoneExists() {
        for (RetrieverHarness harness : harnesses) {
            harness.doGet();

            Robolectric.shadowOf(Looper.getMainLooper()).runToEndOfTasks();
            assertTrue(harness.hasFragmentWithTag(RequestManagerRetriever.TAG));
        }
    }

    @Test
    public void testReturnsNewManagerIfNoneExists() {
        for (RetrieverHarness harness : harnesses) {
            assertNotNull(harness.doGet());
        }
    }

    @Test
    public void testReturnsExistingRequestManagerIfExists() {
        for (RetrieverHarness harness : harnesses) {
            RequestManager requestManager = mock(RequestManager.class);

            harness.addFragmentWithTag(RequestManagerRetriever.TAG, requestManager);

            assertEquals(requestManager, harness.doGet());
        }
    }

    @Test
    public void testReturnsNewRequestManagerIfFragmentExistsButHasNoRequestManager() {
        for (RetrieverHarness harness : harnesses) {
            harness.addFragmentWithTag(RequestManagerRetriever.TAG, null);

            assertNotNull(harness.doGet());
        }
    }

    @Test
    public void testSavesNewRequestManagerToFragmentIfCreatesRequestManagerForExistingFragment() {
        for (RetrieverHarness harness : harnesses) {
            harness.addFragmentWithTag(RequestManagerRetriever.TAG, null);
            RequestManager first = harness.doGet();
            RequestManager second = harness.doGet();

            assertEquals(first, second);
        }
    }

    @Test
    public void testHasValidTag() {
        assertEquals(RequestManagerRetriever.class.getPackage().getName(), RequestManagerRetriever.TAG);
    }

    @Test
    public void testCanGetRequestManagerFromActivity() {
        Activity activity = Robolectric.buildActivity(Activity.class).create().start().get();
        RequestManager manager = retriever.get(activity);
        assertEquals(manager, retriever.get(activity));
    }

    @Test
    public void testSupportCanGetRequestManagerFromActivity() {
        FragmentActivity fragmentActivity = Robolectric.buildActivity(FragmentActivity.class).create().start().get();
        RequestManager manager = retriever.get(fragmentActivity);
        assertEquals(manager, retriever.get(fragmentActivity));
    }

    @Test
    public void testCanGetRequestManagerFromFragment() {
        Activity activity = Robolectric.buildActivity(Activity.class).create().start().resume().get();
        android.app.Fragment fragment = new android.app.Fragment();
        activity.getFragmentManager()
                .beginTransaction()
                .add(fragment, PARENT_TAG)
                .commit();
        activity.getFragmentManager().executePendingTransactions();

        RequestManager manager = retriever.get(fragment);
        assertEquals(manager, retriever.get(fragment));
    }

    @Test
    public void testSupportCanGetRequestManagerFromFragment() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).create().start().resume().get();
        Fragment fragment = new Fragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(fragment, PARENT_TAG)
                .commit();
        activity.getSupportFragmentManager().executePendingTransactions();

        RequestManager manager = retriever.get(fragment);
        assertEquals(manager, retriever.get(fragment));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThrowsIfFragmentNotAttached() {
        android.app.Fragment fragment = new android.app.Fragment();
        retriever.get(fragment);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThrowsIfSupportFragmentNotAttached() {
        Fragment fragment = new Fragment();
        retriever.get(fragment);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThrowsIfActivityDestroyed() {
        DefaultRetrieverHarness harness = new DefaultRetrieverHarness();
        harness.getController().pause().stop().destroy();
        harness.doGet();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThrowsIfFragmentActivityDestroyed() {
        SupportRetrieverHarness harness = new SupportRetrieverHarness();
        harness.getController().pause().stop().destroy();
        harness.doGet();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThrowsIfGivenNullContext() {
        retriever.get((Context) null);
    }

    @Test
    public void testChecksIfContextIsFragmentActivity() {
        SupportRetrieverHarness harness = new SupportRetrieverHarness();
        RequestManager requestManager = harness.doGet();

        assertEquals(requestManager, retriever.get((Context) harness.getController().get()));
    }

    @Test
    public void testChecksIfContextIsActivity() {
        DefaultRetrieverHarness harness = new DefaultRetrieverHarness();
        RequestManager requestManager = harness.doGet();

        assertEquals(requestManager, retriever.get((Context) harness.getController().get()));
    }

    @Test
    public void testHandlesContextWrappersForActivities() {
        DefaultRetrieverHarness harness = new DefaultRetrieverHarness();
        RequestManager requestManager = harness.doGet();
        ContextWrapper contextWrapper = new ContextWrapper((Context) harness.getController().get());

        assertEquals(requestManager, retriever.get(contextWrapper));
    }

    @Test
    public void testHandlesContextWrappersForApplication() {
        ContextWrapper contextWrapper = new ContextWrapper(Robolectric.application);
        RequestManager requestManager = retriever.get(Robolectric.application);

        assertEquals(requestManager, retriever.get(contextWrapper));
    }

    @Test
    public void testReturnsNonNullManagerIfGivenApplicationContext() {
        assertNotNull(retriever.get(Robolectric.application));
    }

    @Test
    public void testApplicationRequestManagerIsNotPausedWhenRetrieved() {
        RequestManager manager = retriever.get(Robolectric.application);
        assertFalse(manager.isPaused());
    }

    @Test
    public void testApplicationRequestManagerIsNotReResumedAfterFirstRetrieval() {
        RequestManager manager = retriever.get(Robolectric.application);
        manager.pauseRequests();
        manager = retriever.get(Robolectric.application);
        assertTrue(manager.isPaused());
    }

    @Test
    public void testDoesNotThrowWhenGetWithContextCalledFromBackgroundThread() throws InterruptedException {
        testInBackground(new BackgroundUtil.BackgroundTester() {
            @Override
            public void runTest() throws Exception {
                retriever.get(Robolectric.application);
            }
        });
    }

    // See Issue #117: https://github.com/bumptech/glide/issues/117.
    @Test
    public void testCanCallGetInOnAttachToWindowInFragmentInViewPager() {
        // Robolectric by default runs messages posted to the main looper synchronously, the framework does not. We post
        // to the main thread here to work around an issue caused by a recursive method call so we need (and reasonably
        // expect) our message to not run immediately
        Robolectric.shadowOf(Looper.getMainLooper()).pause();
        Robolectric.buildActivity(Issue117Activity.class).create().start().resume().visible();
    }

    @Test
    public void testDoesNotThrowIfAskedToGetManagerForActivityPreHoneycomb() {
        Util.setSdkVersionInt(Build.VERSION_CODES.GINGERBREAD_MR1);
        Activity activity = mock(Activity.class);
        when(activity.getApplicationContext()).thenReturn(Robolectric.application);
        when(activity.getFragmentManager()).thenThrow(new NoSuchMethodError());

        assertNotNull(retriever.get(activity));
    }

    @Test
    public void testDoesNotThrowIfAskedToGetManagerForActivityPreJellYBeanMr1() {
        Util.setSdkVersionInt(Build.VERSION_CODES.JELLY_BEAN);
        Activity activity = Robolectric.buildActivity(Activity.class).create().start().resume().get();
        Activity spyActivity = Mockito.spy(activity);
        when(spyActivity.isDestroyed()).thenThrow(new NoSuchMethodError());

        assertNotNull(retriever.get(spyActivity));
    }

    @Test
    public void testDoesNotThrowIfAskedToGetManagerForFragmentPreHoneyCombMr2() {
        Util.setSdkVersionInt(Build.VERSION_CODES.HONEYCOMB_MR1);
        Activity activity = Robolectric.buildActivity(Activity.class).create().start().resume().get();
        android.app.Fragment fragment = new android.app.Fragment();

        activity.getFragmentManager()
                .beginTransaction().add(fragment, "test")
                .commit();
        android.app.Fragment spyFragment = Mockito.spy(fragment);
        when(spyFragment.isDetached()).thenThrow(new NoSuchMethodError());

        assertNotNull(retriever.get(spyFragment));
    }

    @Test
    public void testDoesNotThrowIfAskedToGetManagerForFragmentPreJellyBeanMr1() {
        Util.setSdkVersionInt(Build.VERSION_CODES.JELLY_BEAN);
        Activity activity = Robolectric.buildActivity(Activity.class).create().start().resume().get();
        android.app.Fragment fragment = new android.app.Fragment();

        activity.getFragmentManager()
                .beginTransaction().add(fragment, "test")
                .commit();
        android.app.Fragment spyFragment = Mockito.spy(fragment);
        when(spyFragment.getChildFragmentManager()).thenThrow(new NoSuchMethodError());

        assertNotNull(retriever.get(spyFragment));
    }

    private interface RetrieverHarness {

        public ActivityController getController();

        public RequestManager doGet();

        public boolean hasFragmentWithTag(String tag);

        public void addFragmentWithTag(String tag, RequestManager manager);
    }

    public class DefaultRetrieverHarness implements RetrieverHarness {
        private final ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
        private final android.app.Fragment parent;

        public DefaultRetrieverHarness() {
            this.parent = new android.app.Fragment();

            controller.create();
            controller.get().getFragmentManager()
                .beginTransaction()
                .add(parent, PARENT_TAG)
                .commitAllowingStateLoss();
            controller.get().getFragmentManager().executePendingTransactions();
            controller.start().resume();
        }

        @Override
        public ActivityController getController() {
            return controller;
        }

        @Override
        public RequestManager doGet() {
            return retriever.get(controller.get());
        }

        @Override
        public boolean hasFragmentWithTag(String tag) {
            return controller.get().getFragmentManager().findFragmentByTag(RequestManagerRetriever.TAG) != null;
        }

        @Override
        public void addFragmentWithTag(String tag, RequestManager requestManager) {
            RequestManagerFragment fragment = new RequestManagerFragment();
            fragment.setRequestManager(requestManager);
            controller.get().getFragmentManager()
                    .beginTransaction()
                    .add(fragment, RequestManagerRetriever.TAG)
                    .commitAllowingStateLoss();
            controller.get().getFragmentManager().executePendingTransactions();
        }
    }

    public class SupportRetrieverHarness implements RetrieverHarness {
        private final ActivityController<FragmentActivity> controller = Robolectric.buildActivity(
                FragmentActivity.class);
        private final Fragment parent;

        public SupportRetrieverHarness() {
            this.parent = new Fragment();

            controller.create();
            controller.get().getSupportFragmentManager()
                    .beginTransaction()
                    .add(parent, PARENT_TAG)
                    .commitAllowingStateLoss();
            controller.get().getSupportFragmentManager().executePendingTransactions();
            controller.start().resume();
        }

        @Override
        public ActivityController getController() {
            return controller;
        }

        @Override
        public RequestManager doGet() {
            return retriever.get(controller.get());
        }

        @Override
        public boolean hasFragmentWithTag(String tag) {
            return controller.get().getSupportFragmentManager().findFragmentByTag(RequestManagerRetriever.TAG)
                    != null;
        }

        @Override
        public void addFragmentWithTag(String tag, RequestManager manager) {
            SupportRequestManagerFragment fragment = new SupportRequestManagerFragment();
            fragment.setRequestManager(manager);
            controller.get().getSupportFragmentManager()
                    .beginTransaction()
                    .add(fragment, RequestManagerRetriever.TAG)
                    .commitAllowingStateLoss();
            controller.get().getSupportFragmentManager().executePendingTransactions();
        }
    }
}
