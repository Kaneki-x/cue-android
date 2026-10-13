package io.cueapp.android.ui.views;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import io.cueapp.android.R;
import io.cueapp.android.vault.VaultGroup;

public class GroupHolder extends RecyclerView.ViewHolder {
    private TextView _slotName;
    private TextView _count;
    private View _entries;
    private ImageView _buttonEdit;
    private ImageView _buttonDelete;

    public GroupHolder(final View view) {
        super(view);
        _slotName = view.findViewById(R.id.text_group_name);
        _count = view.findViewById(R.id.text_group_count);
        _entries = view.findViewById(R.id.group_entries);
        _buttonEdit = view.findViewById(R.id.button_edit);
        _buttonDelete = view.findViewById(R.id.button_delete);
    }

    public void setData(VaultGroup group, int entryCount) {
        _slotName.setText(group.getName());
        _count.setText(String.valueOf(entryCount));
    }

    public void setOnEntriesClickListener(View.OnClickListener listener) {
        _entries.setOnClickListener(listener);
    }

    public void setOnEditClickListener(View.OnClickListener listener) {
        _buttonEdit.setOnClickListener(listener);
    }

    public void setOnDeleteClickListener(View.OnClickListener listener) {
        _buttonDelete.setOnClickListener(listener);
    }
}
