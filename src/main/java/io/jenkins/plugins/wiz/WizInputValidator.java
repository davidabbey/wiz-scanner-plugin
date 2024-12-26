package io.jenkins.plugins.wiz;

import hudson.AbortException;
import hudson.FilePath;
import hudson.model.Run;
import hudson.util.Secret;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;

/**
 * Validator for Wiz CLI inputs and commands.
 */
public class WizInputValidator {
    private static final String WIZ_DOWNLOADS_BASE = "https://downloads.wiz.io/wizcli/";
    private static final Pattern URL_PATTERN = Pattern.compile("https://downloads\\.wiz\\.io/wizcli/([^/]+)/([^/]+)");

    private static final Set<String> ALLOWED_ROOT_COMMANDS =
            new HashSet<>(Arrays.asList("auth", "dir", "docker", "iac"));

    private static final Map<String, Set<String>> ALLOWED_SUBCOMMANDS = new HashMap<>();

    static {
        ALLOWED_SUBCOMMANDS.put("dir", new HashSet<>(List.of("scan")));
        ALLOWED_SUBCOMMANDS.put("docker", new HashSet<>(List.of("scan")));
        ALLOWED_SUBCOMMANDS.put("iac", new HashSet<>(List.of("scan")));
    }

    /**
     * Validates the global configuration parameters
     */
    public static void validateConfiguration(String wizClientId, Secret wizSecretKey, String wizCliURL)
            throws AbortException {
        if (StringUtils.isBlank(wizClientId)) {
            throw new AbortException("Wiz Client ID is required");
        }
        if (wizSecretKey == null || StringUtils.isBlank(Secret.toString(wizSecretKey))) {
            throw new AbortException("Wiz Secret Key is required");
        }
        if (StringUtils.isBlank(wizCliURL)) {
            throw new AbortException("Wiz CLI URL is required");
        }
    }

    /**
     * Validates scan action parameters
     */
    public static void validateScanAction(Run<?, ?> build, FilePath workspace, String artifactName)
            throws IllegalArgumentException {
        if (build == null) throw new IllegalArgumentException("Build cannot be null");
        if (workspace == null) throw new IllegalArgumentException("Workspace cannot be null");
        if (artifactName == null) throw new IllegalArgumentException("Artifact name cannot be null");
    }

    /**
     * Validates the Wiz CLI download URL format.
     */
    public static void validateWizCliUrl(String url) throws AbortException {
        if (!URL_PATTERN.matcher(url).matches()) {
            throw new AbortException(
                    "Invalid Wiz CLI URL format. Expected: " + WIZ_DOWNLOADS_BASE + "{version}/{binary_name}");
        }
    }

    /**
     * Validates the command structure and arguments.
     */
    public static void validateCommand(String userInput) throws IllegalArgumentException {
        if (StringUtils.isBlank(userInput)) {
            throw new IllegalArgumentException("No command provided");
        }

        List<String> arguments = parseArgumentsRespectingQuotes(userInput);
        if (arguments.isEmpty()) {
            throw new IllegalArgumentException("No valid arguments provided");
        }

        String rootCommand = arguments.get(0);
        if (!ALLOWED_ROOT_COMMANDS.contains(rootCommand)) {
            throw new IllegalArgumentException(
                    "Invalid command. Allowed commands are: " + String.join(", ", ALLOWED_ROOT_COMMANDS));
        }

        if (ALLOWED_SUBCOMMANDS.containsKey(rootCommand) && arguments.size() > 1) {
            String subcommand = arguments.get(1);
            Set<String> allowedSubcommands = ALLOWED_SUBCOMMANDS.get(rootCommand);

            if (!allowedSubcommands.contains(subcommand)) {
                throw new IllegalArgumentException("Invalid subcommand for " + rootCommand
                        + ". Allowed subcommands are: " + String.join(", ", allowedSubcommands));
            }
        }

        // Validate each argument
        for (String arg : arguments) {
            validateArgument(arg);
        }
    }

    private static List<String> parseArgumentsRespectingQuotes(String input) {
        List<String> arguments = new ArrayList<>();
        Matcher matcher = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'").matcher(input);

        while (matcher.find()) {
            String arg = matcher.group();
            if (arg.startsWith("\"") && arg.endsWith("\"") || arg.startsWith("'") && arg.endsWith("'")) {
                arg = arg.substring(1, arg.length() - 1);
            }
            arguments.add(arg);
        }

        return arguments;
    }

    private static void validateArgument(String arg) {
        if (StringUtils.isBlank(arg)) {
            throw new IllegalArgumentException("Empty argument provided");
        }

        // Check for potential command injection characters
        if (arg.contains(";")
                || arg.contains("|")
                || arg.contains("&")
                || arg.contains(">")
                || arg.contains("<")
                || arg.contains("`")) {
            throw new IllegalArgumentException("Invalid characters in argument: " + arg);
        }
    }
}
