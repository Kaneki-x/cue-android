package io.cueapp.android.ime;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

/**
 * The top row of a keyboard key: the issuer, then the account name, which spans the rest of
 * the row and is aligned to its end. The issuer comes first and the name ellipsizes in what
 * is left, except that a long issuer leaves the name up to a third of the row: two accounts of
 * one service are suggested together, and the name is what tells them apart. The iOS keyboard
 * (apple/CueKeyboard) lays out its keys the same way. Holds exactly these two children; the
 * gap between them is the name's start padding.
 */
public class KeyTitleLayout extends ViewGroup {
    public KeyTitleLayout(Context context) {
        this(context, null);
    }

    public KeyTitleLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        View issuer = getChildAt(0);
        View name = getChildAt(1);
        int width = Math.max(0, MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight());
        int heightSpec = getChildMeasureSpec(heightMeasureSpec, getPaddingTop() + getPaddingBottom(), LayoutParams.WRAP_CONTENT);
        boolean hasName = name.getVisibility() != GONE;

        int nameReserve = 0;
        if (hasName) {
            name.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), heightSpec);
            nameReserve = Math.min(name.getMeasuredWidth(), width / 3);
        }
        issuer.measure(MeasureSpec.makeMeasureSpec(width - nameReserve, MeasureSpec.AT_MOST), heightSpec);
        int height = issuer.getMeasuredHeight();
        if (hasName) {
            name.measure(MeasureSpec.makeMeasureSpec(width - issuer.getMeasuredWidth(), MeasureSpec.EXACTLY), heightSpec);
            height = Math.max(height, name.getMeasuredHeight());
        }

        setMeasuredDimension(
                resolveSize(width + getPaddingLeft() + getPaddingRight(), widthMeasureSpec),
                resolveSize(height + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int innerTop = getPaddingTop();
        int innerHeight = bottom - top - getPaddingTop() - getPaddingBottom();
        int x = rtl ? right - left - getPaddingRight() : getPaddingLeft();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            int childTop = innerTop + (innerHeight - childHeight) / 2;
            int childLeft = rtl ? x - childWidth : x;
            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
            x = rtl ? childLeft : childLeft + childWidth;
        }
    }
}
