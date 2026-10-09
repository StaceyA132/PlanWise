package com.planwise.plan;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.planwise.user.CurrentUser;

@RestController
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @PostMapping("/api/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePlanRequest request) {
        return planService.create(CurrentUser.id(jwt), request);
    }

    @GetMapping("/api/plans")
    public List<PlanResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return planService.list(CurrentUser.id(jwt));
    }

    @GetMapping("/api/plans/{id}")
    public PlanResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return planService.get(CurrentUser.id(jwt), id);
    }

    @PostMapping("/api/payments/{id}/pay")
    public PlanResponse pay(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return planService.pay(CurrentUser.id(jwt), id);
    }
}
