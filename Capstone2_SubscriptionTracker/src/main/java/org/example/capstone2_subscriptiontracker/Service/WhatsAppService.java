package org.example.capstone2_subscriptiontracker.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class WhatsAppService {

    @Value("${ultramsg.instance.id}")
    private String instanceId;

    @Value("${ultramsg.token}")
    private String token;

    public String sendWhatsAppMessage(String phoneNumber, String message) {

        String url =
                "https://api.ultramsg.com/" +
                        instanceId +
                        "/messages/chat";

        RestTemplate restTemplate = new RestTemplate();

        MultiValueMap<String, String> body =
                new LinkedMultiValueMap<>();

        body.add("token", token);

        body.add("to", phoneNumber);

        body.add("body", message);

        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(
                MediaType.APPLICATION_FORM_URLENCODED
        );

        HttpEntity<MultiValueMap<String, String>> request =
                new HttpEntity<>(body, headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        url,
                        request,
                        String.class
                );

        return response.getBody();
    }
}
