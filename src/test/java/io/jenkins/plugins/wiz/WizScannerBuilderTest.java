package io.jenkins.plugins.wiz;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.Secret;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class WizScannerBuilderTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private TaskListener listener;
    private Launcher mockLauncher;
    private WizScannerBuilder builder;
    private FilePath workspace;
    private EnvVars env;
    private ByteArrayOutputStream logOutput;
    private static final String TEST_COMMAND = "docker scan alpine:latest";

    @Before
    public void setUp() throws Exception {
        workspace = j.jenkins.getRootPath();
        builder = new WizScannerBuilder(TEST_COMMAND);
        logOutput = new ByteArrayOutputStream();
        listener = new StreamTaskListener(logOutput, Charset.defaultCharset());

        env = new EnvVars();
        env.put("PATH", "/usr/local/bin:/usr/bin:/bin");
        env.put("WIZ_ENV", "test");

        mockLauncher = mock(Launcher.class);
        Launcher.ProcStarter procStarter = mock(Launcher.ProcStarter.class);
        when(mockLauncher.launch()).thenReturn(procStarter);
        when(procStarter.cmds(any(ArgumentListBuilder.class))).thenReturn(procStarter);
        when(procStarter.envs(anyMap())).thenReturn(procStarter);
        when(procStarter.pwd(any(FilePath.class))).thenReturn(procStarter);
        when(procStarter.stdout(any(OutputStream.class))).thenReturn(procStarter);
        when(procStarter.stderr(any(OutputStream.class))).thenReturn(procStarter);
        when(procStarter.quiet(anyBoolean())).thenReturn(procStarter);
        when(procStarter.join()).thenReturn(0);
    }

    @Test
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(builder);

        project = j.configRoundtrip(project);

        // Get the builder from the configured project
        WizScannerBuilder after = project.getBuildersList().get(WizScannerBuilder.class);

        // Verify configuration is preserved
        j.assertEqualDataBoundBeans(builder, after);
    }

    @Test
    public void testPerformSuccessful() throws Exception {
        WizScannerBuilder.DescriptorImpl descriptor =
                j.jenkins.getDescriptorByType(WizScannerBuilder.DescriptorImpl.class);
        FreeStyleProject project = j.createFreeStyleProject();
        Run<?, ?> run = project.scheduleBuild2(0).get();

        descriptor.configure(
                null,
                net.sf.json.JSONObject.fromObject("{" + "'wizClientId': 'test-client',"
                        + "'wizSecretKey': '"
                        + Secret.fromString("test-secret").getEncryptedValue() + "',"
                        + "'wizCliURL': 'https://downloads.wiz.io/wizcli/latest/wizcli-darwin-arm64',"
                        + "'wizEnv': 'test'"
                        + "}"));

        FilePath resultFile = workspace.child("wizscan.json");
        resultFile.write("{}", "UTF-8");

        try {
            builder.perform(run, workspace, env, mockLauncher, listener);

            assertEquals("test", env.get("WIZ_ENV"));
            assertFalse("Log should contain output", logOutput.toString().isEmpty());
        } finally {
            // Close file handles explicitly
            if (logOutput != null) {
                logOutput.close();
            }

            // Wait a bit for Windows to release file handles
            Thread.sleep(100);

            // Clean up files
            cleanupTestFiles(run, workspace);
        }
    }

    @Test
    public void testPerformFailureInvalidConfig() throws Exception {
        // Setup with invalid config
        WizScannerBuilder.DescriptorImpl descriptor =
                j.jenkins.getDescriptorByType(WizScannerBuilder.DescriptorImpl.class);
        FreeStyleProject project = j.createFreeStyleProject();
        Run<?, ?> run = project.scheduleBuild2(0).get();

        descriptor.configure(
                null,
                net.sf.json.JSONObject.fromObject("{" + "'wizClientId': '',"
                        + "'wizSecretKey': '',"
                        + "'wizCliURL': '',"
                        + "'wizEnv': ''"
                        + "}"));

        try {
            builder.perform(run, workspace, env, mockLauncher, listener);
            fail("Expected AbortException to be thrown");
        } catch (AbortException e) {
            assertEquals("Wiz Client ID is required", e.getMessage());
        }
    }

    @Test
    public void testFormValidation() {
        WizScannerBuilder.DescriptorImpl descriptor = new WizScannerBuilder.DescriptorImpl();

        // Test empty input
        assertEquals(
                "Error message for empty input",
                Messages.WizScannerBuilder_DescriptorImpl_errors_missingName(),
                descriptor.doCheckUserInput("").getMessage());

        // Test valid input
        assertEquals("OK for valid input", FormValidation.Kind.OK, descriptor.doCheckUserInput(TEST_COMMAND).kind);
    }

    @Test
    public void testDescriptorBasics() {
        WizScannerBuilder.DescriptorImpl descriptor = new WizScannerBuilder.DescriptorImpl();

        // Test display name
        assertNotNull("Display name should not be null", descriptor.getDisplayName());

        // Test applicability
        assertTrue("Should be applicable to FreeStyleProject", descriptor.isApplicable(FreeStyleProject.class));
    }

    private void cleanupTestFiles(Run<?, ?> run, FilePath workspace) throws IOException, InterruptedException {
        // List of files to clean up
        List<FilePath> filesToClean = Arrays.asList(
                new FilePath(new File(run.getRootDir(), "wizcli_output")),
                new FilePath(new File(run.getRootDir(), "wizcli_err_output")),
                workspace.child("wizscan.json"),
                workspace.child("wizcli"),
                workspace.child("wizcli.exe"));

        // Try multiple times to delete files (Windows sometimes needs retries)
        for (FilePath file : filesToClean) {
            int attempts = 3;
            while (attempts > 0) {
                try {
                    if (file.exists()) {
                        file.deleteRecursive();
                    }
                    break;
                } catch (IOException e) {
                    attempts--;
                    if (attempts == 0) {
                        // Log warning but don't fail test
                        e.printStackTrace();
                    }
                    Thread.sleep(100);
                }
            }
        }
    }
}
