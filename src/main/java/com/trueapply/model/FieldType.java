package com.trueapply.model;

public enum FieldType {
    TEXT,
    TEXTAREA,
    SINGLE_SELECT,
    MULTI_SELECT,
    FILE,
    /** Free text with an autocomplete list of places (Greenhouse "Location"). */
    LOCATION
}
