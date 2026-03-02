package com.photoexhibition.dto;

import lombok.Data;
import java.util.List;

@Data
public class AlbumMoveResult {
    private boolean success;
    private boolean conflict;
    private String message;
    private String conflictType;        // "same_name_folder"
    private String conflictPath;
    private List<String> conflictFiles; // list of files in the conflicting folder
    private int conflictPhotoCount;
    private String suggestedNewName;
    private AlbumDTO album;
}
