package io.jenkins.plugins.wiz;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

public class WizScannerResult {
    private String scannedResource;
    private String scanTime;
    private ScanStatus status;
    private int secretTotalCount;
    private Vulnerabilities vulnerabilities;
    private ScanStatistics scanStatistics;
    private String reportUrl;

    public enum ScanStatus {
        Passed,
        Failed,
        InProgress,
        Warned
    }

    public static class Vulnerabilities {
        private int infoCount;
        private int lowCount;
        private int mediumCount;
        private int highCount;
        private int criticalCount;
        private int unfixedCount;
        private int totalCount;

        // Getters and Setters
        public int getInfoCount() {
            return infoCount;
        }

        public void setInfoCount(int infoCount) {
            this.infoCount = infoCount;
        }

        public int getLowCount() {
            return lowCount;
        }

        public void setLowCount(int lowCount) {
            this.lowCount = lowCount;
        }

        public int getMediumCount() {
            return mediumCount;
        }

        public void setMediumCount(int mediumCount) {
            this.mediumCount = mediumCount;
        }

        public int getHighCount() {
            return highCount;
        }

        public void setHighCount(int highCount) {
            this.highCount = highCount;
        }

        public int getCriticalCount() {
            return criticalCount;
        }

        public void setCriticalCount(int criticalCount) {
            this.criticalCount = criticalCount;
        }

        public int getUnfixedCount() {
            return unfixedCount;
        }

        public void setUnfixedCount(int unfixedCount) {
            this.unfixedCount = unfixedCount;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public void setTotalCount(int totalCount) {
            this.totalCount = totalCount;
        }
    }

    public static class ScanStatistics {
        private int infoMatches;
        private int lowMatches;
        private int mediumMatches;
        private int highMatches;
        private int criticalMatches;
        private int totalMatches;
        private int filesFound;
        private int filesParsed;
        private int queriesLoaded;
        private int queriesExecuted;
        private int queriesExecutionFailed;

        // Getters and Setters
        public int getInfoMatches() {
            return infoMatches;
        }

        public void setInfoMatches(int infoMatches) {
            this.infoMatches = infoMatches;
        }

        public int getLowMatches() {
            return lowMatches;
        }

        public void setLowMatches(int lowMatches) {
            this.lowMatches = lowMatches;
        }

        public int getMediumMatches() {
            return mediumMatches;
        }

        public void setMediumMatches(int mediumMatches) {
            this.mediumMatches = mediumMatches;
        }

        public int getHighMatches() {
            return highMatches;
        }

        public void setHighMatches(int highMatches) {
            this.highMatches = highMatches;
        }

        public int getCriticalMatches() {
            return criticalMatches;
        }

        public void setCriticalMatches(int criticalMatches) {
            this.criticalMatches = criticalMatches;
        }

        public int getTotalMatches() {
            return totalMatches;
        }

        public void setTotalMatches(int totalMatches) {
            this.totalMatches = totalMatches;
        }

        public int getFilesFound() {
            return filesFound;
        }

        public void setFilesFound(int filesFound) {
            this.filesFound = filesFound;
        }

        public int getFilesParsed() {
            return filesParsed;
        }

        public void setFilesParsed(int filesParsed) {
            this.filesParsed = filesParsed;
        }

        public int getQueriesLoaded() {
            return queriesLoaded;
        }

        public void setQueriesLoaded(int queriesLoaded) {
            this.queriesLoaded = queriesLoaded;
        }

        public int getQueriesExecuted() {
            return queriesExecuted;
        }

        public void setQueriesExecuted(int queriesExecuted) {
            this.queriesExecuted = queriesExecuted;
        }

        public int getQueriesExecutionFailed() {
            return queriesExecutionFailed;
        }

        public void setQueriesExecutionFailed(int queriesExecutionFailed) {
            this.queriesExecutionFailed = queriesExecutionFailed;
        }
    }

    // Getters and Setters for WizScannerResult
    public String getScannedResource() {
        return scannedResource;
    }

    public void setScannedResource(String scannedResource) {
        this.scannedResource = scannedResource;
    }

    public String getScanTime() {
        return scanTime;
    }

    public void setScanTime(String scanTime) {
        this.scanTime = scanTime;
    }

    public ScanStatus getStatus() {
        return status;
    }

    public void setStatus(ScanStatus status) {
        this.status = status;
    }

    public int getSecretTotalCount() {
        return secretTotalCount;
    }

    public void setSecretTotalCount(int secretTotalCount) {
        this.secretTotalCount = secretTotalCount;
    }

    public Vulnerabilities getVulnerabilities() {
        return vulnerabilities;
    }

    public void setVulnerabilities(Vulnerabilities vulnerabilities) {
        this.vulnerabilities = vulnerabilities;
    }

    public ScanStatistics getScanStatistics() {
        return scanStatistics;
    }

    public void setScanStatistics(ScanStatistics scanStatistics) {
        this.scanStatistics = scanStatistics;
    }

