package com.planwise.assistant;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks that every dollar amount and percentage in the AI's reply is one we gave it.
 * If the AI calculated or made up a number, the reply is rejected.
 */
public final class AiNumberGuard {

    // $1,234.56   $300   $ 25.5
    private static final Pattern MONEY = Pattern.compile("\\$\\s*(\\d{1,3}(?:,\\d{3})+|\\d+)(\\.\\d+)?");
    // 26.7%   10 %
    private static final Pattern PERCENT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%");

    private final Set<BigDecimal> allowedMoney;
    private final Set<BigDecimal> allowedPercents;

    public AiNumberGuard(Set<BigDecimal> allowedMoney, Set<BigDecimal> allowedPercents) {
        this.allowedMoney = normalize(allowedMoney);
        this.allowedPercents = normalize(allowedPercents);
    }

    /** The numbers in {@code text} that we never provided. Empty means the text is safe to show. */
    public List<String> unknownNumbers(String text) {
        List<String> unknown = new ArrayList<>();
        Matcher money = MONEY.matcher(text);
        while (money.find()) {
            String digits = money.group(1).replace(",", "") + (money.group(2) == null ? "" : money.group(2));
            if (!allowedMoney.contains(normalize(new BigDecimal(digits)))) {
                unknown.add(money.group());
            }
        }
        Matcher percent = PERCENT.matcher(text);
        while (percent.find()) {
            if (!allowedPercents.contains(normalize(new BigDecimal(percent.group(1))))) {
                unknown.add(percent.group());
            }
        }
        return unknown;
    }

    // 300, 300.0, and 300.00 are the same number; BigDecimal.equals would say they differ.
    private static BigDecimal normalize(BigDecimal value) {
        return value.stripTrailingZeros();
    }

    private static Set<BigDecimal> normalize(Set<BigDecimal> values) {
        return values.stream().map(AiNumberGuard::normalize).collect(Collectors.toUnmodifiableSet());
    }
}
