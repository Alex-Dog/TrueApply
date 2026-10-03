package com.trueapply.model;

import java.time.Instant;

/** Login created on a job site on the user's behalf. Password is decrypted only when shown. */
public class SavedAccount {
    public long id;
    public String company;
    public String siteUrl;
    public String username;
    /** Plain-text password; populated in memory only, encrypted at rest. */
    public String password;
    public Instant createdAt;
    public String notes;
}
