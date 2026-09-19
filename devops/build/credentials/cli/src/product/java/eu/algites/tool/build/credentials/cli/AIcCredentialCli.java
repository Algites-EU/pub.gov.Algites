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
            requireArgumentCount(aArguments, 1);
            System.out.println(new AIcCredentialService().getStoreId());
            return 0;
        }
        if ("store-diagnostics".equals(locCommand)) {
            requireArgumentCount(aArguments, 1);
            System.out.println(new AIcCredentialService().getStoreDiagnostics());
            return 0;
        }
        if ("document-set".equals(locCommand)) {
            if (aArguments.length < 1 || aArguments.length > 2) {
                printUsage();
                return 1;
            }
            return setCredentialDocument(aArguments.length == 2 ? aArguments[1] : null);
        }
        if ("document-status".equals(locCommand)) {
            requireArgumentCount(aArguments, 1);
            return statusCredentialDocument();
        }
        if ("document-remove".equals(locCommand)) {
            requireArgumentCount(aArguments, 1);
            return removeCredentialDocument();
        }
        if ("bootstrap-document".equals(locCommand)) {
            requireArgumentCount(aArguments, 1);
            return bootstrapCredentialDocument();
        }
        if ("bootstrap-secret".equals(locCommand)) {
            requireArgumentCount(aArguments, 2);
            return bootstrapNamedSecret(aArguments[1]);
        }
        if (locCommand.startsWith("secret-")) {
            if (aArguments.length != 2) {
                printUsage();
                return 1;
            }
            return switch (locCommand) {
                case "secret-set" -> setNamedSecret(aArguments[1], aConsole);
                case "secret-status" -> statusNamedSecret(aArguments[1]);
                case "secret-remove" -> removeNamedSecret(aArguments[1]);
                default -> {
                    printUsage();
                    yield 1;
                }
            };
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
                case CERTIFICATE -> {
                    String locCertificateFile = aConsole.readLine("Certificate file: ");
                    requireNonEmpty(locCertificateFile, "Certificate file");
                    locValues.put(AInCredentialField.CERTIFICATE, readTextSecret(Path.of(locCertificateFile)));

                    String locPrivateKeyFile = aConsole.readLine("Private key file (optional): ");
                    if (locPrivateKeyFile != null && !locPrivateKeyFile.isBlank()) {
                        locValues.put(AInCredentialField.PRIVATE_KEY, readTextSecret(Path.of(locPrivateKeyFile)));
                        char[] locPrivateKeyPassword = aConsole.readPassword("Private key password (optional): ");
                        if (locPrivateKeyPassword != null && locPrivateKeyPassword.length > 0) {
                            locValues.put(AInCredentialField.PRIVATE_KEY_PASSWORD, locPrivateKeyPassword);
                        }
                    }
                }
            }

            try (AIcCredential locCredential = new AIcCredential(locValues)) {
                AIcCredentialService locService = new AIcCredentialService();
                locService.store(aProfile, locCredential);
                System.out.println(
                    "Stored credential profile '" + aProfile.getId() + "' type '" + aProfile.getType().getId() +
                        " in the universal ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS document in " + locService.getStoreId() + "."
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
            "Removed credential profile '" + aProfile.getId() + "' type '" + aProfile.getType().getId() +
                " from the stored ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS document."
        );
        return 0;
    }

    private static int setCredentialDocument(String aFile) {
        char[] locDocument = readCredentialDocumentInput(aFile);
        try {
            AIcCredentialService locService = new AIcCredentialService();
            locService.storeCredentialDocument(locDocument);
            System.out.println("Stored universal ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS document in " + locService.getStoreId() + ".");
            return 0;
        } finally {
            Arrays.fill(locDocument, '\0');
        }
    }

    private static int statusCredentialDocument() {
        boolean locStored = new AIcCredentialService().isCredentialDocumentStored();
        System.out.println("Universal ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS document is " + (locStored ? "available." : "not available."));
        return locStored ? 0 : 3;
    }

    private static int removeCredentialDocument() {
        AIcCredentialService locService = new AIcCredentialService();
        locService.removeCredentialDocument();
        System.out.println("Removed stored universal ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS document.");
        return 0;
    }

    private static int bootstrapCredentialDocument() {
        Optional<char[]> locDocument = new AIcCredentialService().readCredentialDocument();
        if (locDocument.isEmpty()) {
            return 3;
        }
        char[] locValue = locDocument.get();
        try {
            System.out.print(locValue);
            return 0;
        } finally {
            Arrays.fill(locValue, '\0');
        }
    }

    private static int setNamedSecret(String aSecretId, Console aConsole) {
        if (aConsole == null) {
            throw new AIxCredentialException("Interactive secret provisioning requires a terminal console.");
        }
        char[] locValue = aConsole.readPassword("Secret content: ");
        requireNonEmpty(locValue, "Secret content");
        try {
            AIcCredentialService locService = new AIcCredentialService();
            locService.storeNamedSecret(aSecretId, locValue);
            System.out.println("Stored named secret '" + aSecretId + "' in " + locService.getStoreId() + ".");
            return 0;
        } finally {
            if (locValue != null) {
                Arrays.fill(locValue, '\0');
            }
        }
    }

    private static int statusNamedSecret(String aSecretId) {
        boolean locStored = new AIcCredentialService().isNamedSecretStored(aSecretId);
        System.out.println("Named secret '" + aSecretId + "' is " + (locStored ? "available." : "not available."));
        return locStored ? 0 : 3;
    }

    private static int removeNamedSecret(String aSecretId) {
        AIcCredentialService locService = new AIcCredentialService();
        locService.removeNamedSecret(aSecretId);
        System.out.println("Removed named secret '" + aSecretId + "'.");
        return 0;
    }

    private static int bootstrapNamedSecret(String aSecretId) {
        Optional<char[]> locSecret = new AIcCredentialService().readNamedSecret(aSecretId);
        if (locSecret.isEmpty()) {
            return 3;
        }
        char[] locValue = locSecret.get();
        try {
            System.out.print(locValue);
            return 0;
        } finally {
            Arrays.fill(locValue, '\0');
        }
    }

    private static char[] readCredentialDocumentInput(String aFile) {
        try {
            if (aFile == null || "-".equals(aFile)) {
                return new String(System.in.readAllBytes(), StandardCharsets.UTF_8).toCharArray();
            }
            Path locPath = Path.of(aFile);
            if (!Files.isRegularFile(locPath)) {
                throw new AIxCredentialException("Credential document file does not exist: " + locPath);
            }
            return Files.readString(locPath, StandardCharsets.UTF_8).toCharArray();
        } catch (IOException aException) {
            throw new AIxCredentialException("Cannot read credential document input.", aException);
        }
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

    private static void requireArgumentCount(String[] aArguments, int aExpected) {
        if (aArguments.length != aExpected) {
            throw new IllegalArgumentException("Invalid command arguments.");
        }
    }

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  algites-credentials set <profile> <basic|bearer|api-key|certificate>");
        System.err.println("  algites-credentials status <profile> <basic|bearer|api-key|certificate>");
        System.err.println("  algites-credentials remove <profile> <basic|bearer|api-key|certificate>");
        System.err.println("  algites-credentials env <profile> <basic|bearer|api-key|certificate>");
        System.err.println("  algites-credentials document-set [<json-file>|-]");
        System.err.println("  algites-credentials document-status");
        System.err.println("  algites-credentials document-remove");
        System.err.println("  algites-credentials secret-set <secret-id>");
        System.err.println("  algites-credentials secret-status <secret-id>");
        System.err.println("  algites-credentials secret-remove <secret-id>");
        System.err.println("  algites-credentials store-id");
        System.err.println("  algites-credentials store-diagnostics");
    }
}
