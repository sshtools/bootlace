package com.sshtools.bootlace.api;

import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.Properties;

public record ArtifactRef(GAV gav, Optional<Path> path) {
	
	public static ArtifactRef of(Properties properties) {
		return of(GAV.ofProperties(properties));
	}
	
	public static ArtifactRef of(Properties properties, Path path) {
		return of(GAV.ofProperties(properties), path);
	}

	public static ArtifactRef of(GAV gav) {
		return new ArtifactRef(gav, Optional.empty());
	}
	
	public static ArtifactRef of(GAV gav, Path path) {
		gav.versionOr().ifPresent(g -> { 
			throw new IllegalArgumentException(MessageFormat.format("The GAV ''{0}'' has a local path of ''{1}'', so the GAV should not have a version number.", gav, path));	
		});
		return new ArtifactRef(gav, Optional.of(path));
	}

	public ArtifactRef withPath(Path path) {
		return new ArtifactRef(gav, Optional.of(path));
	}

	public boolean hasPath() {
		return path.isPresent();
	}

}
