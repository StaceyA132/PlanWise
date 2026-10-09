package com.planwise.insights;

import java.time.YearMonth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.planwise.user.CurrentUser;

@RestController
public class InsightsController {

    private final InsightsService insightsService;

    public InsightsController(InsightsService insightsService) {
        this.insightsService = insightsService;
    }

    /** GET /api/insights/monthly or /api/insights/monthly?month=2026-10 */
    @GetMapping("/api/insights/monthly")
    public MonthlyInsightsResponse monthly(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(required = false) YearMonth month) {
        return insightsService.monthly(CurrentUser.id(jwt), month);
    }
}
