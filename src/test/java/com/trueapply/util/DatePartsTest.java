package com.trueapply.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatePartsTest {
    @Test
    void parsesCommonResumeFormats() {
        assertEquals(new DateParts("June", "2023"), DateParts.parse("Jun 2023"));
        assertEquals(new DateParts("September", "2021"), DateParts.parse("Sept. 2021"));
        assertEquals(new DateParts("March", "2020"), DateParts.parse("03/2020"));
        assertEquals(new DateParts(null, "2019"), DateParts.parse("2019"));
        assertEquals(new DateParts(null, null), DateParts.parse("Present"));
        assertEquals(new DateParts(null, null), DateParts.parse(null));
    }
}
