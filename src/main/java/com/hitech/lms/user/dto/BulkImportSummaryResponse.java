package com.hitech.lms.user.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BulkImportSummaryResponse {
    private int totalRows;
    private int created;
    private int skipped;
    private int errors;
    private List<BulkImportRowResult> results;
}
