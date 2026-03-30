package com.hitech.lms.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportChatRequest {

    @NotBlank
    @Size(max = 8000)
    private String message;

    /** Prior turns, oldest first. Optional; used to continue a thread. */
    @Builder.Default
    private List<SupportChatTurn> history = new ArrayList<>();
}
