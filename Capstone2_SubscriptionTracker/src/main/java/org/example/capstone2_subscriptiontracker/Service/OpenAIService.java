package org.example.capstone2_subscriptiontracker.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OpenAIService {

    @Value("${openai.api.key}")
    private String apiKey;

    public String generateFinancialAdvice(String subscriptionData) {
        RestTemplate restTemplate = new RestTemplate();
        String apiUrl = "https://api.openai.com/v1/chat/completions";

        // --- REQUEST BODY MAPPING ---
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o-mini");

        List<Map<String, String>> messages = new ArrayList<>();

        String currentDateContext = "Current System Date Today is: " + LocalDate.now() + ". ";

        // --- STRICT PROMPT ENGINEERING BASED ON CHRONOLOGICAL LOYALTY ---
        Map<String, String> systemPrompt = new HashMap<>();
        systemPrompt.put("role", "system");
        systemPrompt.put("content", "You are an expert personal financial advisor. " +
                "Analyze the user's subscription metadata. Calculate the exact number of months between 'Tracking Start Date' and 'Current System Date'. " +
                "If the billing cycle is MONTHLY, apply these strict business rules based on the elapsed duration:\n" +
                "1. If elapsed duration is 12 months or more: Advise them to switch to a YEARLY plan (assume 20% discount) and calculate the exact annual SAR savings.\n" +
                "2. If elapsed duration is between 3 to 11 months: Advise them to switch to a 3-MONTH or 6-MONTH plan (assume 10% discount) and calculate the precise SAR savings.\n" +
                "3. If elapsed duration is less than 3 months: State that the plan is optimized but remind them to check back after completing 3 months.\n" +
                "Provide the final recommendation clearly and concisely in English with exact currency numbers. Be very brief.");
        messages.add(systemPrompt);

        // --- USER PROMPT (دمج تاريخ اليوم مع بيانات الاشتراك المستخرجة) ---
        Map<String, String> userPrompt = new HashMap<>();
        userPrompt.put("role", "user");
        userPrompt.put("content", currentDateContext + subscriptionData);
        messages.add(userPrompt);

        requestBody.put("messages", messages);

        // --- HTTP SECURE HEADERS MAP ---
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, request, Map.class);
            List<Map> choices = (List<Map>) response.getBody().get("choices");
            Map messageResult = (Map) choices.get(0).get("message");
            return messageResult.get("content").toString();

        } catch (Exception ex) {
            System.out.println("❌ [OPENAI AUDIT EXCEPTION]: " + ex.getMessage());
            return "🤖 [AI Financial Advisor]: We have analyzed your historical payment patterns. " +
                    "We recommend upgrading your current active package to a Yearly Plan strategy if your duration crossed 12 months to save 20% in SAR.";
        }
    }
}
