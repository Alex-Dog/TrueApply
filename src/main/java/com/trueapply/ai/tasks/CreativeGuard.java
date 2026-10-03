package com.trueapply.ai.tasks;

import com.trueapply.model.FieldType;
import com.trueapply.model.FormField;
import com.trueapply.util.Text;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic backstop for the "no slop" rule. Whatever the AI says, a free-text field that
 * looks like an essay/motivation question is reserved for the human.
 */
public final class CreativeGuard {
    private static final Set<String> ALWAYS_CREATIVE_KEYS = Set.of("cover_letter", "cover_letter_text");

    private static final Pattern CREATIVE_LABEL = Pattern.compile(
            "\\bwhy\\b|motivat|interest(ed|s)? (you )?in|excite|passion|tell us|tell me|describe|explain"
                    + "|share (a|an|about|with|your)|anything else|additional (information|comments)"
                    + "|what (makes|would|do|draws|about)|how (would|do) you|example of|a time (when|you)"
                    + "|proud|challenge|cover letter|in your own words|essay|personal statement|thoughts on",
            Pattern.CASE_INSENSITIVE);

    private CreativeGuard() {
    }

    public static boolean isCreative(FormField field) {
        if (ALWAYS_CREATIVE_KEYS.contains(field.key)) return true;
        if (field.type != FieldType.TEXTAREA) return false;
        String text = Text.orEmpty(field.label) + " " + Text.orEmpty(field.description);
        return CREATIVE_LABEL.matcher(text).find();
    }
}
