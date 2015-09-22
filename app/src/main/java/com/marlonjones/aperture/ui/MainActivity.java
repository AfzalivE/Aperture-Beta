package com.marlonjones.aperture.ui;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.SharedElementCallback;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.internal.view.menu.ListMenuItemView;
import android.support.v7.internal.view.menu.MenuPopupHelper;
import android.support.v7.widget.ActionMenuPresenter;
import android.support.v7.widget.ActionMenuView;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import com.marlonjones.aperture.R;
import com.marlonjones.aperture.api.AlbumEntry;
import com.marlonjones.aperture.cab.MediaCab;
import com.marlonjones.aperture.fragments.MediaFragment;
import com.marlonjones.aperture.fragments.NavDrawerFragment;
import com.marlonjones.aperture.fragments.dialog.FolderSelectorDialog;
import com.marlonjones.aperture.providers.IncludedFolderProvider;
import com.marlonjones.aperture.providers.SortMemoryProvider;
import com.marlonjones.aperture.ui.base.ThemedActivity;
import com.marlonjones.aperture.utils.Utils;
import com.marlonjones.aperture.views.BreadCrumbLayout;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.ThemeSingleton;
import com.afollestad.materialdialogs.internal.MDTintHelper;
import com.melnykov.fab.FloatingActionButton;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * @author Aidan Follestad (afollestad) edited by Marlon Jones (VirusThePanda) for Aperture
 */
