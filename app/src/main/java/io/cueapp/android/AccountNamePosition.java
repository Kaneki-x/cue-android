package io.cueapp.android;

public enum AccountNamePosition {
    HIDDEN,
    END,
    BELOW;

    private static AccountNamePosition[] _values;

    static {
        _values = values();
    }

    public static AccountNamePosition fromInteger(int x) {
        return _values[x];
    }
}
