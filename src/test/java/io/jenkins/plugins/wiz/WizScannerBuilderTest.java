package io.jenkins.plugins.wiz;

import static org.junit.Assert.*;

import hudson.model.FreeStyleProject;
import hudson.util.Secret;
import java.io.IOException;
import javax.servlet.ServletException;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;

public class WizScannerBuilderTest {
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        WizScannerBuilder builder = new WizScannerBuilder("docker scan alpine:latest");
        project.getBuildersList().add(builder);

        // Get the updated project from Jenkins
        project = jenkins.configRoundtrip(project);

        // Verify builder was saved
        WizScannerBuilder lhs = new WizScannerBuilder("docker scan alpine:latest");
        jenkins.assertEqualDataBoundBeans(lhs, project.getBuildersList().get(WizScannerBuilder.class));
    }

    @Test
    @WithoutJenkins
    public void testDescriptorValidation() throws ServletException, IOException {
        WizScannerBuilder.DescriptorImpl descriptor = new WizScannerBuilder.DescriptorImpl();

        // Test empty input validation
        assertEquals("Please set the command", descriptor.doCheckUserInput("").getMessage());

        // Test valid input validation
        assertTrue(descriptor.doCheckUserInput("docker scan alpine:latest").kind == hudson.util.FormValidation.Kind.OK);
    }

    @Test
    public void testGlobalConfig() throws Exception {
        WizScannerBuilder.DescriptorImpl descriptor = jenkins.get(WizScannerBuilder.DescriptorImpl.class);

        // Set global config values
        String clientId = "test-client-id";
        Secret secretKey = Secret.fromString("test-secret-key");
        String cliUrl = "https://downloads.wiz.io/wizcli/latest/wizcli-linux-amd64";
        String env = "test";

        descriptor.configure(
                null,
                new net.sf.json.JSONObject()
                        .element("wizClientId", clientId)
                        .element("wizSecretKey", secretKey.getEncryptedValue())
                        .element("wizCliURL", cliUrl)
                        .element("wizEnv", env));

        // Verify values were saved
        assertEquals(clientId, descriptor.getWizClientId());
        assertEquals(secretKey.getEncryptedValue(), descriptor.getWizSecretKey().getEncryptedValue());
        assertEquals(cliUrl, descriptor.getWizCliURL());
        assertEquals(env, descriptor.getWizEnv());
    }
}
