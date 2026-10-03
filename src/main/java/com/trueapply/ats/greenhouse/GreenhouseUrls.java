package com.trueapply.ats.greenhouse;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recognizes Greenhouse job URLs in their various shapes. */
public final class GreenhouseUrls {
    /** job-boards.greenhouse.io/{board}/jobs/{id} and the legacy boards.greenhouse.io host. */
    private static final Pattern HOSTED = Pattern.compile(
            "https?://(?:job-boards|boards)(?:\\.eu)?\\.greenhouse\\.io/([A-Za-z0-9_-]+)/jobs/(\\d+)");
    /** .../embed/job_app?for={board}&token={id} (parameters in either order). */
    private static final Pattern EMBED_FOR = Pattern.compile("[?&]for=([A-Za-z0-9_-]+)");
    private static final Pattern EMBED_TOKEN = Pattern.compile("[?&]token=(\\d+)");
    /** Company career pages embed Greenhouse and pass the job id as gh_jid. */
    private static final Pattern GH_JID = Pattern.compile("[?&]gh_jid=(\\d+)");
    /** Board token mentioned anywhere in a page's HTML (embed script/iframe). */
    private static final Pattern BOARD_IN_HTML = Pattern.compile(
            "greenhouse\\.io/(?:embed/job_(?:board|app)(?:/js)?\\?for=|)([A-Za-z0-9_-]+)(?:/jobs|[\"'&])");

    public record Ref(String board, String jobId) {
    }

    private GreenhouseUrls() {
    }

    public static Optional<Ref> parse(String url) {
        if (url == null) return Optional.empty();
        Matcher hosted = HOSTED.matcher(url);
        if (hosted.find()) return Optional.of(new Ref(hosted.group(1), hosted.group(2)));
        if (url.contains("greenhouse.io") && url.contains("job_app")) {
            Matcher board = EMBED_FOR.matcher(url);
            Matcher token = EMBED_TOKEN.matcher(url);
            if (board.find() && token.find()) return Optional.of(new Ref(board.group(1), token.group(1)));
        }
        return Optional.empty();
    }

    /** Job id from a company careers URL like https://stripe.com/jobs/listing/x?gh_jid=123. */
    public static Optional<String> ghJid(String url) {
        if (url == null) return Optional.empty();
        Matcher m = GH_JID.matcher(url);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /** Best-effort board token from a careers page that embeds Greenhouse. */
    public static Optional<String> boardFromHtml(String html) {
        if (html == null) return Optional.empty();
        Matcher m = BOARD_IN_HTML.matcher(html);
        while (m.find()) {
            String token = m.group(1);
            if (!token.equals("embed") && !token.equals("boards") && !token.equals("job-boards")) {
                return Optional.of(token);
            }
        }
        return Optional.empty();
    }

    /** Stand-alone application form for any Greenhouse job, independent of the company's site. */
    public static String embedFormUrl(String board, String jobId) {
        return "https://job-boards.greenhouse.io/embed/job_app?for=" + board + "&token=" + jobId;
    }

    public static String host(String url) {
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
