package io.cueapp.android.ui.views;

import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import io.cueapp.android.AccountNamePosition;
import io.cueapp.android.Preferences;
import io.cueapp.android.R;
import io.cueapp.android.ViewMode;
import io.cueapp.android.helpers.AnimationsHelper;
import io.cueapp.android.helpers.CenterVerticalSpan;
import io.cueapp.android.helpers.SimpleAnimationEndListener;
import io.cueapp.android.helpers.UiRefresher;
import io.cueapp.android.helpers.ViewHelper;
import io.cueapp.android.otp.HotpInfo;
import io.cueapp.android.otp.OtpInfo;
import io.cueapp.android.otp.OtpInfoException;
import io.cueapp.android.otp.SteamInfo;
import io.cueapp.android.otp.TotpInfo;
import io.cueapp.android.otp.YandexInfo;
import io.cueapp.android.ui.glide.GlideHelper;
import io.cueapp.android.vault.VaultEntry;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

public class EntryHolder extends RecyclerView.ViewHolder {
    private static final float DEFAULT_ALPHA = 1.0f;
    private static final float DIMMED_ALPHA = 0.2f;
    private static final char HIDDEN_CHAR = '●';

    /**
     * Milliseconds left in the period when a code and its ring turn colorError: motion.expiring
     * in design/tokens.json, which design/generate.py --check compares with this line. The card
     * ring (TotpProgressBar) and the keyboard's ring (CountdownRingView) use it too.
     */
    public static final int totalStateDuration = 7000;

    private View _favoriteIndicator;
    private TextView _profileName;
    private TextView _profileCode;
    private TextView _nextProfileCode;
    private TextView _profileIssuer;
    private TextView _profileCopied;
    private ImageView _profileDrawable;
    private VaultEntry _entry;
    private ImageView _buttonRefresh;
    private RelativeLayout _description;
    private ImageView _dragHandle;
    private ViewMode _viewMode;

    private final ImageView _selected;

    private Preferences.CodeGrouping _codeGrouping = Preferences.CodeGrouping.NO_GROUPING;
    private AccountNamePosition _accountNamePosition = AccountNamePosition.HIDDEN;

    private boolean _hidden;
    private boolean _paused;
    private TotpProgressBar _progressBar;
    private MaterialCardView _view;

    private UiRefresher _refresher;
    private Handler _copyAnimationHandler;
    private Handler _expirationHandler;
    private Handler _nextCodeHandler;
    private AnimatorSet _expirationAnimSet;
    private boolean _showNextCode;
    private boolean _showExpirationState;
    // the entry and the period (TOTP) or counter (HOTP) of the code on screen, to tell a code
    // that rolled over from one that is only shown again
    private VaultEntry _codeEntry;
    private long _codeSlot = -1;

    private Animation _scaleIn;
    private Animation _scaleOut;

    public EntryHolder(final View view) {
        super(view);

        _view = (MaterialCardView) view;
        _profileName = view.findViewById(R.id.profile_account_name);
        _profileCode = view.findViewById(R.id.profile_code);
        _nextProfileCode = view.findViewById(R.id.next_profile_code);
        _profileIssuer = view.findViewById(R.id.profile_issuer);
        _profileCopied = view.findViewById(R.id.profile_copied);
        _description = view.findViewById(R.id.description);
        _profileDrawable = view.findViewById(R.id.ivTextDrawable);
        _buttonRefresh = view.findViewById(R.id.buttonRefresh);
        _selected = view.findViewById(R.id.ivSelected);
        _dragHandle = view.findViewById(R.id.drag_handle);
        _favoriteIndicator = view.findViewById(R.id.favorite_indicator);

        _copyAnimationHandler = new Handler();
        _expirationHandler = new Handler();
        _nextCodeHandler = new Handler();

        _progressBar = view.findViewById(R.id.progressBar);
        // the refresh button of HOTP entries is 32-40dp; the card hands it a 48dp touch target
        ViewHelper.expandTouchTarget(_buttonRefresh, _view, 48);

        _scaleIn = AnimationsHelper.loadScaledAnimation(view.getContext(), R.anim.item_scale_in);
        _scaleOut = AnimationsHelper.loadScaledAnimation(view.getContext(), R.anim.item_scale_out);

        _refresher = new UiRefresher(new UiRefresher.Listener() {
            @Override
            public void onRefresh() {
                refreshCode();
            }

            @Override
            public void onExpiring() { }

            @Override
            public long getMillisTillNextRefresh() {
                return ((TotpInfo) _entry.getInfo()).getMillisTillNextRotation();
            }

            @Override
            public long getPeriodMillis() {
                return ((TotpInfo) _entry.getInfo()).getPeriod() * 1000L;
            }
        });
    }

