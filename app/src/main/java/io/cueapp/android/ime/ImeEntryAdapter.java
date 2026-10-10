package io.cueapp.android.ime;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import io.cueapp.android.Preferences;
import io.cueapp.android.R;
import io.cueapp.android.helpers.CodeFormatHelper;
import io.cueapp.android.otp.OtpInfo;
import io.cueapp.android.otp.OtpInfoException;
import io.cueapp.android.otp.TotpInfo;
import io.cueapp.android.ui.glide.GlideHelper;
import io.cueapp.android.vault.VaultEntry;
import com.bumptech.glide.RequestManager;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

public class ImeEntryAdapter extends RecyclerView.Adapter<ImeEntryAdapter.Holder> {
    /**
     * Filter key that only shows pinned entries (the vault's favorites).
     */
    public static final String FILTER_FAVORITES = "★";
    /**
     * Prefix of a filter key that shows one group: "group:" + its UUID.
     */
    public static final String FILTER_GROUP_PREFIX = "group:";

    private final RequestManager _glide;
    private final Listener _listener;

    private List<ImeEntryRanker.Ranked> _all = new ArrayList<>();
    private final List<ImeEntryRanker.Ranked> _visible = new ArrayList<>();

    private String _filter;
    private Preferences.CodeGrouping _codeGrouping = Preferences.CodeGrouping.GROUPING_THREES;
    private boolean _hideCodes;
    private boolean _showIcons = true;
    // the entry whose code was just inserted, shown as 已填入 for a moment
    @Nullable
    private java.util.UUID _inserted;
    private final android.os.Handler _handler = new android.os.Handler(android.os.Looper.getMainLooper());

    public ImeEntryAdapter(RequestManager glide, Listener listener) {
        _glide = glide;
        _listener = listener;
    }

    public void setCodeGrouping(Preferences.CodeGrouping grouping) {
        _codeGrouping = grouping;
    }

    public void setHideCodes(boolean hide) {
        _hideCodes = hide;
    }

    public void setShowIcons(boolean show) {
        _showIcons = show;
    }

    public void setEntries(List<ImeEntryRanker.Ranked> entries) {
        _all = entries;
        applyFilter();
    }

    /**
     * Sets the filter: null shows everything, FILTER_FAVORITES the pinned entries and
     * FILTER_GROUP_PREFIX + a group's UUID the entries in that group.
     */
    public void setFilter(@Nullable String filter) {
        _filter = filter;
        applyFilter();
    }

    @Nullable
    public String getFilter() {
        return _filter;
    }

    public boolean hasEntries() {
        return !_all.isEmpty();
    }

