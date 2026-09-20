package eu.algites.tool.build.policy.polarman;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * <p>
 * Title: {@link AIsPolicyArtifactManagerUtils}
 * </p>
 * <p>
 * Description: Utilitties for the Repository Artifact Manager
 * </p>
 * <p>
 * Copyright: Copyright (c) 2026 Artur Linhart, Algites
 * </p>
 * <p>
 * Company: Artur Linhart, Algites
 * </p>
 *
 * @author linhart1
 * @date 04.01.26 11:39
 */
public final class AIsPolicyArtifactManagerUtils {

	private AIsPolicyArtifactManagerUtils() {
	}

	/**
	 * Traverses upwards from startPath and tries to find a directory
	 * containing the given marker file.
	 *
	 * @param startPath starting directory or file
	 * @param markerFileName name of marker file (e.g. algites-source-repository.properties)
	 * @return path to repository root
	 * @throws IllegalStateException if root cannot be found
	 */
	public static Path findRepositoryRoot(Path startPath, String markerFileName) {
		Objects.requireNonNull(startPath, "startPath");
		Objects.requireNonNull(markerFileName, "markerFileName");

		Path current = startPath.normalize().toAbsolutePath();

		if (Files.isRegularFile(current)) {
			current = current.getParent();
		}

		while (current != null) {
			Path marker = current.resolve(markerFileName);
			if (Files.isRegularFile(marker)) {
				return current;
			}
			current = current.getParent();
		}

		throw new IllegalStateException(
				"Repository root not found (marker file '" + markerFileName + "' not found above " + startPath + ")"
		);
	}

	/**
	 * Optional-based variant (if you prefer not to throw).
	 */
	public static Optional<Path> tryFindRepositoryRoot(Path startPath, String markerFileName) {
		try {
			return Optional.of(findRepositoryRoot(startPath, markerFileName));
		} catch (IllegalStateException e) {
			return Optional.empty();
		}
	}
}
