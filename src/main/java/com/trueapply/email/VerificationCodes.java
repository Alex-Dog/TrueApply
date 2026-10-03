package com.trueapply.email;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds a one-time code in an email body. */
public final class VerificationCodes {
    private static final Pattern KEYWORD = Pattern.compile("(?i)\\b(?:code|passcode|pin)\\b");
    private static final Pattern TOKEN = Pattern.compile("\\b([A-Za-z0-9]{6,10})\\b");
    /** A line containing nothing but the code. */
    private static final Pattern OWN_LINE = Pattern.compile("(?m)^\\s*([A-Za-z0-9]{6,10})\\s*$");
    /** How far after "code" to look, e.g. "paste this code into the field on your application: AB12CD34". */
    private static final int WINDOW = 150;

    private VerificationCodes() {
    }

    public static Optional<String> extract(String text) {
        if (text == null) return Optional.empty();
        Matcher keyword = KEYWORD.matcher(text);
        while (keyword.find()) {
            String window = text.substring(keyword.end(), Math.min(text.length(), keyword.end() + WINDOW));
            Matcher token = TOKEN.matcher(window);
            while (token.find()) {
                if (looksLikeCode(token.group(1))) return Optional.of(token.group(1));
            }
        }
        Matcher line = OWN_LINE.matcher(text);
        while (line.find()) {
            if (looksLikeCode(line.group(1))) return Optional.of(line.group(1));
        }
        return Optional.empty();
    }

    private static final Pattern URL = Pattern.compile("https?://[^\\s\"'<>)]+");
    private static final Pattern VERIFY_WORDS = Pattern.compile("(?i)verif|activat|confirm|validat");

    /** First link to {@code host} that looks like an account verification/activation link. */
    public static Optional<String> extractLink(String text, String host) {
        if (text == null || host == null) return Optional.empty();
        Matcher m = URL.matcher(text);
        while (m.find()) {
            String url = m.group().replace("&amp;", "&");
            if (url.toLowerCase().contains(host.toLowerCase()) && VERIFY_WORDS.matcher(url).find()) {
                return Optional.of(url);
            }
        }
        return Optional.empty();
    }

    /** Codes contain at least one digit; that rules out words like "below" or "expires". */
    static boolean looksLikeCode(String token) {
        return token.chars().anyMatch(Character::isDigit);
    }
}