    public String getReportUrl() {
        return reportUrl;
    }

    public void setReportUrl(String reportUrl) {
        this.reportUrl = reportUrl;
    }

    public static WizScannerResult fromJsonFile(File jsonFile) {
        try {
            // Read the file content with UTF-8 encoding explicitly
            String content = new String(Files.readAllBytes(Paths.get(jsonFile.toURI())), StandardCharsets.UTF_8);
            JSONObject root = (JSONObject) JSONSerializer.toJSON(content);
            WizScannerResult details = new WizScannerResult();

            details.setScannedResource(getJsonString(root, "scanOriginResource.name"));
            details.setScanTime(getJsonDateTime(root, "createdAt"));
            details.setStatus(getJsonStatus(root, "status.verdict"));
            details.setSecretTotalCount(getJsonInt(root, "result.analytics.secrets.totalCount"));
            details.setVulnerabilities(getJsonVulnerabilities(root));
            details.setScanStatistics(getJsonScanStatistics(root));
            details.setReportUrl(getJsonString(root, "reportUrl"));
            return details;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String getJsonString(JSONObject root, String path) {
        if (root == null) {
            return "";
        }
        String[] keys = path.split("\\.");
        for (int i = 0; i < keys.length - 1; i++) {
            if (!root.has(keys[i])) {
                return "";
            }
            root = root.getJSONObject(keys[i]);
        }
        return root.optString(keys[keys.length - 1], null);
    }

    private static String getJsonDateTime(JSONObject root, String path) {
        if (root == null) {
            return "";
        }
        String dateTimeString = getJsonString(root, path);
        if (dateTimeString != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a", Locale.ENGLISH);
            return LocalDateTime.parse(dateTimeString, DateTimeFormatter.ISO_DATE_TIME)
                    .format(formatter);
        }
        return "";
    }

    private static ScanStatus getJsonStatus(JSONObject root, String path) {
        if (root == null) {
            return null;
        }
        String statusString = getJsonString(root, path);
        if (!statusString.isEmpty()) {
            switch (statusString) {
                case "PASSED_BY_POLICY":
                    return ScanStatus.Passed;
                case "FAILED_BY_POLICY":
                    return ScanStatus.Failed;
                case "WARN_BY_POLICY":
                    return ScanStatus.Warned;
            }
        }
        return null;
    }

    private static int getJsonInt(JSONObject root, String path) {
        if (root == null) {
            return 0;
        }
        String[] keys = path.split("\\.");
        for (int i = 0; i < keys.length - 1; i++) {
            if (!root.has(keys[i])) {
                return 0;
            }
            root = root.getJSONObject(keys[i]);
        }
        return root.optInt(keys[keys.length - 1], 0);
    }

    private static Vulnerabilities getJsonVulnerabilities(JSONObject root) {
        Vulnerabilities vulnerabilities = new Vulnerabilities();

        if (root != null
                && root.has("result")
                && root.getJSONObject("result").has("analytics")
                && root.getJSONObject("result").getJSONObject("analytics").has("vulnerabilities")) {
            root = root.getJSONObject("result").getJSONObject("analytics").getJSONObject("vulnerabilities");
            vulnerabilities.setInfoCount(root.optInt("infoCount", 0));
            vulnerabilities.setLowCount(root.optInt("lowCount", 0));
            vulnerabilities.setMediumCount(root.optInt("mediumCount", 0));
            vulnerabilities.setHighCount(root.optInt("highCount", 0));
            vulnerabilities.setCriticalCount(root.optInt("criticalCount", 0));
            vulnerabilities.setUnfixedCount(root.optInt("unfixedCount", 0));
            vulnerabilities.setTotalCount(root.optInt("totalCount", 0));
        }
        return vulnerabilities;
    }

    private static ScanStatistics getJsonScanStatistics(JSONObject root) {
        ScanStatistics stats = new ScanStatistics();

        if (root != null && root.has("result") && root.getJSONObject("result").has("scanStatistics")) {
            root = root.getJSONObject("result").getJSONObject("scanStatistics");
            stats.setInfoMatches(root.optInt("infoMatches", 0));
            stats.setLowMatches(root.optInt("lowMatches", 0));
            stats.setMediumMatches(root.optInt("mediumMatches", 0));
            stats.setHighMatches(root.optInt("highMatches", 0));
            stats.setCriticalMatches(root.optInt("criticalMatches", 0));
            stats.setTotalMatches(root.optInt("totalMatches", 0));
            stats.setFilesFound(root.optInt("filesFound", 0));
            stats.setFilesParsed(root.optInt("filesParsed", 0));
            stats.setQueriesLoaded(root.optInt("queriesLoaded", 0));
            stats.setQueriesExecuted(root.optInt("queriesExecuted", 0));
            stats.setQueriesExecutionFailed(root.optInt("queriesExecutionFailed", 0));
        }
        return stats;
    }
}
