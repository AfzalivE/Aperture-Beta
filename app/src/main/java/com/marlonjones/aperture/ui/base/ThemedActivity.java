package com.marlonjones.aperture.ui.base;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.res.ColorStateList;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

import com.marlonjones.aperture.R;
import com.marlonjones.aperture.views.CircleView;
import com.afollestad.materialdialogs.internal.ThemeSingleton;

/**
 * @author Aidan Follestad (afollestad), edited for Aperture by Marlon Jones (VirusThePanda)
 */
public abstract class ThemedActivity extends AppCompatActivity {

    private boolean mLastDarkTheme;
    private int mLastPrimaryColor;
    private int mLastAccentColor;
    private boolean mLastColoredNav;

    protected int darkTheme() {
        return com.marlonjones.aperture.R.style.AppTheme_Dark;
    }

    protected int lightTheme() {
        return com.marlonjones.aperture.R.style.AppTheme;
    }

    public boolean isDarkTheme() {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean("dark_theme", false);
    }

    public int primaryColor() {
        String key = "primary_color";
        if (mLastDarkTheme) key += "_dark";
        else key += "_light";
        final int defaultColor = ContextCompat.getColor(this, mLastDarkTheme ?
                com.marlonjones.aperture.R.color.dark_theme_gray : R.color.default_main);
        return PreferenceManager.getDefaultSharedPreferences(this).getInt(key, defaultColor);
    }

    protected void primaryColor(int newColor) {
        String key = "primary_color";
        if (mLastDarkTheme) key += "_dark";
        else key += "_light";
        PreferenceManager.getDefaultSharedPreferences(this).edit().putInt(key, newColor).commit();
    }

    public int primaryColorDark() {
        return CircleView.shiftColorDown(primaryColor());
    }

    public int accentColor() {
        String key = "accent_color";
        if (mLastDarkTheme) key += "_dark";
        else key += "_light";
        final int defaultColor = ContextCompat.getColor(this, R.color.default_accent);
        return PreferenceManager.getDefaultSharedPreferences(this).getInt(key, defaultColor);
    }

    //protected void fabcolour(int newColor) {
      //  String key = "fab_color";
       // if (mLastDarkTheme) key += "_dark";
      //  else key += "_light";
      //  PreferenceManager.getDefaultSharedPreferences(this).edit().putInt(key, newColor).commit();
    //}
  //  public int fabcolour() {
       // String key = "fab_color";
        //if (mLastDarkTheme) key += "_dark";
       // else key += "_light";
        //final int defaultColor = ContextCompat.getColor(this, R.color.material_red_A400);
        //return PreferenceManager.getDefaultSharedPreferences(this).getInt(key, defaultColor);
//}
    protected void accentColor(int newColor) {
        String key = "accent_color";
        if (mLastDarkTheme) key += "_dark";
        else key += "_light";
        PreferenceManager.getDefaultSharedPreferences(this).edit().putInt(key, newColor).commit();
    }
    public boolean isColoredNavBar() {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean("colored_navbar", false);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLastDarkTheme = isDarkTheme();
        mLastPrimaryColor = primaryColor();
        mLastAccentColor = accentColor();
        mLastColoredNav = isColoredNavBar();
        ColorStateList sl = ColorStateList.valueOf(mLastAccentColor);
        ThemeSingleton.get().positiveColor = sl;
        ThemeSingleton.get().neutralColor = sl;
        ThemeSingleton.get().negativeColor = sl;
        ThemeSingleton.get().widgetColor = mLastAccentColor;
        setTheme(mLastDarkTheme ? darkTheme() : lightTheme());
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Sets color of entry in the system recents page
            ActivityManager.TaskDescription td = new ActivityManager.TaskDescription(
                    getString(com.marlonjones.aperture.R.string.app_name),
                    BitmapFactory.decodeResource(getResources(), com.marlonjones.aperture.R.mipmap.ic_launcher_beta),
                    primaryColor());
            setTaskDescription(td);
        }

        if (getSupportActionBar() != null)
            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(primaryColor()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && hasColoredBars()) {
            final int dark = primaryColorDark();
            if (allowStatusBarColoring())
                getWindow().setStatusBarColor(dark);
            else
                getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.transparent));
            if (mLastColoredNav)
                getWindow().setNavigationBarColor(dark);
        }
    }

    protected boolean allowStatusBarColoring() {
        return false;
    }

    protected boolean hasColoredBars() {
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean darkTheme = isDarkTheme();
        int primaryColor = primaryColor();
        int accentColor = accentColor();
        boolean coloredNav = isColoredNavBar();
        if (darkTheme != mLastDarkTheme || primaryColor != mLastPrimaryColor ||
                accentColor != mLastAccentColor || coloredNav != mLastColoredNav) {
            recreate();
        }
    }
}
