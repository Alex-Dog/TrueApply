package com.trueapply.ai.tasks;

import com.trueapply.model.AnswerSource;
import com.trueapply.model.FieldCategory;
import com.trueapply.model.FieldType;
import com.trueapply.model.FormField;
import com.trueapply.model.UserProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillListTest {
    private static UserProfile profile(String... skills) {
        UserProfile p = new UserProfile();
        p.formSkills = List.of(skills);
        return p;
    }

    @Test
    void checklistGetsOnlyTheListedSkillsThatAreOffered() {
        FormField f = new FormField("s", "Skills", FieldType.MULTI_SELECT, false);
        f.options = List.of("JavaScript", "Java", "Python", "Excel", "C++");
        assertTrue(FormAnswerer.applySkillList(f, profile("Java", "Python", "Rust")));
        assertEquals(List.of("Java", "Python"), f.answers);
        assertEquals(AnswerSource.PROFILE, f.source);
    }

    @Test
    void workdayPickerGetsTheWholeList() {
        FormField f = new FormField("s", "My Experience › Type to Add Skills", FieldType.SINGLE_SELECT, false);
        f.control = "prompt";
        assertTrue(FormAnswerer.applySkillList(f, profile("Java", "SQL")));
        assertEquals(List.of("Java", "SQL"), f.answers);
        assertEquals("Java, SQL", f.answer);
    }

    @Test
    void leavesOtherQuestionsAlone() {
        FormField essay = new FormField("e", "Describe a time you used your skills", FieldType.TEXTAREA, false);
        assertFalse(FormAnswerer.applySkillList(essay, profile("Java")));
        FormField text = new FormField("t", "Technical Skills", FieldType.TEXT, false);
        assertTrue(FormAnswerer.applySkillList(text, profile("Java", "SQL")));
        assertEquals("Java, SQL", text.answer);
        FormField none = new FormField("n", "Skills", FieldType.MULTI_SELECT, false);
        none.options = List.of("Excel");
        assertTrue(FormAnswerer.applySkillList(none, profile("Java")));
        assertEquals(FieldCategory.MISSING_INFO, none.category);
        assertFalse(FormAnswerer.applySkillList(new FormField("x", "Skills", FieldType.TEXT, false), profile()));
    }
}
