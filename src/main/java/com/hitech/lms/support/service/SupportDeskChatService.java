package com.hitech.lms.support.service;

import com.hitech.lms.ai.GeminiClientService;
import com.hitech.lms.auth.model.User;
import com.hitech.lms.support.dto.ArticleResponse;
import com.hitech.lms.support.dto.SupportChatRequest;
import com.hitech.lms.support.dto.SupportChatResponse;
import com.hitech.lms.support.dto.SupportChatTurn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SupportDeskChatService {

    private static final int MAX_HISTORY_TURNS = 20;
    private static final int KB_SNIPPET_CHARS = 1200;
    private static final int KB_MAX_ARTICLES = 3;

    @Autowired
    private GeminiClientService geminiClient;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    public SupportChatResponse chat(SupportChatRequest req, User user) {
        String kbContext = buildKnowledgeBaseContext(req.getMessage().trim());
        String systemText = buildSystemInstruction(user, kbContext);

        List<Map<String, Object>> contents = new ArrayList<>();
        for (SupportChatTurn t : trimHistory(req.getHistory())) {
            if (t.getText() == null || t.getText().isBlank()) continue;
            String role = normalizeRole(t.getRole());
            if (role == null) continue;
            contents.add(Map.of(
                    "role", role,
                    "parts", List.of(Map.of("text", t.getText().trim()))));
        }
        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", req.getMessage().trim()))));

        String reply = geminiClient.generateContent(
                false,
                systemText,
                contents,
                2048,
                0.4);

        return SupportChatResponse.builder()
                .reply(reply)
                .build();
    }

    private String buildSystemInstruction(User user, String kbContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the support assistant for Hi-Tech Institute's Learning Management System (Hi-Tech LMS). ");
        sb.append("Be concise, friendly, and professional. ");
        if (user != null) {
            sb.append("The user is signed in as ").append(user.getFullName())
                    .append(" (role: ").append(user.getRole().name()).append("). ");

            switch (user.getRole()) {
            case STUDENT -> sb.append("Prioritize student-friendly, step-by-step guidance. ")
                .append("When the issue needs account review, billing checks, grading disputes, or manual intervention, ")
                .append("recommend creating a support ticket and mention booking an appointment for academic guidance. ");
            case INSTRUCTOR -> sb.append("Focus on instructor workflows such as appointment availability, course support, and exam operations. ")
                .append("For issues requiring admin or support-staff action, recommend the correct support channel clearly. ");
            case SUPPORT_STAFF -> sb.append("Act as a support-agent copilot. ")
                .append("Provide clear troubleshooting checklists, triage steps, and concise reply suggestions for ticket conversations. ")
                .append("Do not expose sensitive personal data and do not make final policy decisions; advise escalation when needed. ");
            case ADMIN -> sb.append("Act as an operations copilot for an administrator. ")
                .append("Provide concise summaries, likely root causes, and suggested next operational actions. ")
                .append("Highlight when a policy or security-sensitive issue should be reviewed by authorized staff. ");
            default -> sb.append("Provide general support guidance and suggest official support channels when needed. ");
            }
        }
        sb.append("For account-specific or billing issues, suggest creating a support ticket or booking an appointment when appropriate. ");
        sb.append("If you are unsure, say so and point them to the knowledge base or support tickets. ");
        if (!kbContext.isEmpty()) {
            sb.append("Use the following published knowledge base excerpts when relevant; do not invent policy details that contradict them:\n\n");
            sb.append(kbContext);
        }
        return sb.toString();
    }

    private String buildKnowledgeBaseContext(String query) {
        Page<ArticleResponse> page = knowledgeBaseService.searchPublished(query, null, 0, KB_MAX_ARTICLES);
        if (page.isEmpty()) {
            return "";
        }
        return page.getContent().stream()
                .map(a -> {
                    String c = a.getContent() != null ? a.getContent() : "";
                    if (c.length() > KB_SNIPPET_CHARS) {
                        c = c.substring(0, KB_SNIPPET_CHARS) + "…";
                    }
                    return "— " + a.getTitle() + " (" + a.getCategory() + ")\n" + c;
                })
                .collect(Collectors.joining("\n\n"));
    }

    private List<SupportChatTurn> trimHistory(List<SupportChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, history.size() - MAX_HISTORY_TURNS);
        return history.subList(from, history.size());
    }

    private String normalizeRole(String role) {
        if (role == null) return null;
        String r = role.trim().toLowerCase();
        if (r.equals("user")) return "user";
        if (r.equals("model") || r.equals("assistant")) return "model";
        return null;
    }
}
