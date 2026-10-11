package io.cueapp.android.helpers;

import android.graphics.Rect;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.AppBarLayout;

public class ViewHelper {
    private ViewHelper() {

    }

    public static void setupAppBarInsets(AppBarLayout appBar) {
        ViewCompat.setOnApplyWindowInsetsListener(appBar, (targetView, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            targetView.setPadding(
                    insets.left,
                    insets.top,
                    insets.right,
                    0
            );
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /**
     * Keeps the end of a scrolling list clear of the navigation bar: adds the bottom inset to
     * the padding the view has in its layout. Edge to edge (targetSdk 35+), a list under an app
     * bar reaches the bottom of the screen; with clipToPadding off it still scrolls under the bar.
     */
    public static void setupBottomInsets(View view) {
        final int paddingBottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (targetView, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            targetView.setPadding(
                    targetView.getPaddingLeft(),
                    targetView.getPaddingTop(),
                    targetView.getPaddingRight(),
                    paddingBottom + insets.bottom
            );
            return windowInsets;
        });
    }

    /**
     * Gives a small view a touch target of at least minSizeDp square without changing how it
     * looks: the ancestor hands it the touches around it (a TouchDelegate, so the ancestor can
     * have no other one). Follows the view's position and visibility as the ancestor lays out.
     */
    public static void expandTouchTarget(View view, ViewGroup ancestor, int minSizeDp) {
        int minSize = Math.round(minSizeDp * view.getResources().getDisplayMetrics().density);
        ancestor.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (view.getVisibility() != View.VISIBLE || view.getWidth() == 0 || view.getHeight() == 0) {
                ancestor.setTouchDelegate(null);
                return;
            }

            Rect rect = new Rect();
            view.getDrawingRect(rect);
            ancestor.offsetDescendantRectToMyCoords(view, rect);
            int growX = Math.max(0, minSize - rect.width());
            int growY = Math.max(0, minSize - rect.height());
            rect.left -= growX / 2;
            rect.right += growX - growX / 2;
            rect.top -= growY / 2;
            rect.bottom += growY - growY / 2;
            ancestor.setTouchDelegate(new TouchDelegate(rect, view));
        });
    }
}
