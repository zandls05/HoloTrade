package com.holotrade;

public enum PayMode {
    ITEM("Solo items"),
    MONEY("Solo dinero"),
    BOTH("Items o dinero");

    public final String label;

    PayMode(String label) {
        this.label = label;
    }

    public PayMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
