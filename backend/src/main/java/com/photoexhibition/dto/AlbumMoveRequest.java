package com.photoexhibition.dto;

import lombok.Data;

@Data
public class AlbumMoveRequest {
    private String targetPath;
    private String conflictResolution; // "overwrite", "rename", or null (= check only)
}