    public void setData(VaultEntry entry, Preferences.CodeGrouping groupSize, ViewMode viewMode, AccountNamePosition accountNamePosition, boolean showIcon, boolean nonUniform, boolean hidden, boolean paused, boolean dimmed, boolean showExpirationState, boolean showExpirationCountdown, boolean showNextCode) {
        _entry = entry;
        _hidden = hidden;
        _paused = paused;
        _codeGrouping = groupSize;
        _viewMode = viewMode;

        _accountNamePosition = accountNamePosition;
        if (viewMode.equals(ViewMode.TILES) && _accountNamePosition == AccountNamePosition.END) {
            _accountNamePosition = AccountNamePosition.BELOW;
        }

        _selected.clearAnimation();
        _selected.setVisibility(View.GONE);
        // whatever the card was animating for the entry it showed before ends here: its blink
        // (kept when the new one is paused or hidden), a fading code, its "copied" label
        stopExpirationAnimation();
        clearCopyAnimation();
        _showNextCode = entry.getInfo() instanceof TotpInfo && showNextCode;
        _showExpirationState = _entry.getInfo() instanceof TotpInfo && showExpirationState;
        _progressBar.setShowSeconds(_showExpirationState && showExpirationCountdown);

        _favoriteIndicator.setVisibility(_entry.isFavorite() ? View.VISIBLE : View.INVISIBLE);

        // only show the progress bar if there is no uniform period and the entry type is TotpInfo
        setShowProgress(nonUniform);

        // only show the button if this entry is of type HotpInfo
        _buttonRefresh.setVisibility(entry.getInfo() instanceof HotpInfo ? View.VISIBLE : View.GONE);
        _nextProfileCode.setVisibility(_showNextCode ? View.INVISIBLE : View.GONE);

        String profileIssuer = entry.getIssuer();
        String profileName = entry.getName();
        // an entry with only a name shows it as the title, as on iOS, and a missing account
        // name leaves no empty line
        if (profileIssuer.isEmpty()) {
            profileIssuer = profileName;
            profileName = "";
        }
        if (profileName.isEmpty()) {
            _accountNamePosition = AccountNamePosition.HIDDEN;
        }
        if (!profileIssuer.isEmpty() && !profileName.isEmpty() && _accountNamePosition == AccountNamePosition.END) {
            profileName = _viewMode.getFormattedAccountName(profileName);
        }
        _profileIssuer.setText(profileIssuer);
        _profileName.setText(profileName);
        setAccountNameLayout(_accountNamePosition, !profileIssuer.isEmpty() && !profileName.isEmpty());

        if (_hidden) {
            hideCode();
        } else if (!_paused) {
            refreshCode();
        }

        showIcon(showIcon);
        itemView.setAlpha(dimmed ? DIMMED_ALPHA : DEFAULT_ALPHA);
    }

