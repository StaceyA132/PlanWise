package com.planwise.insights;

import java.math.BigDecimal;

/**
 * Spending in one category for the month.
 *
 * @param category        "Electronics", ..., or "Uncategorized" if the AI couldn't categorize it
 * @param percentOfTotal  share of the month's spending, to one decimal place
 */
public record CategoryTotal(String category, BigDecimal total, int purchaseCount, BigDecimal percentOfTotal) {
}
