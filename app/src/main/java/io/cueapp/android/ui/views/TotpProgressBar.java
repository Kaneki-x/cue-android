package io.cueapp.android.ui.views;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;

import io.cueapp.android.R;
import io.cueapp.android.helpers.AnimationsHelper;
import io.cueapp.android.otp.TotpInfo;

public class TotpProgressBar extends ProgressBar {
    private int _period = TotpInfo.DEFAULT_PERIOD;
    private Handler _handler;
    private ObjectAnimator _animation;
    private float _animDurationScale;
    private boolean _showSeconds;
    private Paint _secondsPaint;
    private long _secondsLeft = -1;

    // start() was called and stop() was not: the ring animates whenever it can be seen
    private boolean _started;
    // the refresh loop is running, which it only does while the ring is on screen
    private boolean _running;
    private final Runnable _refreshRunnable = this::refresh;

    /** Seconds left in the period when the code and ring turn colorError (design/tokens.json motion.expiring). */
    private static final int EXPIRING_MILLIS = EntryHolder.totalStateDuration;

    public TotpProgressBar(Context context) {
        super(context);
    }

    public TotpProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TotpProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public TotpProgressBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setPeriod(int period) {
        _period = period;
    }

    /**
     * Cue: the whole seconds left, inside the ring, once the code is about to expire.
     */
    public void setShowSeconds(boolean showSeconds) {
        _showSeconds = showSeconds;
        invalidate();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        long millisLeft = TotpInfo.getMillisTillNextRotation(_period);
        if (!_showSeconds || millisLeft > EXPIRING_MILLIS) {
            return;
        }

        if (_secondsPaint == null) {
            _secondsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            _secondsPaint.setTextAlign(Paint.Align.CENTER);
            _secondsPaint.setTypeface(Typeface.DEFAULT_BOLD);
        }
        _secondsPaint.setColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorError));
        _secondsPaint.setTextSize(getHeight() * 0.48f);

        // the ring is drawn rotated by -90 degrees (android:rotation), the digits stand upright
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        canvas.save();
        canvas.rotate(-getRotation(), cx, cy);
        Paint.FontMetrics metrics = _secondsPaint.getFontMetrics();
        String seconds = String.valueOf((millisLeft + 999) / 1000);
        canvas.drawText(seconds, cx, cy - (metrics.ascent + metrics.descent) / 2f, _secondsPaint);
        canvas.restore();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        // the content description says how many seconds are left; the ring's percentage adds nothing
        info.setRangeInfo(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            info.setStateDescription(null);
        }
    }

    public void start() {
        stop();
        _started = true;
        updateLoop();
    }

    public void stop() {
        _started = false;
        updateLoop();
    }

    public void restart() {
        stop();
        start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateLoop();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        updateLoop();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateLoop();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateLoop();
    }

    /**
     * Runs the refresh loop only while the ring can be seen: not while it or its card is gone,
     * its activity is in the background or its view is detached (a recycled card, a destroyed
     * activity), so nothing animates unseen and nothing holds on to the view.
     */
    private void updateLoop() {
        boolean shouldRun = _started && isAttachedToWindow() && isShown() && getWindowVisibility() == VISIBLE;
        if (shouldRun == _running) {
            return;
        }

        _running = shouldRun;
        if (shouldRun) {
            _animDurationScale = AnimationsHelper.Scale.ANIMATOR.getValue(getContext());
            refresh();
        } else {
            if (_handler != null) {
                _handler.removeCallbacks(_refreshRunnable);
            }
            if (_animation != null) {
                _animation.cancel();
                _animation = null;
            }
        }
    }

    private void refresh() {
        if (!_running) {
            return;
        }

        // calculate the current progress the bar should start at
        int maxProgress = getMax();
        long millisTillRotation = TotpInfo.getMillisTillNextRotation(_period);
        int currentProgress = (int) (maxProgress * ((float) millisTillRotation / (_period * 1000)));

        // start progress animation, compensating for any changes to the animator duration scale settings
        float animPart = (float) maxProgress / _period;
        int animEnd = (int) (Math.floor(currentProgress / animPart) * animPart);
        int animPartDuration = _animDurationScale > 0 ? (int) (1000 / _animDurationScale) : 0;
        float animDurationFraction = (float) (currentProgress - animEnd) / animPart;
        int realAnimDuration = (int) (1000 * animDurationFraction);
        int animDuration =  (int) (animPartDuration * animDurationFraction);

        if (_animation != null) {
            _animation.cancel();
        }
        _animation = ObjectAnimator.ofInt(this, "progress", currentProgress, animEnd);
        _animation.setDuration(animDuration);
        _animation.setInterpolator(new LinearInterpolator());
        _animation.start();

        // screen readers hear the seconds left as part of the card
        long secondsLeft = (millisTillRotation + 999) / 1000;
        if (secondsLeft != _secondsLeft) {
            _secondsLeft = secondsLeft;
            setContentDescription(getResources().getQuantityString(R.plurals.countdown_seconds_left, (int) secondsLeft, (int) secondsLeft));
        }

        // the animation only lasts for (less than) one second, so restart it after that
        if (_handler == null) {
            _handler = new Handler(Looper.getMainLooper());
        }
        _handler.removeCallbacks(_refreshRunnable);
        _handler.postDelayed(_refreshRunnable, realAnimDuration);
    }
}
