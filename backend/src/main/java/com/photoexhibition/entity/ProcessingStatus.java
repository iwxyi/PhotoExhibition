package com.photoexhibition.entity;

/**
 * 照片处理状态枚举
 */
public enum ProcessingStatus {
    PENDING("pending", "待处理"),
    BASIC_INFO_DONE("basic_info_done", "基础信息完成"),
    THUMBNAILS_DONE("thumbnails_done", "缩略图完成"),
    ANALYSIS_DONE("analysis_done", "分析完成"),
    FACES_DONE("faces_done", "人脸检测完成"),
    SUBJECT_DONE("subject_done", "主体检测完成"),
    TAGS_DONE("tags_done", "标签完成"),
    COMPLETED("completed", "完全完成"),
    FAILED("failed", "处理失败");

    private final String code;
    private final String description;

    ProcessingStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static ProcessingStatus fromCode(String code) {
        for (ProcessingStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return PENDING;
    }

    /**
     * 获取下一个处理步骤
     */
    public ProcessingStatus getNextStep() {
        switch (this) {
            case PENDING: return BASIC_INFO_DONE;
            case BASIC_INFO_DONE: return THUMBNAILS_DONE;
            case THUMBNAILS_DONE: return ANALYSIS_DONE;
            case ANALYSIS_DONE: return FACES_DONE;
            case FACES_DONE: return SUBJECT_DONE;
            case SUBJECT_DONE: return TAGS_DONE;
            case TAGS_DONE: return COMPLETED;
            case COMPLETED: return COMPLETED;
            case FAILED: return FAILED;
            default: return PENDING;
        }
    }

    /**
     * 检查是否可以跳过处理（用于断点续上）
     */
    public boolean canSkip() {
        return this == COMPLETED;
    }

    /**
     * 检查是否需要重新处理
     */
    public boolean needsReprocessing(boolean force) {
        if (force) {
            return true; // 强制扫描时全部重新处理
        }
        // 非强制扫描时，只有失败和待处理状态需要重新处理
        // 中间状态（如缩略图已完成但后续步骤未完成）应该继续处理
        return this == FAILED || this == PENDING;
    }

    /**
     * 检查是否需要继续处理（从当前状态开始）
     */
    public boolean needsContinuation(boolean force) {
        if (force) {
            return true; // 强制扫描时全部重新处理
        }
        // 非强制扫描时，失败状态需要重新开始，已完成的不需要处理
        // 中间状态需要继续处理
        return this != COMPLETED;
    }
}
