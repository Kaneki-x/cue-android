package io.cueapp.android.ui.views;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import io.cueapp.android.R;
import io.cueapp.android.ui.models.ErrorCardInfo;
import com.google.android.material.card.MaterialCardView;

public class ErrorCardHolder extends RecyclerView.ViewHolder {
    public ErrorCardHolder(@NonNull View itemView, ErrorCardInfo info) {
        super(itemView);

        TextView errorTextView = itemView.findViewById(R.id.text_error_bar);
        errorTextView.setText(info.getMessage());

        MaterialCardView errorCard = itemView.findViewById(R.id.card_error);
        errorCard.setOnClickListener(info.getListener());
    }
}
