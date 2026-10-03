package com.trueapply.discovery;

import com.trueapply.model.Job;
import com.trueapply.model.UserProfile;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/** A place to find postings. Results are filtered by {@link JobFilter} afterwards. */
public interface JobSource {

    String name();

    /** False when the source needs configuration (e.g. API keys) that isn't there yet. */
    boolean isEnabled();

    List<Job> fetch(UserProfile.JobPreferences preferences, Consumer<String> progress) throws IOException;
}
