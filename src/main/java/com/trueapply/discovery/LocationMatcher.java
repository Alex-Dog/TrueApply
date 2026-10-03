package com.trueapply.discovery;

import com.trueapply.util.Text;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides whether a posting's location fits the user's preferred locations. Understands
 * countries ("United States" matches "Austin, TX" and "Remote - US" but not "Remote - UK"),
 * multi-location postings ("London; New York, NY"), and remote qualifiers.
 */
public final class LocationMatcher {
    private static final Pattern SEGMENT_SPLIT = Pattern.compile("[;•|/]|\\s+or\\s+|\\s+and\\s+");
    private static final Pattern REMOTE_WORDS = Pattern.compile(
            "\\b(remote|remotely|anywhere|first|friendly|hybrid|based|in|only|within|from|location|flexible|of|the)\\b");
    private static final Set<String> GLOBAL = Set.of("worldwide", "global", "anywhere", "international");
    private static final Pattern MULTIPLE = Pattern.compile("(?i)\\d+\\s+locations");

    /** Canonical country → names and abbreviations that refer to it (normalized, dots removed). */
    private static final Map<String, List<String>> COUNTRIES = new LinkedHashMap<>();

    static {
        country("united states", "united states", "united states of america", "usa", "us", "america");
        country("canada", "canada");
        country("united kingdom", "united kingdom", "uk", "england", "scotland", "wales", "great britain", "britain");
        country("ireland", "ireland");
        country("germany", "germany", "deutschland");
        country("france", "france");
        country("netherlands", "netherlands", "holland");
        country("spain", "spain");
        country("portugal", "portugal");
        country("italy", "italy");
        country("poland", "poland");
        country("sweden", "sweden");
        country("switzerland", "switzerland");
        country("india", "india");
        country("japan", "japan");
        country("singapore", "singapore");
        country("australia", "australia");
        country("new zealand", "new zealand");
        country("brazil", "brazil");
        country("mexico", "mexico");
        country("argentina", "argentina");
        country("colombia", "colombia");
        country("israel", "israel");
        country("south korea", "south korea", "korea");
        country("china", "china");
        country("philippines", "philippines");
        country("emea", "emea", "europe", "eu");
        country("apac", "apac", "asia");
        country("latam", "latam", "latin america");
    }

    private static final Set<String> US_STATES = Set.of(
            "alabama", "alaska", "arizona", "arkansas", "california", "colorado", "connecticut", "delaware",
            "florida", "georgia", "hawaii", "idaho", "illinois", "indiana", "iowa", "kansas", "kentucky",
            "louisiana", "maine", "maryland", "massachusetts", "michigan", "minnesota", "mississippi", "missouri",
            "montana", "nebraska", "nevada", "new hampshire", "new jersey", "new mexico", "new york",
            "north carolina", "north dakota", "ohio", "oklahoma", "oregon", "pennsylvania", "rhode island",
            "south carolina", "south dakota", "tennessee", "texas", "utah", "vermont", "virginia", "washington",
            "west virginia", "wisconsin", "wyoming", "district of columbia");
    private static final Set<String> US_STATE_CODES = Set.of(
            "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA", "HI", "ID", "IL", "IN", "IA", "KS", "KY",
            "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ", "NM", "NY", "NC", "ND",
            "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY", "DC");
    /** Big US metros that postings often list without a state. */
    private static final Set<String> US_CITIES = Set.of(
            "san francisco", "bay area", "silicon valley", "new york", "nyc", "seattle", "austin", "boston",
            "chicago", "los angeles", "denver", "atlanta", "miami", "pittsburgh", "philadelphia", "san diego",
            "san jose", "palo alto", "mountain view", "menlo park", "sunnyvale", "dallas", "houston", "phoenix",
            "salt lake city", "minneapolis", "detroit", "raleigh", "nashville", "washington dc", "washington, dc");
    private static final Pattern STATE_CODE = Pattern.compile("(?:,|\\b)\\s*([A-Z]{2})\\b");

    private LocationMatcher() {
    }

    private static void country(String canonical, String... names) {
        COUNTRIES.put(canonical, List.of(names));
    }

    /** "USA", "U.S.", "United States" → "united states"; empty when the text isn't a known country. */
    public static Optional<String> countryOf(String text) {
        String wanted = compact(text);
        for (Map.Entry<String, List<String>> e : COUNTRIES.entrySet()) {
            if (e.getValue().contains(wanted)) return Optional.of(e.getKey());
        }
        return Optional.empty();
    }

    /**
     * @param homeCountry country used to judge remote postings when the preferences name only
     *                    cities; may be blank
     */
    public static boolean matches(String jobLocation, List<String> preferred, boolean remoteOk, String homeCountry) {
        if (preferred.isEmpty()) return true;
        String home = preferred.stream().map(LocationMatcher::countryOf).flatMap(Optional::stream).findFirst()
                .or(() -> countryOf(homeCountry))
                .orElse(null);

        // Workday collapses multi-location postings to "3 Locations"; we can't judge those, so keep them.
        if (MULTIPLE.matcher(Text.orEmpty(jobLocation).trim()).matches()) return true;
        for (String segment : SEGMENT_SPLIT.split(Text.orEmpty(jobLocation))) {
            if (segment.isBlank()) continue;
            String normalized = Text.normalize(segment);
            boolean remote = normalized.contains("remote") || normalized.contains("anywhere");

            for (String p : preferred) {
                Optional<String> country = countryOf(p);
                if (country.isPresent() ? inCountry(segment, country.get()) : normalized.contains(Text.normalize(p))) {
                    if (!remote || remoteOk) return true;
                }
            }
            if (remote && remoteOk) {
                String qualifier = REMOTE_WORDS.matcher(compact(segment)).replaceAll(" ").replaceAll("[^a-z ]", " ").trim();
                if (qualifier.isEmpty() || GLOBAL.contains(qualifier)) return true;
                if (home != null && inCountry(segment, home)) return true;
            }
        }
        return false;
    }

    static boolean inCountry(String segment, String country) {
        String text = " " + compact(segment) + " ";
        // US places whose names contain another country's name.
        if (text.contains(" new mexico ") || text.contains(" new england ")) return country.equals("united states");
        for (Map.Entry<String, List<String>> e : COUNTRIES.entrySet()) {
            boolean named = e.getValue().stream().anyMatch(n -> text.contains(" " + n + " "));
            if (named) return e.getKey().equals(country); // an explicit country wins over city/state guesses
        }
        if (!country.equals("united states")) return false;
        if (US_STATES.stream().anyMatch(s -> text.contains(" " + s + " "))) return true;
        if (US_CITIES.stream().anyMatch(c -> text.contains(" " + c.replace(",", "") + " "))) return true;
        Matcher m = STATE_CODE.matcher(segment);
        while (m.find()) {
            if (US_STATE_CODES.contains(m.group(1))) return true;
        }
        return false;
    }

    /** Lowercase, dots dropped ("U.S." → "us"), other punctuation turned into spaces. */
    private static String compact(String s) {
        String lower = Text.normalize(s).replace(".", "");
        return String.join(" ", Arrays.stream(lower.split("[^a-z0-9]+")).filter(t -> !t.isEmpty()).toList());
    }
}
