package com.photoexhibition.service;

/** Published after a photo reaches the completed scan state. */
public final class PhotoScanCompletedEvent {
    private final Long photoId;
    private final Long ownerUserId;

    public PhotoScanCompletedEvent(Long photoId, Long ownerUserId) {
        this.photoId = photoId;
        this.ownerUserId = ownerUserId;
    }

    public Long getPhotoId() { return photoId; }
    public Long getOwnerUserId() { return ownerUserId; }
}
