package io.jenkins.plugins.wiz;

import hudson.model.FreeStyleProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class WizScannerBuilderTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    final String userInput = "docker scan --image ubuntu-image:0.1";

    @Test
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.getBuildersList().add(new WizScannerBuilder(userInput));
        project = jenkins.configRoundtrip(project);
        jenkins.assertEqualDataBoundBeans(
                new WizScannerBuilder(userInput), project.getBuildersList().get(0));
    }
}
