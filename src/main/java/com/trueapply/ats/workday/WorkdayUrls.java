package com.trueapply.ats.workday;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Workday career sites live at {tenant}.wd{N}.myworkdayjobs.com/{locale?}/{site}; job pages
 * add /job/{location}/{Title}_{RequisitionId}.
 */
public final class WorkdayUrls {
    private static final Pattern URL = Pattern.compile(
            "https?://([A-Za-z0-9-]+)\\.(wd\\d+)\\.myworkdayjobs\\.com/(?:[a-z]{2}-[A-Z]{2}/)?([^/?#]+)(/job/[^?#]+)?");

    /** One company's career site. */
    public record Site(String tenant, String dataCenter, String site) {
        public String host() {
            return tenant + "." + dataCenter + ".myworkdayjobs.com";
        }

        /** Compact id used in settings and dedupe keys, e.g. "nvidia.wd5/NVIDIAExternalCareerSite". */
        public String board() {
            return tenant + "." + dataCenter + "/" + site;
        }

        /** Base of Workday's public job-search JSON API for this site. */
        public String apiBase() {
            return "https://" + host() + "/wday/cxs/" + tenant + "/" + site;
        }

        public String jobUrl(String externalPath) {
            return "https://" + host() + "/en-US/" + site + externalPath;
        }
    }

    /** A job on a site; {@code externalPath} starts with "/job/". */
    public record JobRef(Site site, String externalPath) {
        /** Requisition id, the stable part of the path ("..._JR2024692" → "JR2024692"). */
        public String reqId() {
            String last = externalPath.substring(externalPath.lastIndexOf('/') + 1);
            int underscore = last.lastIndexOf('_');
            return underscore >= 0 && underscore < last.length() - 1 ? last.substring(underscore + 1) : last;
        }
    }

    private WorkdayUrls() {
    }

    public static Optional<Site> site(String url) {
        if (url == null) return Optional.empty();
        Matcher m = URL.matcher(url);
        if (!m.find() || m.group(3).equals("wday")) return Optional.empty();
        return Optional.of(new Site(m.group(1).toLowerCase(), m.group(2).toLowerCase(), m.group(3)));
    }

    public static Optional<JobRef> job(String url) {
        if (url == null) return Optional.empty();
        Matcher m = URL.matcher(url);
        if (!m.find() || m.group(4) == null || m.group(3).equals("wday")) return Optional.empty();
        String path = m.group(4).replaceFirst("/apply(/.*)?$", "").replaceFirst("/$", "");
        return Optional.of(new JobRef(new Site(m.group(1).toLowerCase(), m.group(2).toLowerCase(), m.group(3)), path));
    }

    /** Parses the settings form "tenant.wdN/Site". */
    public static Optional<Site> parseBoard(String board) {
        if (board == null) return Optional.empty();
        Matcher m = Pattern.compile("^\\s*([A-Za-z0-9-]+)\\.(wd\\d+)/([^/\\s|]+)").matcher(board);
        return m.find() ? Optional.of(new Site(m.group(1).toLowerCase(), m.group(2).toLowerCase(), m.group(3))) : Optional.empty();
    }
}
