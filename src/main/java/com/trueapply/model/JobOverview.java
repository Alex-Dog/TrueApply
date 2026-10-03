package com.trueapply.model;

import java.util.List;

/** AI-written summary of a posting, shown in the inbox so the user knows what they're answering for. */
public record JobOverview(
        String roleSummary,
        List<String> responsibilities,
        List<String> requirements,
        String companySummary,
        String compensation) {
}
