package com.dorm.ndidrs_back.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class AiAnalysisResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime generatedAt;
    private boolean aiAvailable;
    private String provider;
    private String report;
    private Map<String, Object> snapshot;
    private List<Map<String, Object>> dailyTrend;
    private List<Map<String, Object>> riskRooms;
    private List<Map<String, Object>> anomalyRecords;

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public boolean isAiAvailable() { return aiAvailable; }
    public void setAiAvailable(boolean aiAvailable) { this.aiAvailable = aiAvailable; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getReport() { return report; }
    public void setReport(String report) { this.report = report; }
    public Map<String, Object> getSnapshot() { return snapshot; }
    public void setSnapshot(Map<String, Object> snapshot) { this.snapshot = snapshot; }
    public List<Map<String, Object>> getDailyTrend() { return dailyTrend; }
    public void setDailyTrend(List<Map<String, Object>> dailyTrend) { this.dailyTrend = dailyTrend; }
    public List<Map<String, Object>> getRiskRooms() { return riskRooms; }
    public void setRiskRooms(List<Map<String, Object>> riskRooms) { this.riskRooms = riskRooms; }
    public List<Map<String, Object>> getAnomalyRecords() { return anomalyRecords; }
    public void setAnomalyRecords(List<Map<String, Object>> anomalyRecords) { this.anomalyRecords = anomalyRecords; }
}
