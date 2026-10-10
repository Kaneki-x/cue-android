package io.cueapp.android.helpers.comparators;

import io.cueapp.android.vault.VaultEntry;

import java.util.Comparator;

public class AccountNameComparator implements Comparator<VaultEntry> {
    @Override
    public int compare(VaultEntry a, VaultEntry b) {
        return a.getName().compareToIgnoreCase(b.getName());
    }
}