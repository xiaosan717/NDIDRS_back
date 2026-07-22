package com.dorm.ndidrs_back.dto;

import java.time.LocalDate;

public class AiAnalysisRequest {
    private LocalDate startDate;
    private LocalDate endDate;
    private String question;
    private String language;

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}
