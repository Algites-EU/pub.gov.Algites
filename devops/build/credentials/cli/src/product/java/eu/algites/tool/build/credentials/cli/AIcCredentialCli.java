package eu.algites.tool.build.credentials.cli;

import eu.algites.tool.build.credentials.coreimpl.AIcCredentialResolver;
import eu.algites.tool.build.credentials.coreimpl.AIcCredentialService;
import eu.algites.tool.build.credentials.coreimpl.AIcEnvironmentCredentialProvider;
import eu.algites.tool.build.credentials.coreintf.AIcCredential;
import eu.algites.tool.build.credentials.coreintf.AIcCredentialProfile;
import eu.algites.tool.build.credentials.coreintf.AInCredentialField;
import eu.algites.tool.build.credentials.coreintf.AInCredentialType;
import eu.algites.tool.build.credentials.coreintf.AIxCredentialException;

import java.io.Console;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Optional;

/**
 * Cross-platform interactive CLI for provisioning and inspecting Algites credential profiles.
 */
public final class AIcCredentialCli {
    private AIcCredentialCli() {
    }

    public static void main(String[] aArguments) {
        int locExitCode;
        try {
            locExitCode = run(aArguments, System.console());
        } catch (AIxCredentialException | IllegalArgumentException aException) {
            System.err.println("ERROR: " + aException.getMessage());
            locExitCode = 2;
        }
        if (locExitCode != 0) {
            System.exit(locExitCode);
        }
    }

    static int run(String[] aArguments, Console aConsole) {
        if (aArguments.length < 1) {
            printUsage();
            return 1;
        }

        String locCommand = aArguments[0].toLowerCase(java.util.Locale.ROOT);
        if ("store-id".equals(locCommand)) {
            System.out.println(new AIcCredentialService().getStoreId());
            return 0;
        }
        if ("store-diagnostics".equals(locCommand)) {
            System.out.println(new AIcCredentialService().getStoreDiagnostics());
            return 0;
        }
        if (aArguments.length != 3) {
            printUsage();
            return 1;
        }

        AIcCredentialProfile locProfile = new AIcCredentialProfile(aArguments[1], AInCredentialType.fromId(aArguments[2]));
        return switch (locCommand) {
            case "set" -> set(locProfile, aConsole);
            case "status" -> status(locProfile);
            case "remove" -> remove(locProfile);
            case "env" -> environment(locProfile);
            default -> {
                printUsage();
                yield 1;
            }
        };
    }

    private static int set(AIcCredentialProfile aProfile, Console aConsole) {
        if (aConsole == null) {
            throw new AIxCredentialException("Interactive credential provisioning requires a terminal console.");
        }

        EnumMap<AInCredentialField, char[]> locValues = new EnumMap<>(AInCredentialField.class);
        try {
            switch (aProfile.getType()) {
                case BASIC -> {
                    String locUsername = aConsole.readLine("Username: ");
                    char[] locPassword = aConsole.readPassword("Password: ");
                    requireNonEmpty(locUsername, "Username");
                    requireNonEmpty(locPassword, "Password");
                    locValues.put(AInCredentialField.USERNAME, locUsername.toCharArray());
                    locValues.put(AInCredentialField.PASSWORD, locPassword);
                }
                case BEARER -> {
                    char[] locToken = aConsole.readPassword("Bearer token: ");
                    requireNonEmpty(locToken, "Bearer token");
                    locValues.put(AInCredentialField.TOKEN, locToken);
                }
                case API_KEY -> {
                    char[] locApiKey = aConsole.readPassword("API key: ");
                    requireNonEmpty(locApiKey, "API key");
                    locValues.put(AInCredentialField.API_KEY, locApiKey);
                }
                case CLIENT_CERTIFICATE -> {
                    String locCertificateFile = aConsole.readLine("Certificate file: ");
                    String locPrivateKeyFile = aConsole.readLine("Private key file: ");
                    requireNonEmpty(locCertificateFile, "Certificate file");
                    requireNonEmpty(locPrivateKeyFile, "Private key file");
                    locValues.put(AInCredentialField.CERTIFICATE, readTextSecret(Path.of(locCertificateFile)));
                    locValues.put(AInCredentialField.PRIVATE_KEY, readTextSecret(Path.of(locPrivateKeyFile)));
                    char[] locPrivateKeyPassword = aConsole.readPassword("Private key password (optional): ");
                    if (locPrivateKeyPassword != null && locPrivateKeyPassword.length > 0) {
                        locValues.put(AInCredentialField.PRIVATE_KEY_PASSWORD, locPrivateKeyPassword);
                    }
                }
            }

            try (AIcCredential locCredential = new AIcCredential(locValues)) {
                AIcCredentialService locService = new AIcCredentialService();
                locService.store(aProfile, locCredential);
                System.out.println(
                    "Stored credential profile '" + aProfile.getId() + "' type '" + aProfile.getType().getId() +
                        "' in " + locService.getStoreId() + "."
                );
            }
            return 0;
        } finally {
            locValues.values().forEach(locValue -> Arrays.fill(locValue, '\0'));
        }
    }

