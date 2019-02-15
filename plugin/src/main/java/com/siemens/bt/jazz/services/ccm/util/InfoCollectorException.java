package com.siemens.bt.jazz.services.ccm.util;
public class InfoCollectorException extends Exception {
    private String summary;
    private String description;
    private InfoCollectorSeverity severity;

    private InfoCollectorException() {
        super();
    }

    private InfoCollectorException(String message) {
        super(message);
    }

    private InfoCollectorException(String message, Throwable t) {
        super(message, t);
    }

    private InfoCollectorException(Throwable t) {
        super(t);
    }

    public InfoCollectorException(String summary, String description, InfoCollectorSeverity severity) {
        this.summary = summary;
        this.description = description;
        this.severity = severity;
    }

    public String getSummary() {
        return summary;
    }

    public String getDescription() {
        return description;
    }

    public int getSeverity() {
        return severity.getSeverity();
    }
}
