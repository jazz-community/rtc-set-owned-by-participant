package com.siemens.bt.jazz.services.ccm.util;

public enum InfoCollectorSeverity {
    OK(0), WARNING(2), ERROR(4);
    private final int value;

    InfoCollectorSeverity(int value) {
        this.value = value;
    }

    public int getSeverity() {
        return value;
    }
}