    private void setAccountNameLayout(AccountNamePosition accountNamePosition, Boolean hasBothIssuerAndName) {
        RelativeLayout.LayoutParams profileNameLayoutParams;

        switch (accountNamePosition) {
            case HIDDEN:
                _profileName.setVisibility(View.GONE);
                if (_viewMode == ViewMode.TILES) {
                    setTileTitleOnly(true);
                }
                break;

            case BELOW:
                if (_viewMode == ViewMode.TILES) {
                    setTileTitleOnly(false);
                }
                profileNameLayoutParams = (RelativeLayout.LayoutParams) _profileName.getLayoutParams();
                profileNameLayoutParams.removeRule(RelativeLayout.END_OF);
                profileNameLayoutParams.addRule(RelativeLayout.BELOW, R.id.profile_issuer);
                profileNameLayoutParams.setMarginStart(0);
                _profileName.setLayoutParams(profileNameLayoutParams);
                _profileName.setVisibility(View.VISIBLE);
                break;

            case END:
            default:
                profileNameLayoutParams = (RelativeLayout.LayoutParams) _profileName.getLayoutParams();
                profileNameLayoutParams.addRule(RelativeLayout.END_OF, R.id.profile_issuer);
                profileNameLayoutParams.removeRule(RelativeLayout.BELOW);
                if (hasBothIssuerAndName) {
                    profileNameLayoutParams.setMarginStart(24);
                }
                _profileName.setLayoutParams(profileNameLayoutParams);
                _profileName.setVisibility(View.VISIBLE);
                break;
        }
    }

    /**
     * Tiles: a title alone is larger and centred beside the icon; with an account name below
     * it, both are small (card_entry_tile.xml). Set both ways, since holders are recycled
     * between entries with and without an account name.
     */
    private void setTileTitleOnly(boolean titleOnly) {
        RelativeLayout.LayoutParams issuerParams = (RelativeLayout.LayoutParams) _profileIssuer.getLayoutParams();
        RelativeLayout.LayoutParams copiedParams = (RelativeLayout.LayoutParams) _profileCopied.getLayoutParams();
        if (titleOnly) {
            // the block wraps its text (at least the icon's height), so centre with a rule
            issuerParams.addRule(RelativeLayout.CENTER_VERTICAL);
            copiedParams.removeRule(RelativeLayout.BELOW);
            copiedParams.addRule(RelativeLayout.CENTER_VERTICAL);
        } else {
            issuerParams.removeRule(RelativeLayout.CENTER_VERTICAL);
            copiedParams.removeRule(RelativeLayout.CENTER_VERTICAL);
            copiedParams.addRule(RelativeLayout.BELOW, R.id.profile_issuer);
        }
        _profileIssuer.setLayoutParams(issuerParams);
        _profileCopied.setLayoutParams(copiedParams);
        _profileIssuer.setGravity(titleOnly ? Gravity.CENTER_VERTICAL : Gravity.NO_GRAVITY);
        _profileCopied.setGravity(titleOnly ? Gravity.CENTER_VERTICAL : Gravity.NO_GRAVITY);
        _profileIssuer.setTextSize(titleOnly ? 14 : 11);
        _profileCopied.setTextSize(titleOnly ? 14 : 9);
    }

    public VaultEntry getEntry() {
        return _entry;
    }

    public void loadIcon(Fragment fragment) {
        GlideHelper.loadEntryIcon(Glide.with(fragment), _entry, _profileDrawable);
    }

    public ImageView getIconView() {
        return _profileDrawable;
    }

    public void setOnRefreshClickListener(View.OnClickListener listener) {
        _buttonRefresh.setOnClickListener(listener);
    }

    public void setShowDragHandle(boolean showDragHandle) {
        if (showDragHandle) {
            _dragHandle.setVisibility(View.VISIBLE);
        } else {
            _dragHandle.setVisibility(View.INVISIBLE);
        }
    }

    public void setShowProgress(boolean showProgress) {
        setShowProgress(showProgress, true);
    }

    /**
     * Shows or hides this entry's progress indicator. When ownRefreshLoop is false, the
     * indicator animates but the code is refreshed by the list's shared refresher instead
     * of a per-entry one (used when all entries share the same period).
     */
    public void setShowProgress(boolean showProgress, boolean ownRefreshLoop) {
        if (_entry.getInfo() instanceof HotpInfo) {
            showProgress = false;
        }

        _progressBar.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        if (showProgress) {
            _progressBar.setPeriod(((TotpInfo) _entry.getInfo()).getPeriod());
            if (ownRefreshLoop) {
                startRefreshLoop();
            } else {
                _refresher.stop();
                _progressBar.start();
            }
        } else {
            stopRefreshLoop();
        }
    }

