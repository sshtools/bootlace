package com.sshtools.bootlace.api;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

public interface LayerArtifacts {

	Layer layer();

	Set<ArtifactRef> artifacts();

	Set<Path> paths();

	default Optional<ArtifactRef> artifact(GAV gav) {
		return artifacts().stream().filter(a -> a.gav().toWithoutVersion().equals(gav.toWithoutVersion())).findFirst();
	}

}