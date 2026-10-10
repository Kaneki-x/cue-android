package io.cueapp.android;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class ThemeMap {
    private ThemeMap() {

    }

    public static final Map<Theme, Integer> DEFAULT = ImmutableMap.of(
            Theme.LIGHT, R.style.Theme_Cue_Light,
            Theme.DARK, R.style.Theme_Cue_Dark,
            Theme.AMOLED, R.style.Theme_Cue_Amoled
    );
}
