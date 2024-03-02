package com.sshtools.bootlace.api;

import java.util.Optional;
import java.util.Set;

public interface Layer {
	String id();

	Optional<String> name();

	Set<String> appRepositories();

	Optional<ResolutionMonitor> monitor();

	Set<String> remoteRepositories();

	Set<String> localRepositories();

	boolean global();

	default Optional<LayerArtifacts> finalArtifacts() {
		return Optional.empty();
	}
}