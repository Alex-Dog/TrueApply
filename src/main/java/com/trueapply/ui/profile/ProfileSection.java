package com.trueapply.ui.profile;

import com.trueapply.model.UserProfile;
import javafx.scene.Node;

/** One editable slice of the profile, reused by onboarding and the Profile page. */
public interface ProfileSection {
    Node view();

    void load(UserProfile profile);

    void save(UserProfile profile);

    /** Returns an error message, or null when the input is acceptable. */
    default String validate() {
        return null;
    }
}