    public void setFocused(boolean focused) {
        if (focused) {
            _selected.setVisibility(View.VISIBLE);
        }
        _view.setChecked(focused);
    }

    public void setFocusedAndAnimate(boolean focused) {
        setFocused(focused);

        if (focused) {
            _selected.startAnimation(_scaleIn);
        } else {
            _selected.startAnimation(_scaleOut);
            _scaleOut.setAnimationListener(new SimpleAnimationEndListener(animation -> {
                _selected.setVisibility(View.GONE);
            }));
        }
    }

    /**
     * Cue: the card scrolled out of the list and waits to show another entry. Nothing it animates
     * carries on while it waits: an expiring code's blink ran on until the card was bound again,
     * and a "copied" label (its animations fill after) stayed over the next entry's title.
     */
    public void recycle() {
        _nextCodeHandler.removeCallbacksAndMessages(null);
        stopRefreshLoop();
        stopExpirationAnimation();
        clearCopyAnimation();
        _codeEntry = null;
        _codeSlot = -1;
    }

    private void clearCopyAnimation() {
        _copyAnimationHandler.removeCallbacksAndMessages(null);
        _profileCopied.clearAnimation();
        _profileIssuer.clearAnimation();
        _profileName.clearAnimation();
        _description.clearAnimation();
    }

    public void destroy() {
        _refresher.destroy();
        _progressBar.stop();
        _copyAnimationHandler.removeCallbacksAndMessages(null);
        _expirationHandler.removeCallbacksAndMessages(null);
        _nextCodeHandler.removeCallbacksAndMessages(null);
        if (_expirationAnimSet != null) {
            _expirationAnimSet.cancel();
            _expirationAnimSet = null;
        }
    }

    public void startRefreshLoop() {
        _refresher.start();
        _progressBar.start();
    }

    public void stopRefreshLoop() {
        _refresher.stop();
        _progressBar.stop();
    }

    public void refresh() {
        _progressBar.restart();
        refreshCode();
    }

    public void refreshCode() {
        if (!_hidden && !_paused) {
            boolean rotated = updateCodes();
            startExpirationAnimation();
            if (rotated) {
                fadeInNewCode(); // after the expiration reset, which sets the alpha back to 1
            }
        }
    }

    /**
     * Shows the current code. Returns whether it replaced the previous code of the same entry
     * because the period ended or the HOTP counter moved on, not because the code was revealed
     * or the row bound again.
     */
    private boolean updateCodes() {
        _profileCode.setText(getOtp());

        if (_showNextCode) {
            _nextProfileCode.setText(getOtp(1));
            // hideCode() gave it the hidden colour
            _nextProfileCode.setTextColor(MaterialColors.getColor(_nextProfileCode, R.attr.colorOnSurfaceDim));
        }

        updateNextCodeVisibility();

        long slot = getCodeSlot();
        boolean rotated = _entry == _codeEntry && _codeSlot != -1 && slot != _codeSlot;
        _codeEntry = _entry;
        _codeSlot = slot;
        return rotated;
    }

    private long getCodeSlot() {
        OtpInfo info = _entry.getInfo();
        if (info instanceof TotpInfo) {
            return System.currentTimeMillis() / (((TotpInfo) info).getPeriod() * 1000L);
        } else if (info instanceof HotpInfo) {
            return ((HotpInfo) info).getCounter();
        }
        return -1;
    }

    /**
     * Cue: a new code fades in rather than replacing the old one in a single frame. Skipped when
     * the row is off screen or animations are turned off.
     */
    private void fadeInNewCode() {
        float scale = AnimationsHelper.Scale.ANIMATOR.getValue(itemView.getContext());
        if (scale == 0f || !itemView.isAttachedToWindow() || _hidden) {
            return;
        }

        _profileCode.setAlpha(0.2f);
        _profileCode.animate().alpha(1f).setDuration((long) (300 / scale)).start();
        if (_showNextCode) {
            _nextProfileCode.setAlpha(0.2f);
            _nextProfileCode.animate().alpha(1f).setDuration((long) (300 / scale)).start();
        }
    }

