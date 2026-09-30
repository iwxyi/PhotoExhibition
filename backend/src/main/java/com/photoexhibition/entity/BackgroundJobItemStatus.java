package com.photoexhibition.entity;

public enum BackgroundJobItemStatus {
    QUEUED,
    WAITING_DEPENDENCY,
    RUNNING,
    BLOCKED,
    SUCCEEDED,
    FAILED,
    SKIPPED,
    CANCELED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == SKIPPED || this == CANCELED;
    }
}