    private static int status(AIcCredentialProfile aProfile) {
        Optional<AIcCredential> locCredential = AIcCredentialResolver.standard().resolve(aProfile);
        if (locCredential.isPresent()) {
            locCredential.get().close();
            System.out.println(
                "Credential profile '" + aProfile.getId() + "' type '" + aProfile.getType().getId() + "' is available."
            );
            return 0;
        }
        System.out.println(
            "Credential profile '" + aProfile.getId() + "' type '" + aProfile.getType().getId() + "' is not available."
        );
        printEnvironmentVariables(aProfile);
        System.out.println(new AIcCredentialService().getStoreDiagnostics());
        return 3;
    }

    private static int environment(AIcCredentialProfile aProfile) {
        printEnvironmentVariables(aProfile);
        return 0;
    }

    private static void printEnvironmentVariables(AIcCredentialProfile aProfile) {
        System.out.println("Required environment variables:");
        AIcEnvironmentCredentialProvider.getRequiredEnvironmentVariables(aProfile)
            .forEach(locVariable -> System.out.println("  " + locVariable));
        if (!aProfile.getType().getOptionalFields().isEmpty()) {
            System.out.println("Optional environment variables:");
            AIcEnvironmentCredentialProvider.getOptionalEnvironmentVariables(aProfile)
                .forEach(locVariable -> System.out.println("  " + locVariable));
        }
    }

    private static int remove(AIcCredentialProfile aProfile) {
        AIcCredentialService locService = new AIcCredentialService();
        locService.remove(aProfile);
        System.out.println(
            "Removed stored credential profile '" + aProfile.getId() + "' type '" + aProfile.getType().getId() + "'."
        );
        return 0;
    }

    private static char[] readTextSecret(Path aPath) {
        try {
            if (!Files.isRegularFile(aPath)) {
                throw new AIxCredentialException("Credential file does not exist: " + aPath);
            }
            return Files.readString(aPath, StandardCharsets.UTF_8).toCharArray();
        } catch (IOException aException) {
            throw new AIxCredentialException("Cannot read credential file: " + aPath, aException);
        }
    }

    private static void requireNonEmpty(String aValue, String aLabel) {
        if (aValue == null || aValue.isBlank()) {
            throw new AIxCredentialException(aLabel + " must not be empty.");
        }
    }

    private static void requireNonEmpty(char[] aValue, String aLabel) {
        if (aValue == null || aValue.length == 0) {
            throw new AIxCredentialException(aLabel + " must not be empty.");
        }
    }

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  algites-credentials set <profile> <basic|bearer|api-key|client-certificate>");
        System.err.println("  algites-credentials status <profile> <basic|bearer|api-key|client-certificate>");
        System.err.println("  algites-credentials remove <profile> <basic|bearer|api-key|client-certificate>");
        System.err.println("  algites-credentials env <profile> <basic|bearer|api-key|client-certificate>");
        System.err.println("  algites-credentials store-id");
        System.err.println("  algites-credentials store-diagnostics");
    }
}