    private String getOtp() {
        return getOtp(0);
    }

    private String getOtp(int offset) {
        OtpInfo info = _entry.getInfo();

        // In previous versions, it was possible to import entries with an empty
        // secret. Attempting to generate OTP's for such entries would result in a crash.
        // In case we encounter an old entry that has this issue, we display "ERROR" as
        // the OTP, instead of crashing.
        String otp;
        try {
            if (info instanceof TotpInfo) {
                otp = ((TotpInfo)info).getOtp((System.currentTimeMillis() / 1000) + ((long) (offset) * ((TotpInfo) _entry.getInfo()).getPeriod()));
            } else {
                otp = info.getOtp();
            }

            if (!(info instanceof SteamInfo || info instanceof YandexInfo)) {
                otp = formatCode(otp);
            }
        } catch (OtpInfoException e) {
            otp = _view.getResources().getString(R.string.error_all_caps);
        }

        return otp;
    }

    private String formatCode(String code) {
        int groupSize;
        StringBuilder sb = new StringBuilder();

        switch (_codeGrouping) {
            case NO_GROUPING:
                groupSize = code.length();
                break;
            case HALVES:
                groupSize = (code.length() / 2) + (code.length() % 2);
                break;
            default:
                groupSize = _codeGrouping.getValue();
                if (groupSize <= 0) {
                    throw new IllegalArgumentException("Code group size cannot be zero or negative");
                }
        }

        for (int i = 0; i < code.length(); i++) {
            if (i != 0 && i % groupSize == 0) {
                sb.append(" ");
            }
            sb.append(code.charAt(i));
        }
        code = sb.toString();

        return code;
    }

    public void revealCode() {
        updateCodes();
        startExpirationAnimation();
        _hidden = false;
        updateNextCodeVisibility();
    }

    public void hideCode() {
        String code = getOtp();
        String hiddenText = code.replaceAll("\\S", Character.toString(HIDDEN_CHAR));
        stopExpirationAnimation();

        updateTextViewWithDots(_profileCode,  hiddenText, code);
        updateTextViewWithDots(_nextProfileCode,  hiddenText, code);

        _hidden = true;
        updateNextCodeVisibility();
    }

    /**
     * Cue: the next code shows only for the last seconds of the period (totalStateDuration), when
     * the current one may run out before it is typed: the next one can be typed ahead and sent once
     * it turns. The rest of the time its line is kept but left blank, so the card does not change
     * height.
     */
    private void updateNextCodeVisibility() {
        _nextCodeHandler.removeCallbacksAndMessages(null);
        if (!_showNextCode) {
            return;
        }

        long left = ((TotpInfo) _entry.getInfo()).getMillisTillNextRotation();
        boolean show = !_hidden && left <= totalStateDuration;
        if (show && _nextProfileCode.getVisibility() != View.VISIBLE) {
            _nextProfileCode.setVisibility(View.VISIBLE);
            float scale = AnimationsHelper.Scale.ANIMATOR.getValue(itemView.getContext());
            if (scale != 0f && itemView.isAttachedToWindow()) {
                _nextProfileCode.setAlpha(0f);
                _nextProfileCode.animate().alpha(1f).setDuration((long) (300 / scale)).start();
            }
        } else if (!show) {
            _nextProfileCode.setVisibility(View.INVISIBLE);
        }

        if (!_hidden && left > totalStateDuration) {
            // a paused card picks it up again in setPaused(false), through updateCodes()
            _nextCodeHandler.postDelayed(() -> {
                if (!_paused) {
                    updateNextCodeVisibility();
                }
            }, left - totalStateDuration);
        }
    }

