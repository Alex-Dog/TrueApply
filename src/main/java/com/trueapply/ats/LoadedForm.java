package com.trueapply.ats;

import com.trueapply.model.FormField;

import java.util.List;

public record LoadedForm(List<FormField> fields, String jobDescriptionHtml, String companyDescriptionHtml) {
}
