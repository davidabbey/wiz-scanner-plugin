package io.jenkins.plugins.wiz;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.apache.commons.lang.StringUtils;

/**
 * Represents the results of a Wiz security scan.
 * This class handles parsing and storing scan results from JSON format.
 */
public class WizScannerResult {
    private static final Logger LOGGER = Logger.getLogger(WizScannerResult.class.getName());
    private static final DateTimeFormatter OUTPUT_FORMATTER =
            DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    private String scannedResource;
    private String scanTime;
    private ScanStatus status;
    private int secretTotalCount;
    private Vulnerabilities vulnerabilities;
    private ScanStatistics scanStatistics;
    private String reportUrl;

    public enum ScanStatus {
        PASSED("PASSED_BY_POLICY", "Passed"),
        FAILED("FAILED_BY_POLICY", "Failed"),
        IN_PROGRESS("IN_PROGRESS", "InProgress"),
        WARNED("WARN_BY_POLICY", "Warned"),
        UNKNOWN("UNKNOWN", "Unknown");

        private final String apiValue;
        private final String displayValue;

        ScanStatus(String apiValue, String displayValue) {
            this.apiValue = apiValue;
            this.displayValue = displayValue;
        }

        @Override
        public String toString() {
            return displayValue;
        }

        public static ScanStatus fromString(String value) {
            for (ScanStatus status : values()) {
                if (status.apiValue.equals(value)) {
                    return status;
                }
            }
            return UNKNOWN;
        }

        public boolean matches(String displayStatus) {
            return this.displayValue.equals(displayStatus);
        }
    }

    public static class Vulnerabilities {
        private int infoCount;
        private int lowCount;
        private int mediumCount;
        private int highCount;
        private int criticalCount;
        private int unfixedCount;
        private int totalCount;

        // Enhanced getters with validation
        public int getInfoCount() {
            return Math.max(0, infoCount);
        }

        public int getLowCount() {
            return Math.max(0, lowCount);
        }

        public int getMediumCount() {
            return Math.max(0, mediumCount);
        }

        public int getHighCount() {
            return Math.max(0, highCount);
        }

        public int getCriticalCount() {
            return Math.max(0, criticalCount);
        }

        public int getUnfixedCount() {
            return Math.max(0, unfixedCount);
        }

        public int getTotalCount() {
            return Math.max(0, totalCount);
        }

        // Setters with validation
        public void setInfoCount(int count) {
            this.infoCount = Math.max(0, count);
        }

        public void setLowCount(int count) {
            this.lowCount = Math.max(0, count);
        }

        public void setMediumCount(int count) {
            this.mediumCount = Math.max(0, count);
        }

        public void setHighCount(int count) {
            this.highCount = Math.max(0, count);
        }

        public void setCriticalCount(int count) {
            this.criticalCount = Math.max(0, count);
        }

        public void setUnfixedCount(int count) {
            this.unfixedCount = Math.max(0, count);
        }

        public void setTotalCount(int count) {
            this.totalCount = Math.max(0, count);
        }

        // Add validation method
        public boolean isValid() {
            return totalCount >= (infoCount + lowCount + mediumCount + highCount + criticalCount);
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

        // Enhanced getters with validation
        public int getInfoMatches() {
            return Math.max(0, infoMatches);
        }

        public int getLowMatches() {
            return Math.max(0, lowMatches);
        }

        public int getMediumMatches() {
            return Math.max(0, mediumMatches);
        }

        public int getHighMatches() {
            return Math.max(0, highMatches);
        }

        public int getCriticalMatches() {
            return Math.max(0, criticalMatches);
        }

        public int getTotalMatches() {
            return Math.max(0, totalMatches);
        }

        public int getFilesFound() {
            return Math.max(0, filesFound);
        }

        public int getFilesParsed() {
            return Math.max(0, filesParsed);
        }

        public int getQueriesLoaded() {
            return Math.max(0, queriesLoaded);
        }

        public int getQueriesExecuted() {
            return Math.max(0, queriesExecuted);
        }

        public int getQueriesExecutionFailed() {
            return Math.max(0, queriesExecutionFailed);
        }

        // Setters with validation
        public void setInfoMatches(int matches) {
            this.infoMatches = Math.max(0, matches);
        }

        public void setLowMatches(int matches) {
            this.lowMatches = Math.max(0, matches);
        }

        public void setMediumMatches(int matches) {
            this.mediumMatches = Math.max(0, matches);
        }

        public void setHighMatches(int matches) {
            this.highMatches = Math.max(0, matches);
        }

        public void setCriticalMatches(int matches) {
            this.criticalMatches = Math.max(0, matches);
        }

        public void setTotalMatches(int matches) {
            this.totalMatches = Math.max(0, matches);
        }

        public void setFilesFound(int count) {
            this.filesFound = Math.max(0, count);
        }

        public void setFilesParsed(int count) {
            this.filesParsed = Math.max(0, count);
        }

        public void setQueriesLoaded(int count) {
            this.queriesLoaded = Math.max(0, count);
        }

        public void setQueriesExecuted(int count) {
            this.queriesExecuted = Math.max(0, count);
        }

        public void setQueriesExecutionFailed(int count) {
            this.queriesExecutionFailed = Math.max(0, count);
        }

        // Add validation method
        public boolean isValid() {
            return totalMatches >= 0 && filesParsed <= filesFound && queriesExecuted <= queriesLoaded;
        }
    }

