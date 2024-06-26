package io.jenkins.plugins.wiz;

import hudson.FilePath;
import hudson.model.Run;
import java.io.File;
import jenkins.model.RunAction2;

public class WizScannerAction implements RunAction2 {
    private transient Run run;
    private WizScannerResult scanDetails;
    private String name;
    private String resultsUrl;
    private Run<?, ?> build;
    private String artifactSuffix;

    public WizScannerAction(Run<?, ?> build, FilePath workspace, String artifactSuffix, String artifactName) {
        this.name = artifactSuffix;
        this.build = build;
        this.artifactSuffix = artifactSuffix;
        this.resultsUrl = "../artifact/" + artifactName;
        loadScanDetails(new File(workspace.getRemote(), artifactName));
    }

    private void loadScanDetails(File jsonFile) {
        this.scanDetails = WizScannerResult.fromJsonFile(jsonFile);
    }

    public Run getRun() {
        return run;
    }

    @Override
    public void onAttached(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public void onLoad(Run<?, ?> run) {
        this.run = run;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getIconFileName() {
        return "plugin/wiz-scanner/images/wiz.png";
    }

    @Override
    public String getDisplayName() {
        if (artifactSuffix == null) {
            return "Wiz Scanner";
        } else {
            return "Wiz Scanner " + artifactSuffix;
        }
    }

    @Override
    public String getUrlName() {
        if (artifactSuffix == null) {
            return "wiz-results";
        } else {
            return "wiz-results-" + artifactSuffix;
        }
    }

    public Run<?, ?> getBuild() {
        return this.build;
    }

    public String getResultsUrl() {
        return this.resultsUrl;
    }

    public WizScannerResult getScanDetails() {
        return scanDetails;
    }
}
