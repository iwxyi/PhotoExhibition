package com.photoexhibition.entity;

public enum BackgroundJobStatus {
    QUEUED,
    WAITING_DEPENDENCY,
    RUNNING,
    PAUSED,
    BLOCKED,
    SUCCEEDED,
    PARTIAL_SUCCESS,
    FAILED,
    SKIPPED,
    CANCELED,
    IGNORED,
    RETRIED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == PARTIAL_SUCCESS || this == FAILED
            || this == SKIPPED || this == CANCELED || this == IGNORED || this == RETRIED;
    }
}
