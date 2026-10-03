package com.trueapply.ats;

import com.trueapply.ats.greenhouse.GreenhouseUrls;
import com.trueapply.ats.workday.WorkdayUrls;
import com.trueapply.model.AtsType;
import com.trueapply.model.Job;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recognizes which applicant tracking system a posting URL belongs to. */
public final class AtsUrls {
    private static final Pattern LEVER = Pattern.compile(
            "https?://jobs(?:\\.eu)?\\.lever\\.co/([A-Za-z0-9._-]+)/([0-9a-fA-F-]{36})");
    private static final Pattern ASHBY = Pattern.compile(
            "https?://jobs\\.ashbyhq\\.com/([A-Za-z0-9._%-]+)/([0-9a-fA-F-]{36})");

    public record AtsRef(AtsType ats, String board, String jobId) {
        public String dedupeKey() {
            return ats.name().toLowerCase(Locale.ROOT) + ":" + board.toLowerCase(Locale.ROOT) + ":" + jobId;
        }
    }

    private AtsUrls() {
    }

    public static Optional<AtsRef> detect(String url) {
        if (url == null) return Optional.empty();
        Optional<GreenhouseUrls.Ref> gh = GreenhouseUrls.parse(url);
        if (gh.isPresent()) return Optional.of(new AtsRef(AtsType.GREENHOUSE, gh.get().board(), gh.get().jobId()));
        Matcher m = LEVER.matcher(url);
        if (m.find()) return Optional.of(new AtsRef(AtsType.LEVER, m.group(1), m.group(2)));
        m = ASHBY.matcher(url);
        if (m.find()) return Optional.of(new AtsRef(AtsType.ASHBY, m.group(1), m.group(2)));
        Optional<WorkdayUrls.JobRef> wd = WorkdayUrls.job(url);
        if (wd.isPresent()) return Optional.of(new AtsRef(AtsType.WORKDAY, wd.get().site().board(), wd.get().reqId()));
        return Optional.empty();
    }

    /** Tags the job with its ATS (and the matching dedupe key) when the URL is recognized. */
    public static boolean tag(Job job, String url) {
        Optional<AtsRef> ref = detect(url);
        if (ref.isEmpty()) return false;
        job.ats = ref.get().ats();
        job.atsBoard = ref.get().board();
        job.atsJobId = ref.get().jobId();
        job.dedupeKey = ref.get().dedupeKey();
        return true;
    }
}
