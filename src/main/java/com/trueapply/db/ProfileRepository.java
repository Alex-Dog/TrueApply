package com.trueapply.db;

import com.trueapply.model.UserProfile;
import com.trueapply.util.Json;

/** The profile is a single JSON document in the settings table. */
public class ProfileRepository {
    private static final String KEY = "profile";

    private final SettingsRepository settings;

    public ProfileRepository(SettingsRepository settings) {
        this.settings = settings;
    }

    public UserProfile load() {
        return settings.get(KEY).map(json -> Json.read(json, UserProfile.class)).orElseGet(UserProfile::new);
    }

    public void save(UserProfile profile) {
        settings.put(KEY, Json.write(profile));
    }
}
