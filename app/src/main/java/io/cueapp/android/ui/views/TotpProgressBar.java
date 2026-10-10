package io.cueapp.android.ui.views;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;

import com.google.android.material.color.MaterialColors;

import io.cueapp.android.helpers.AnimationsHelper;
import io.cueapp.android.otp.TotpInfo;

public class TotpProgressBar extends ProgressBar {
    private int _period = TotpInfo.DEFAULT_PERIOD;
    private Handler _handler;
    private float _animDurationScale;
    private boolean _showSeconds;
    private Paint _secondsPaint;

    /** Seconds left in the period when the code and ring turn colorError (design/tokens.json motion.expiring). */
    private static final int EXPIRING_MILLIS = 7000;

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

    public void start() {
        stop();
        _handler = new Handler();
        _animDurationScale = AnimationsHelper.Scale.ANIMATOR.getValue(getContext());
        refresh();
    }

    public void stop() {
        if (_handler != null) {
            _handler.removeCallbacksAndMessages(null);
            _handler = null;
        }
    }

    public void restart() {
        stop();
        start();
    }

    private void refresh() {
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

        ObjectAnimator animation = ObjectAnimator.ofInt(this, "progress", currentProgress, animEnd);
        animation.setDuration(animDuration);
        animation.setInterpolator(new LinearInterpolator());
        animation.start();

        // the animation only lasts for (less than) one second, so restart it after that
        _handler.postDelayed(this::refresh, realAnimDuration);
    }
}
