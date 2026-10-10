package io.cueapp.android.ui.slides;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import io.cueapp.android.R;
import io.cueapp.android.ui.intro.SlideFragment;

public class DoneSlide extends SlideFragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_done_slide, container, false);
    }
}