    private void updateTextViewWithDots(TextView textView, String hiddenCode, String code) {
        // measure with the view's own typeface: the code font has no dot glyph, and the
        // fallback font's dot (a full-width one under a CJK locale) is much wider than a digit
        Paint paint = new Paint(textView.getPaint());

        // Calculate the difference between the actual code width and the dots width
        float codeWidth = paint.measureText(code);
        float dotsWidth = paint.measureText(hiddenCode);
        float scaleFactor = codeWidth / dotsWidth;
        scaleFactor = (float)(Math.round(scaleFactor * 10.0) / 10.0);
        textView.setTextColor(MaterialColors.getColor(textView, R.attr.colorCodeHidden));

        // If the dots are no wider than the code, proceed with the normal text rendering (Cue:
        // was 0.8, which left Steam's letter codes with dots a quarter wider than the code,
        // and larger or smaller depending on the letters)
        if (scaleFactor >= 1.0) {
            textView.setText(hiddenCode);
            return;
        }

        // We need to use an invisible character in order to get the height of the profileCode textview consistent
        // Tokens without a space (ie Steam TOTP) will get misaligned without this
        SpannableString dotsString = new SpannableString("\u200B" + hiddenCode);

        // Only scale the digits/characters, skip the spaces
        int start = 1;
        for (int i = 0; i <= dotsString.length(); i++) {
            if (i == dotsString.length() || dotsString.charAt(i) == ' ') {
                if (i > start) {
                    dotsString.setSpan(new RelativeSizeSpan(scaleFactor), start, i, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                start = i + 1;
            }
        }

        Rect dotsRectBounds = new Rect();
        paint.getTextBounds(hiddenCode, 1, hiddenCode.length(), dotsRectBounds);

        // Use custom CenterVerticalSpan to make sure the dots are vertically aligned
        dotsString.setSpan(new CenterVerticalSpan(dotsRectBounds), 1, dotsString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        textView.setText(dotsString);
    }

    public void startExpirationAnimation() {
        stopExpirationAnimation();
        if (!_showExpirationState) {
            return;
        }

        TotpInfo info = (TotpInfo) _entry.getInfo();
        if (info.getPeriod() * 1000 <= totalStateDuration) {
            setExpiringColor(MaterialColors.getColor(_profileCode, androidx.appcompat.R.attr.colorError));
            return;
        }

        // Workaround for when animations are disabled or Android version being too old
        float durationScale = AnimationsHelper.Scale.ANIMATOR.getValue(itemView.getContext());
        if (durationScale == 0.0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            int color = MaterialColors.getColor(_profileCode, androidx.appcompat.R.attr.colorError);
            if (info.getMillisTillNextRotation() < totalStateDuration) {
                setExpiringColor(color);
            } else {
                _expirationHandler.postDelayed(() -> {
                    setExpiringColor(color);
                }, info.getMillisTillNextRotation() - totalStateDuration);
            }

            return;
        }

        final int colorShiftDuration = 300;
        long delayAnimDuration = info.getPeriod() * 1000L - totalStateDuration - colorShiftDuration;
        ValueAnimator delayAnim = ValueAnimator.ofFloat(0f, 0f);
        delayAnim.setDuration((long) (delayAnimDuration / durationScale));

        int colorFrom = _profileCode.getCurrentTextColor();
        int colorTo = MaterialColors.getColor(_profileCode, androidx.appcompat.R.attr.colorError);
        ValueAnimator colorAnim = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        colorAnim.setDuration((long) (colorShiftDuration / durationScale));
        colorAnim.addUpdateListener(a -> setExpiringColor((int) a.getAnimatedValue()));

        final int blinkDuration = 3000;
        ValueAnimator delayAnim2 = ValueAnimator.ofFloat(0f, 0f);
        delayAnim2.setDuration((long) ((totalStateDuration - blinkDuration) / durationScale));

        ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(_profileCode, "alpha", 1f, .5f);
        alphaAnim.setDuration((long) (500 / durationScale));
        alphaAnim.setRepeatCount(blinkDuration / 500 - 1);
        alphaAnim.setRepeatMode(ValueAnimator.REVERSE);

        _expirationAnimSet = new AnimatorSet();
        _expirationAnimSet.playSequentially(delayAnim, colorAnim, delayAnim2, alphaAnim);
        _expirationAnimSet.start();
        long currentPlayTime = (info.getPeriod() * 1000L) - info.getMillisTillNextRotation();
        _expirationAnimSet.setCurrentPlayTime((long) (currentPlayTime / durationScale));
    }

    private void stopExpirationAnimation() {
        _expirationHandler.removeCallbacksAndMessages(null);
        if (_expirationAnimSet != null) {
            _expirationAnimSet.cancel();
            _expirationAnimSet = null;
        }
        // a new code's fade drives the same alpha as the blink
        _profileCode.animate().cancel();
        _nextProfileCode.animate().cancel();

        int colorTo = MaterialColors.getColor(_profileCode, R.attr.colorCode);
        _profileCode.setTextColor(colorTo);
        _profileCode.setAlpha(1f);
        _nextProfileCode.setAlpha(1f);
        _progressBar.setProgressTintList(null);
    }

    /**
     * Colors the code and the countdown ring alike while the code is about to expire.
     */
    private void setExpiringColor(int color) {
        _profileCode.setTextColor(color);
        _progressBar.setProgressTintList(ColorStateList.valueOf(color));
    }

    public void showIcon(boolean show) {
        if (show) {
            _profileDrawable.setVisibility(View.VISIBLE);
        } else {
            _profileDrawable.setVisibility(View.GONE);
        }
    }

    public boolean isHidden() {
        return _hidden;
    }

    public void setPaused(boolean paused) {
        _paused = paused;

        if (_paused) {
            stopExpirationAnimation();
        } else if (!_hidden) {
            updateCodes();
            startExpirationAnimation();
        }
    }

    public void dim() {
        animateAlphaTo(DIMMED_ALPHA);
    }

    public void highlight() {
        animateAlphaTo(DEFAULT_ALPHA);
    }

    public void animateCopyText() {
        _copyAnimationHandler.removeCallbacksAndMessages(null);

        // In the normal view mode the "Copied" label shares its slot with the description,
        // in the other modes it sits below it and slides up into place
        Animation slideDownFadeIn = AnimationsHelper.loadScaledAnimation(itemView.getContext(),
                _viewMode == ViewMode.NORMAL ? R.anim.slide_down_fade_in_inplace : R.anim.slide_down_fade_in);
        Animation slideDownFadeOut = AnimationsHelper.loadScaledAnimation(itemView.getContext(), R.anim.slide_down_fade_out);
        Animation fadeOut = AnimationsHelper.loadScaledAnimation(itemView.getContext(), R.anim.fade_out);
        Animation fadeIn = AnimationsHelper.loadScaledAnimation(itemView.getContext(), R.anim.fade_in);

        // Use slideDown animation when user is not using Tiles mode
        if (_viewMode != ViewMode.TILES) {
            _profileCopied.startAnimation(slideDownFadeIn);
            View fadeOutView = (_accountNamePosition == AccountNamePosition.BELOW) ? _profileName : _description;
            fadeOutView.startAnimation(slideDownFadeOut);

            _copyAnimationHandler.postDelayed(() -> {
                _profileCopied.startAnimation(fadeOut);
                fadeOutView.startAnimation(fadeIn);
            }, 3000);
        } else {
            View visibleProfileText = _accountNamePosition == AccountNamePosition.BELOW ? _profileName : _profileIssuer;

            _profileCopied.startAnimation(fadeIn);
            visibleProfileText.startAnimation(fadeOut);

            _copyAnimationHandler.postDelayed(() -> {
                _profileCopied.startAnimation(fadeOut);
                visibleProfileText.startAnimation(fadeIn);
            }, 3000);
        }
    }

    private void animateAlphaTo(float alpha) {
        itemView.animate().alpha(alpha).setDuration(200).start();
    }
}
