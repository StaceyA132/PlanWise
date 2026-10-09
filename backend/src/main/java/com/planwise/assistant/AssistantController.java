package com.planwise.assistant;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.planwise.user.CurrentUser;

@RestController
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/api/assistant/ask")
    public AssistantResponse ask(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AskRequest request) {
        return assistantService.ask(CurrentUser.id(jwt), request);
    }
}