public class MainActivity extends ThemedActivity
        implements FolderSelectorDialog.FolderCallback {

    public DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    public boolean mPickMode;
    private SelectAlbumMode mSelectAlbumMode = SelectAlbumMode.NONE;
    public MediaCab mMediaCab;
    public Toolbar mToolbar;
    public FloatingActionButton mFab;
    public BreadCrumbLayout mCrumbs;

    private CharSequence mTitle;
    public boolean drawerArrowOpen;
    private final static int SETTINGS_REQUEST = 9000;

    private static final String TAG = "MainActivity";
    private static final boolean DEBUG = true;
    public static final String EXTRA_CURRENT_ITEM_POSITION = "extra_current_item_position";
    public static final String EXTRA_OLD_ITEM_POSITION = "extra_old_item_position";
    public static final String ACTION_SELECT_ALBUM = "com.afollestad.impression.SELECT_FOLDER";

    public enum SelectAlbumMode {
        NONE,
        COPY,
        MOVE,
        CHOOSE
    }

    public RecyclerView mRecyclerView;
    public Bundle mTmpState;
    public boolean mIsReentering;

    public void animateDrawerArrow(boolean closed) {
        if (mDrawerToggle == null || drawerArrowOpen == !closed) return;
        ValueAnimator anim;
        drawerArrowOpen = !closed;
        if (closed) {
            anim = ValueAnimator.ofFloat(1f, 0f);
        } else {
            anim = ValueAnimator.ofFloat(0f, 1f);
        }
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float slideOffset = (Float) valueAnimator.getAnimatedValue();
                mDrawerToggle.onDrawerSlide(null, slideOffset);
            }
        });
        anim.setInterpolator(new DecelerateInterpolator());
        anim.setDuration(300);
        anim.start();
    }
    public void camFab(View view) {
        new MaterialDialog.Builder(this)
                .title(R.string.camtitle)
                .items(R.array.camitems)
                .itemsCallbackSingleChoice(-1, new MaterialDialog.ListCallbackSingleChoice() {
                    @Override
                    public boolean onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                        /**
                         * If you use alwaysCallSingleChoiceCallback(), which is discussed below,
                         * returning false here won't allow the newly selected radio button to actually be selected.
                         **/

                        return true;
                    }
                })
                .positiveText(R.string.choose)
                .show();
    }
    public void setStatus(String status) {
        TextView view = (TextView) findViewById(com.marlonjones.aperture.R.id.status);
        if (status == null) {
            view.setVisibility(View.GONE);
        } else {
            view.setVisibility(View.VISIBLE);
            view.setText(status);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setupSharedElementCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        final SharedElementCallback mCallback = new SharedElementCallback() {
            @Override
            public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
                LOG("onMapSharedElements(List<String>, Map<String, View>)", mIsReentering);
                boolean shouldAdd = true;
                int oldPosition = mTmpState != null ? mTmpState.getInt(EXTRA_OLD_ITEM_POSITION) : 0;
                int currentPosition = mTmpState != null ? mTmpState.getInt(EXTRA_CURRENT_ITEM_POSITION) : 0;
                mTmpState = null;
                if (mIsReentering) {
                    shouldAdd = currentPosition != oldPosition;
                }
                if (shouldAdd && mRecyclerView != null) {
                    View newSharedView = mRecyclerView.findViewWithTag(currentPosition);
                    if (newSharedView != null) {
                        newSharedView = newSharedView.findViewById(com.marlonjones.aperture.R.id.image);
                        final String transName = newSharedView.getTransitionName();
                        names.clear();
                        names.add(transName);
                        sharedElements.clear();
                        sharedElements.put(transName, newSharedView);
                    }
                }

                //Somehow this works (setting status bar color in both MediaFragment and here)
                //to avoid image glitching through on when ViewActivity is first created.
                getWindow().setStatusBarColor(primaryColorDark());

                View decor = getWindow().getDecorView();
                View navigationBar = decor.findViewById(android.R.id.navigationBarBackground);
                View statusBar = decor.findViewById(android.R.id.statusBarBackground);

                if (navigationBar != null && !sharedElements.containsKey(Window.NAVIGATION_BAR_BACKGROUND_TRANSITION_NAME)) {
                    if (!names.contains(Window.NAVIGATION_BAR_BACKGROUND_TRANSITION_NAME))
                        names.add(Window.NAVIGATION_BAR_BACKGROUND_TRANSITION_NAME);
                    sharedElements.put(Window.NAVIGATION_BAR_BACKGROUND_TRANSITION_NAME, navigationBar);
                }

                View toolbarFrame = findViewById(com.marlonjones.aperture.R.id.toolbar_frame);
                if (toolbarFrame != null && !sharedElements.containsKey(toolbarFrame.getTransitionName())) {
                    if (!names.contains(toolbarFrame.getTransitionName()))
                        names.add(toolbarFrame.getTransitionName());
                    sharedElements.put(toolbarFrame.getTransitionName(), toolbarFrame);
                }

                if (statusBar != null && !sharedElements.containsKey(Window.STATUS_BAR_BACKGROUND_TRANSITION_NAME)) {
                    if (!names.contains(Window.STATUS_BAR_BACKGROUND_TRANSITION_NAME))
                        names.add(Window.STATUS_BAR_BACKGROUND_TRANSITION_NAME);
                    sharedElements.put(Window.STATUS_BAR_BACKGROUND_TRANSITION_NAME, statusBar);
                }

                LOG("=== names: " + names.toString(), mIsReentering);
                LOG("=== sharedElements: " + Utils.setToString(sharedElements.keySet()), mIsReentering);
            }

            @Override
            public void onSharedElementStart(List<String> sharedElementNames, List<View> sharedElements,
                                             List<View> sharedElementSnapshots) {
                LOG("onSharedElementStart(List<String>, List<View>, List<View>)", mIsReentering);
                logSharedElementsInfo(sharedElementNames, sharedElements);
            }

            @Override
            public void onSharedElementEnd(List<String> sharedElementNames, List<View> sharedElements,
                                           List<View> sharedElementSnapshots) {
                LOG("onSharedElementEnd(List<String>, List<View>, List<View>)", mIsReentering);
                logSharedElementsInfo(sharedElementNames, sharedElements);

                if (mIsReentering) {
                    View statusBar = getWindow().getDecorView().findViewById(android.R.id.statusBarBackground);
                    if (statusBar != null) {
                        statusBar.post(new Runnable() {
                            @Override
                            public void run() {
                                getWindow().setStatusBarColor(ContextCompat.getColor(
                                        MainActivity.this, android.R.color.transparent));
                            }
                        });
                    }
                }
            }

            private void logSharedElementsInfo(List<String> names, List<View> sharedElements) {
                LOG("=== names: " + names.toString(), mIsReentering);
                for (View view : sharedElements) {
                    int[] loc = new int[2];
                    //noinspection ResourceType
                    view.getLocationInWindow(loc);
                    Log.i(TAG, "=== " + view.getTransitionName() + ": " + "(" + loc[0] + ", " + loc[1] + ")");
                }
            }
        };
        setExitSharedElementCallback(mCallback);
    }

    private void saveScrollPosition() {
        Fragment frag = getFragmentManager().findFragmentById(com.marlonjones.aperture.R.id.content_frame);
        if (frag != null) {
            ((MediaFragment) frag).saveScrollPosition();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == -1) {
            new MaterialDialog.Builder(this)
                    .title(com.marlonjones.aperture.R.string.permission_needed)
                    .content(com.marlonjones.aperture.R.string.permission_needed_desc)
                    .cancelable(false)
                    .positiveText(android.R.string.ok)
                    .dismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            finish();
                        }
                    }).show();
        } else {
            MediaFragment content = (MediaFragment) getFragmentManager().findFragmentById(com.marlonjones.aperture.R.id.content_frame);
            if (content != null) content.reload();
            NavDrawerFragment nav = (NavDrawerFragment) getFragmentManager().findFragmentByTag("NAV_DRAWER");
            if (nav != null) nav.reloadAccounts();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.marlonjones.aperture.R.layout.activity_main);
        setupSharedElementCallback();
        //mFab = (FloatingActionButton) findViewById(com.marlonjones.aperture.R.id.camFab);
        //findViewById(com.marlonjones.aperture.R.id.camFab).setBackgroundColor(accentColor());
        mToolbar = (Toolbar) findViewById(com.marlonjones.aperture.R.id.toolbar);
        mToolbar.setSubtitleTextAppearance(this, com.marlonjones.aperture.R.style.ToolbarSubtitleStyle);
        setSupportActionBar(mToolbar);
        findViewById(com.marlonjones.aperture.R.id.toolbar_frame).setBackgroundColor(primaryColor());

        processIntent(getIntent());

        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);

        if (!isSelectAlbumMode()) {
            mDrawerLayout = (DrawerLayout) findViewById(com.marlonjones.aperture.R.id.drawer_layout);
            mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                    com.marlonjones.aperture.R.string.navigation_drawer_open, com.marlonjones.aperture.R.string.navigation_drawer_close) {
                @Override
                public void onDrawerOpened(View drawerView) {
                }

                @Override
                public void onDrawerSlide(View drawerView, float slideOffset) {
                    if (drawerView == null) super.onDrawerSlide(mDrawerLayout, slideOffset);
                }

                @Override
                public void onDrawerClosed(View drawerView) {
                    Fragment nav = getFragmentManager().findFragmentByTag("NAV_DRAWER");
                    if (nav != null)
                        ((NavDrawerFragment) nav).notifyClosed();
                }
            };
            mDrawerLayout.setDrawerShadow(com.marlonjones.aperture.R.drawable.drawer_shadow, GravityCompat.START);
            mDrawerLayout.post(new Runnable() {
                @Override
                public void run() {
                    mDrawerToggle.syncState();
                }
            });
            mDrawerLayout.setDrawerListener(mDrawerToggle);
            mDrawerLayout.setStatusBarBackgroundColor(primaryColorDark());

            FrameLayout navDrawerFrame = (FrameLayout) findViewById(com.marlonjones.aperture.R.id.nav_drawer_frame);
            int navDrawerMargin = getResources().getDimensionPixelSize(com.marlonjones.aperture.R.dimen.nav_drawer_margin);
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            int navDrawerWidthLimit = getResources().getDimensionPixelSize(com.marlonjones.aperture.R.dimen.nav_drawer_width_limit);
            int navDrawerWidth = displayMetrics.widthPixels - navDrawerMargin;
            if (navDrawerWidth > navDrawerWidthLimit) {
                navDrawerWidth = navDrawerWidthLimit;
            }
            navDrawerFrame.setLayoutParams(new DrawerLayout.LayoutParams(navDrawerWidth, DrawerLayout.LayoutParams.MATCH_PARENT, Gravity.START));
            navDrawerFrame.setBackgroundColor(primaryColorDark());

            if (getIntent().getAction() != null &&
                    (getIntent().getAction().equals(Intent.ACTION_GET_CONTENT) ||
                            getIntent().getAction().equals(Intent.ACTION_PICK))) {
                mTitle = getTitle();
                getSupportActionBar().setTitle(com.marlonjones.aperture.R.string.pick_something);
                mPickMode = true;
            }
        } else {
            actionBar.setHomeAsUpIndicator(com.marlonjones.aperture.R.drawable.ic_action_discard);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // The drawer layout would handle this if album selection mode wasn't active
                getWindow().setStatusBarColor(primaryColorDark());
            }
        }

        mCrumbs = (BreadCrumbLayout) findViewById(com.marlonjones.aperture.R.id.breadCrumbs);
        mCrumbs.setFragmentManager(getFragmentManager());
        mCrumbs.setCallback(new BreadCrumbLayout.SelectionCallback() {
            @Override
            public void onCrumbSelection(BreadCrumbLayout.Crumb crumb, int count, int index) {
                if (index == -1) {
                    onBackPressed();
                } else {
                    saveScrollPosition();
                    int active = mCrumbs.getActiveIndex();
                    if (active > index) {
                        final int difference = Math.abs(active - index);
                        for (int i = 0; i < difference; i++) {
                            try {
                                getFragmentManager().popBackStack();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } else if (active < index) {
                        for (int i = active + 1; i != index + 1; i++)
                            addArtificalBackStack(mCrumbs.getCrumb(i).getPath(), true);
                    }
                    mCrumbs.setActive(crumb);
                }
            }

            @Override
            public void onArtificialSelection(BreadCrumbLayout.Crumb crumb, String path, boolean backStack) {
                addArtificalBackStack(path, backStack);
            }
        });

        if (savedInstanceState == null) {
            // Show initial page (overview)
            switchPage(AlbumEntry.ALBUM_OVERVIEW, true);
        } else if (!isSelectAlbumMode()) {
            if (mTitle != null) getSupportActionBar().setTitle(mTitle);
            mMediaCab = MediaCab.restoreState(savedInstanceState, this);
        }

        getFragmentManager().addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                MediaFragment content = (MediaFragment) getFragmentManager().findFragmentById(com.marlonjones.aperture.R.id.content_frame);
                if (content != null)
                    content.onBackStackResume();
                NavDrawerFragment nav = (NavDrawerFragment) getFragmentManager().findFragmentByTag("NAV_DRAWER");
                if (content != null && nav != null && content.getAlbumPath() != null) {
                    nav.notifyBackStack(content.getAlbumPath());
                }
            }
        });

        if (savedInstanceState != null && savedInstanceState.containsKey("breadcrumbs_state")) {
            mCrumbs.restoreFromStateWrapper((BreadCrumbLayout.SavedStateWrapper)
                    savedInstanceState.getSerializable("breadcrumbs_state"), this);
        }

        SortMemoryProvider.cleanup(this);
    }

    private void addArtificalBackStack(final String to, boolean backStack) {
        Fragment frag = MediaFragment.create(to);
        String tag = null;
        if (to != null &&
                (to.equals(Environment.getExternalStorageDirectory().getAbsolutePath()) ||
                        to.equals(AlbumEntry.ALBUM_OVERVIEW))) {
            tag = "[root]";
        }
        @SuppressLint("CommitTransaction")
        FragmentTransaction transaction = getFragmentManager().beginTransaction()
                .replace(com.marlonjones.aperture.R.id.content_frame, frag, tag);
        if (backStack)
            transaction.addToBackStack(null);
        try {
            transaction.commit();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        invalidateCrumbs();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveScrollPosition();
    }

    public void invalidateCrumbs() {
        final boolean explorerMode = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("explorer_mode", false);
        mCrumbs.setVisibility(explorerMode ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("breadcrumbs_state", mCrumbs.getStateWrapper());
        if (mMediaCab != null)
            mMediaCab.saveState(outState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        processIntent(intent);
    }

    private void processIntent(final Intent intent) {
        String action = intent.getAction();
        if (action != null && action.equals(ACTION_SELECT_ALBUM)) {
            switch (intent.getIntExtra("mode", -1)) {
                default:
                    setSelectAlbumMode(SelectAlbumMode.CHOOSE);
                    break;
                case com.marlonjones.aperture.R.id.copyTo:
                    setSelectAlbumMode(SelectAlbumMode.COPY);
                    break;
                case com.marlonjones.aperture.R.id.moveTo:
                    setSelectAlbumMode(SelectAlbumMode.MOVE);
                    break;
            }
        }
    }

    public boolean isSelectAlbumMode() {
        return mSelectAlbumMode != SelectAlbumMode.NONE;
    }

    private void setSelectAlbumMode(SelectAlbumMode mode) {
        mSelectAlbumMode = mode;
        switch (mSelectAlbumMode) {
            default:
                getSupportActionBar().setTitle(com.marlonjones.aperture.R.string.choose_album);
                break;
            case COPY:
                getSupportActionBar().setTitle(com.marlonjones.aperture.R.string.copy_to);
                break;
            case MOVE:
                getSupportActionBar().setTitle(com.marlonjones.aperture.R.string.move_to);
                break;
        }
        invalidateOptionsMenu();
    }

    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        if (!mPickMode && mSelectAlbumMode == SelectAlbumMode.NONE && getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onActivityReenter(int resultCode, Intent data) {
        super.onActivityReenter(resultCode, data);
        mIsReentering = true;
        mTmpState = new Bundle(data.getExtras());
        int oldPosition = mTmpState.getInt(EXTRA_OLD_ITEM_POSITION);
        int currentPosition = mTmpState.getInt(EXTRA_CURRENT_ITEM_POSITION);
        if (oldPosition != currentPosition && mRecyclerView != null) {
            mRecyclerView.scrollToPosition(currentPosition);
        }
        if (mRecyclerView != null) {
            postponeEnterTransition();
            mRecyclerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    mRecyclerView.getViewTreeObserver().removeOnPreDrawListener(this);
                    mRecyclerView.requestLayout();
                    startPostponedEnterTransition();
                    return true;
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (mMediaCab != null) {
            mMediaCab.finish();
            mMediaCab = null;
        } else {
            if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                mDrawerLayout.closeDrawer(GravityCompat.START);
            } else if (mCrumbs.canPop()) {
                mCrumbs.pop();
            } else {
                super.onBackPressed();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(com.marlonjones.aperture.R.menu.main, menu);
        menu.findItem(com.marlonjones.aperture.R.id.settings).setVisible(!mPickMode && mSelectAlbumMode == SelectAlbumMode.NONE);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == com.marlonjones.aperture.R.id.settings) {
            startActivityForResult(new Intent(this, SettingsActivity.class), SETTINGS_REQUEST);
            return true;
        } else if (item.getItemId() == android.R.id.home) {
            if (drawerArrowOpen) {
                onBackPressed();
                return true;
            } else if (isSelectAlbumMode()) {
                finish();
                return true;
            }
        }
        return mDrawerLayout != null && (mDrawerLayout.getDrawerLockMode(GravityCompat.START) == DrawerLayout.LOCK_MODE_LOCKED_CLOSED ||
                mDrawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SETTINGS_REQUEST && resultCode == Activity.RESULT_OK) {
            MediaFragment content = (MediaFragment) getFragmentManager().findFragmentById(com.marlonjones.aperture.R.id.content_frame);
            if (content != null) content.reload();
            reloadNavDrawerAlbums();
        }
    }

    private void switchPage(String path, boolean closeDrawer) {
        switchPage(path, closeDrawer, false);
    }

    public void switchPage(String to, boolean closeDrawer, boolean backStack) {
        boolean wasNull = false;
        if (to == null) {
            // Initial directory
            wasNull = true;
            to = Environment.getExternalStorageDirectory().getAbsolutePath();
        }
        BreadCrumbLayout.Crumb crumb = new BreadCrumbLayout.Crumb(this, to);
        if (!backStack) {
            mCrumbs.clearCrumbs();
            mCrumbs.addCrumb(crumb, true);
            addArtificalBackStack(to, false);
        } else {
            final boolean explorerMode = PreferenceManager.getDefaultSharedPreferences(this)
                    .getBoolean("explorer_mode", false);
            mCrumbs.setActiveOrAdd(crumb, !explorerMode, wasNull);
        }
        if (closeDrawer && mDrawerLayout != null)
            mDrawerLayout.closeDrawers();
    }

    public void notifyFoldersChanged() {
        FragmentManager fm = getFragmentManager();
        Fragment frag = fm.findFragmentByTag("[root]");
        if (frag != null)
            ((MediaFragment) frag).reload();
        for (int i = 0; i < fm.getBackStackEntryCount(); i++) {
            final String name = fm.getBackStackEntryAt(i).getName();
            if (name != null) {
                frag = fm.findFragmentByTag(name);
                if (frag != null) ((MediaFragment) frag).reload();
            }
        }
    }

    private static void LOG(String message, boolean isReentering) {
        if (DEBUG) {
            Log.i(TAG, String.format("%s: %s", isReentering ? "REENTERING" : "EXITING", message));
        }
    }

    @Override
    public void onFolderSelection(File folder) {
        IncludedFolderProvider.add(this, folder);
        reloadNavDrawerAlbums();
        notifyFoldersChanged();
    }

    public void reloadNavDrawerAlbums() {
        NavDrawerFragment nav = (NavDrawerFragment) getFragmentManager().findFragmentByTag("NAV_DRAWER");
        if (nav != null) {
            if (nav.mCurrentAccount == null)
                nav.reloadAccounts();
            else nav.getAlbums(nav.mCurrentAccount);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        invalidateMenuTint();
        return super.onPrepareOptionsMenu(menu);
    }

    private void invalidateMenuTint() {
        mToolbar.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Field f1 = Toolbar.class.getDeclaredField("mMenuView");
                    f1.setAccessible(true);
                    ActionMenuView actionMenuView = (ActionMenuView) f1.get(mToolbar);

                    Field f2 = ActionMenuView.class.getDeclaredField("mPresenter");
                    f2.setAccessible(true);
                    ActionMenuPresenter presenter = (ActionMenuPresenter) f2.get(actionMenuView);

                    Field f3 = presenter.getClass().getDeclaredField("mOverflowPopup");
                    f3.setAccessible(true);
                    MenuPopupHelper overflowMenuPopupHelper = (MenuPopupHelper) f3.get(presenter);
                    setTintForMenuPopupHelper(overflowMenuPopupHelper);

                    Field f4 = presenter.getClass().getDeclaredField("mActionButtonPopup");
                    f4.setAccessible(true);
                    MenuPopupHelper subMenuPopupHelper = (MenuPopupHelper) f4.get(presenter);
                    setTintForMenuPopupHelper(subMenuPopupHelper);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void setTintForMenuPopupHelper(MenuPopupHelper menuPopupHelper) {
        if (menuPopupHelper != null) {
            final ListView listView = menuPopupHelper.getPopup().getListView();
            listView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    try {
                        Field checkboxField = ListMenuItemView.class.getDeclaredField("mCheckBox");
                        checkboxField.setAccessible(true);
                        Field radioButtonField = ListMenuItemView.class.getDeclaredField("mRadioButton");
                        radioButtonField.setAccessible(true);

                        for (int i = 0; i < listView.getChildCount(); i++) {
                            View v = listView.getChildAt(i);
                            if (!(v instanceof ListMenuItemView)) continue;
                            ListMenuItemView iv = (ListMenuItemView) v;

                            CheckBox check = (CheckBox) checkboxField.get(iv);
                            if (check != null) {
                                MDTintHelper.setTint(check, ThemeSingleton.get().widgetColor);
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    check.setBackground(null);
                                }
                            }

                            RadioButton radioButton = (RadioButton) radioButtonField.get(iv);
                            if (radioButton != null) {
                                MDTintHelper.setTint(radioButton, ThemeSingleton.get().widgetColor);
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    radioButton.setBackground(null);
                                }
                            }
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        listView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    } else {
                        //noinspection deprecation
                        listView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    }
                }
            });
        }
    }
}