    public boolean hasFavorites() {
        for (ImeEntryRanker.Ranked ranked : _all) {
            if (ranked.getEntry().isFavorite()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The groups that have at least one entry, for building the filter chips.
     */
    public java.util.Set<java.util.UUID> getUsedGroups() {
        java.util.Set<java.util.UUID> groups = new java.util.HashSet<>();
        for (ImeEntryRanker.Ranked ranked : _all) {
            groups.addAll(ranked.getEntry().getGroups());
        }
        return groups;
    }

    /**
     * Shows 已填入 on the entry's key for a moment, after its code was inserted.
     */
    public void markInserted(VaultEntry entry) {
        _inserted = entry.getUUID();
        notifyDataSetChanged();
        _handler.removeCallbacksAndMessages(null);
        _handler.postDelayed(() -> {
            _inserted = null;
            notifyDataSetChanged();
        }, 1500);
    }

    private void applyFilter() {
        _visible.clear();
        for (ImeEntryRanker.Ranked ranked : _all) {
            VaultEntry entry = ranked.getEntry();
            if (_filter == null
                    || (_filter.equals(FILTER_FAVORITES) && entry.isFavorite())
                    || (_filter.startsWith(FILTER_GROUP_PREFIX)
                        && entry.getGroups().contains(java.util.UUID.fromString(_filter.substring(FILTER_GROUP_PREFIX.length()))))) {
                _visible.add(ranked);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.ime_entry_item, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(_visible.get(position));
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        holder.recycle();
    }

    @Override
    public int getItemCount() {
        return _visible.size();
    }

    class Holder extends RecyclerView.ViewHolder {
        private final View _favoriteIndicator;
        private final ImageView _icon;
        private final TextView _issuer;
        private final TextView _name;
        private final TextView _suggested;
        private final TextView _code;
        private final CountdownRingView _ring;
        private final ImageView _hotpIcon;

        private VaultEntry _entry;

        Holder(View view) {
            super(view);
            _favoriteIndicator = view.findViewById(R.id.favorite_indicator);
            // the row's rounded background clips the pinned bar
            view.setClipToOutline(true);
            _icon = view.findViewById(R.id.icon);
            _issuer = view.findViewById(R.id.issuer);
            _name = view.findViewById(R.id.name);
            _suggested = view.findViewById(R.id.suggested);
            _code = view.findViewById(R.id.code);
            _ring = view.findViewById(R.id.ring);
            _hotpIcon = view.findViewById(R.id.hotp_icon);

            _ring.setListener(this::refreshCode);
            view.setOnClickListener(v -> {
                if (_entry != null) {
                    _listener.onEntryClick(_entry);
                }
            });
        }

        void bind(ImeEntryRanker.Ranked ranked) {
            _entry = ranked.getEntry();

            String issuer = _entry.getIssuer();
            String name = _entry.getName();
            if (issuer.isEmpty()) {
                issuer = name;
                name = "";
            }
            _issuer.setText(issuer);
            _name.setText(name);
            // a suggested entry says so where its account name would be
            _suggested.setVisibility(ranked.isSuggested() ? View.VISIBLE : View.GONE);
            _name.setVisibility(name.isEmpty() || ranked.isSuggested() ? View.GONE : View.VISIBLE);
            _favoriteIndicator.setVisibility(_entry.isFavorite() ? View.VISIBLE : View.GONE);

            OtpInfo info = _entry.getInfo();
            if (info instanceof TotpInfo) {
                _ring.setPeriod(((TotpInfo) info).getPeriod());
                _ring.setVisibility(View.VISIBLE);
                _hotpIcon.setVisibility(View.GONE);
            } else {
                _ring.setVisibility(View.GONE);
                _hotpIcon.setVisibility(View.VISIBLE);
            }

            if (_showIcons) {
                _icon.setVisibility(View.VISIBLE);
                GlideHelper.loadEntryIcon(_glide, _entry, _icon);
            } else {
                _glide.clear(_icon);
                _icon.setVisibility(View.GONE);
            }

            refreshCode();
        }

        void refreshCode() {
            if (_entry == null) {
                return;
            }

            boolean inserted = _entry.getUUID().equals(_inserted);
            itemView.setBackgroundTintList(inserted
                    ? ColorStateList.valueOf(MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorSecondaryContainer))
                    : null);
            if (inserted) {
                _code.setText(itemView.getResources().getString(R.string.ime_inserted));
                _code.setTextColor(MaterialColors.getColor(_code, com.google.android.material.R.attr.colorOnSecondaryContainer));
                return;
            }

            String code;
            try {
                code = CodeFormatHelper.format(_entry.getInfo().getOtp(), _entry.getInfo(), _codeGrouping);
            } catch (OtpInfoException e) {
                code = itemView.getResources().getString(R.string.error_all_caps);
            }

            _code.setText(_hideCodes ? CodeFormatHelper.hide(code) : code);
            _code.setTextColor(MaterialColors.getColor(_code, _hideCodes ? R.attr.colorCodeHidden : R.attr.colorCode));
        }

        void recycle() {
            _entry = null;
            _glide.clear(_icon);
        }
    }

    public interface Listener {
        void onEntryClick(VaultEntry entry);
    }
}