    // Enhanced getters and setters with validation
    public String getScannedResource() {
        return StringUtils.defaultString(scannedResource);
    }

    public void setScannedResource(String resource) {
        this.scannedResource = StringUtils.trimToNull(resource);
    }

    public String getScanTime() {
        return StringUtils.defaultString(scanTime);
    }

    public void setScanTime(String time) {
        this.scanTime = StringUtils.trimToNull(time);
    }

    public ScanStatus getStatus() {
        return Objects.requireNonNullElse(status, ScanStatus.UNKNOWN);
    }

    public void setStatus(ScanStatus status) {
        this.status = status;
    }

    public int getSecretTotalCount() {
        return Math.max(0, secretTotalCount);
    }

    public void setSecretTotalCount(int count) {
        this.secretTotalCount = Math.max(0, count);
    }

    public Optional<Vulnerabilities> getVulnerabilities() {
        return Optional.ofNullable(vulnerabilities);
    }

    public void setVulnerabilities(Vulnerabilities vulns) {
        this.vulnerabilities = vulns;
    }

    public Optional<ScanStatistics> getScanStatistics() {
        return Optional.ofNullable(scanStatistics);
    }

    public void setScanStatistics(ScanStatistics stats) {
        this.scanStatistics = stats;
    }

    public String getReportUrl() {
        return StringUtils.defaultString(reportUrl);
    }

    public void setReportUrl(String url) {
        this.reportUrl = StringUtils.trimToNull(url);
    }

    /**
     * Creates a WizScannerResult from a JSON file
     * @param jsonFile The JSON file to parse
     * @return The parsed WizScannerResult or null if parsing fails
     */
    public static WizScannerResult fromJsonFile(File jsonFile) {
        try {
            if (jsonFile == null || !jsonFile.exists()) {
                throw new IOException("JSON file does not exist");
            }

            String content = new String(Files.readAllBytes(jsonFile.toPath()), StandardCharsets.UTF_8);
            if (StringUtils.isBlank(content)) {
                throw new IOException("JSON file is empty");
            }

            JSONObject root = (JSONObject) JSONSerializer.toJSON(content);
            return parseJsonContent(root);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to parse scan results", e);
            return null;
        }
    }

    private static WizScannerResult parseJsonContent(JSONObject root) {
        WizScannerResult details = new WizScannerResult();

        try {
            details.setScannedResource(getJsonString(root, "scanOriginResource.name"));
            details.setScanTime(formatDateTime(getJsonString(root, "createdAt")));
            details.setStatus(parseStatus(getJsonString(root, "status.verdict")));
            details.setSecretTotalCount(getJsonInt(root, "result.analytics.secrets.totalCount"));
            details.setVulnerabilities(parseVulnerabilities(root));
            details.setScanStatistics(parseScanStatistics(root));
            details.setReportUrl(getJsonString(root, "reportUrl"));

            validateResult(details);
            return details;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error parsing JSON content", e);
            return null;
        }
    }

    private static void validateResult(WizScannerResult details) {
        if (details.getVulnerabilities().isPresent()
                && !details.getVulnerabilities().get().isValid()) {
            LOGGER.warning("Vulnerabilities data contains inconsistencies");
        }
        if (details.getScanStatistics().isPresent()
                && !details.getScanStatistics().get().isValid()) {
            LOGGER.warning("Scan statistics contain inconsistencies");
        }
    }

    private static String getJsonString(JSONObject root, String path) {
        if (root == null || StringUtils.isBlank(path)) {
            return "";
        }
        try {
            JSONObject current = root;
            String[] keys = path.split("\\.");

            for (int i = 0; i < keys.length - 1; i++) {
                if (!current.has(keys[i])) {
                    return "";
                }
                current = current.getJSONObject(keys[i]);
                if (current == null) {
                    return "";
                }
            }

            return current.optString(keys[keys.length - 1], "");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting JSON string for path: " + path, e);
            return "";
        }
    }

    private static String formatDateTime(String dateTimeString) {
        if (StringUtils.isBlank(dateTimeString)) {
            return "";
        }
        try {
            LocalDateTime dateTime = LocalDateTime.parse(dateTimeString, INPUT_FORMATTER);
            return dateTime.format(OUTPUT_FORMATTER);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error formatting datetime: " + dateTimeString, e);
            return dateTimeString;
        }
    }

