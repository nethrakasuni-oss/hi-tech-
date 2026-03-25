package com.hitech.lms.user.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BulkImportRowResult {
    private int rowNumber;
    private String status; // CREATED, SKIPPED, ERROR
    private String email;
    private String name;
    private String message;
}
