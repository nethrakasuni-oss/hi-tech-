package com.hitech.lms.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One turn in a client-side conversation (for multi-turn chat with Gemini).
 * Role is "user" or "assistant" (mapped to "model" when calling the API).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportChatTurn {
    private String role;
    private String text;
}
