package com.planwise.purchase;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The only spending categories PlanWise allows. The AI must pick one of these. */
public enum Category {
    ELECTRONICS("Electronics"),
    CLOTHING("Clothing"),
    HOME("Home"),
    TRAVEL("Travel"),
    OTHER("Other");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static List<String> displayNames() {
        return Arrays.stream(values()).map(Category::displayName).toList();
    }

    /**
     * Matches a name from the allowed list, ignoring case and surrounding spaces ("electronics" is fine).
     * Anything else ("Gadgets", "Electronics & Tech", null) is empty: not on the list, so rejected.
     */
    public static Optional<Category> fromDisplayName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(category -> category.displayName.toLowerCase(Locale.ROOT).equals(wanted))
                .findFirst();
    }
}