    private static ScanStatus parseStatus(String statusString) {
        if (StringUtils.isBlank(statusString)) {
            return ScanStatus.UNKNOWN;
        }
        return ScanStatus.fromString(statusString);
    }

    private static int getJsonInt(JSONObject root, String path) {
        if (root == null || StringUtils.isBlank(path)) {
            return 0;
        }
        try {
            JSONObject current = root;
            String[] keys = path.split("\\.");

            for (int i = 0; i < keys.length - 1; i++) {
                if (!current.has(keys[i])) {
                    return 0;
                }
                current = current.getJSONObject(keys[i]);
                if (current == null) {
                    return 0;
                }
            }

            return current.optInt(keys[keys.length - 1], 0);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting JSON integer for path: " + path, e);
            return 0;
        }
    }

    private static Vulnerabilities parseVulnerabilities(JSONObject root) {
        Vulnerabilities vulnerabilities = new Vulnerabilities();
        try {
            if (root != null && root.has("result")) {
                JSONObject result = root.getJSONObject("result");
                if (result.has("analytics")) {
                    JSONObject analytics = result.getJSONObject("analytics");
                    if (analytics.has("vulnerabilities")) {
                        JSONObject vulns = analytics.getJSONObject("vulnerabilities");
                        vulnerabilities.setInfoCount(vulns.optInt("infoCount", 0));
                        vulnerabilities.setLowCount(vulns.optInt("lowCount", 0));
                        vulnerabilities.setMediumCount(vulns.optInt("mediumCount", 0));
                        vulnerabilities.setHighCount(vulns.optInt("highCount", 0));
                        vulnerabilities.setCriticalCount(vulns.optInt("criticalCount", 0));
                        vulnerabilities.setUnfixedCount(vulns.optInt("unfixedCount", 0));
                        vulnerabilities.setTotalCount(vulns.optInt("totalCount", 0));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error parsing vulnerabilities", e);
        }
        return vulnerabilities;
    }

    private static ScanStatistics parseScanStatistics(JSONObject root) {
        ScanStatistics stats = new ScanStatistics();
        try {
            if (root != null && root.has("result")) {
                JSONObject result = root.getJSONObject("result");
                if (result.has("scanStatistics")) {
                    JSONObject scanStats = result.getJSONObject("scanStatistics");
                    stats.setInfoMatches(scanStats.optInt("infoMatches", 0));
                    stats.setLowMatches(scanStats.optInt("lowMatches", 0));
                    stats.setMediumMatches(scanStats.optInt("mediumMatches", 0));
                    stats.setHighMatches(scanStats.optInt("highMatches", 0));
                    stats.setCriticalMatches(scanStats.optInt("criticalMatches", 0));
                    stats.setTotalMatches(scanStats.optInt("totalMatches", 0));
                    stats.setFilesFound(scanStats.optInt("filesFound", 0));
                    stats.setFilesParsed(scanStats.optInt("filesParsed", 0));
                    stats.setQueriesLoaded(scanStats.optInt("queriesLoaded", 0));
                    stats.setQueriesExecuted(scanStats.optInt("queriesExecuted", 0));
                    stats.setQueriesExecutionFailed(scanStats.optInt("queriesExecutionFailed", 0));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error parsing scan statistics", e);
        }
        return stats;
    }

    @Override
    public String toString() {
        return String.format(
                "WizScannerResult{resource='%s', status=%s, vulnerabilities=%d, secrets=%d}",
                getScannedResource(),
                getStatus(),
                getVulnerabilities().map(Vulnerabilities::getTotalCount).orElse(0),
                getSecretTotalCount());
    }

    public int getVulnerabilitiesCriticalCount() {
        return getVulnerabilities().map(Vulnerabilities::getCriticalCount).orElse(0);
    }

    public int getVulnerabilitiesHighCount() {
        return getVulnerabilities().map(Vulnerabilities::getHighCount).orElse(0);
    }

    public int getVulnerabilitiesMediumCount() {
        return getVulnerabilities().map(Vulnerabilities::getMediumCount).orElse(0);
    }

    public int getVulnerabilitiesLowCount() {
        return getVulnerabilities().map(Vulnerabilities::getLowCount).orElse(0);
    }

    public int getScanStatisticsCriticalMatches() {
        return getScanStatistics().map(ScanStatistics::getCriticalMatches).orElse(0);
    }

    public int getScanStatisticsHighMatches() {
        return getScanStatistics().map(ScanStatistics::getHighMatches).orElse(0);
    }

    public int getScanStatisticsMediumMatches() {
        return getScanStatistics().map(ScanStatistics::getMediumMatches).orElse(0);
    }

    public int getScanStatisticsLowMatches() {
        return getScanStatistics().map(ScanStatistics::getLowMatches).orElse(0);
    }
}


