package com.sshtools.bootlace.api;

import java.net.URI;
import java.util.Optional;

public interface ResolutionMonitor {
	default void have(GAV gav, URI uri, Repository remoteRepository) {
	}

	default void need(GAV gav, URI uri, Repository remoteRepository) {
	}

	default void found(GAV gav, URI uri, Repository remoteRepository, Optional<Long> size) {
	}

	default void downloading(GAV gav, URI uri, Repository remoteRepository, Optional<Long> bytes) {
	}

	default void downloaded(GAV gav, URI uri, Repository remoteRepository) {
	}

	default void failed(GAV gav, String location, Repository remoteRepository, Exception exception) {
	}
	
	default void loadingLayer(ChildLayer layerDef) {
	}
	
	default void loadedLayer(ChildLayer layerDef) {
	}

	static ResolutionMonitor monitor() {
		return DefaultResolutionMonitor.DEFAULT;
	}

	static ResolutionMonitor empty() {
		return DefaultResolutionMonitor.EMPTY;
	}

	public final static class DefaultResolutionMonitor {
		private final static ResolutionMonitor EMPTY = new ResolutionMonitor() {
		};
		private final static ResolutionMonitor DEFAULT = new ResolutionMonitor() {

			@Override
			public void loadingLayer(ChildLayer layerDef) {
				System.out.format("[Loading] %s%n", layerDef.id());
			}

			@Override
			public void loadedLayer(ChildLayer layerDef) {
				System.out.format("[Loaded] %s%n", layerDef.id());
			}

			@Override
			public void have(GAV gav, URI uri, Repository remoteRepository) {
				if(remoteRepository == null)
					System.out.format("    [Have] %s @ %s%n", gav, uri);
				else
					System.out.format("    [Have] %s @ %s in %s%n", gav, uri, remoteRepository.name());
			}

			@Override
			public void need(GAV gav, URI uri, Repository remoteRepository) {
				System.out.format("    [Need] %s @ %s in %s%n", gav, uri, remoteRepository.name());
			}

			@Override
			public void found(GAV gav, URI uri, Repository remoteRepository, Optional<Long> size) {
				System.out.format("    [Downloading] %s @ %s in %s%n", gav, uri, remoteRepository.name());
			}

			@Override
			public void downloading(GAV gav, URI uri, Repository remoteRepository, Optional<Long> bytes) {
			}

			@Override
			public void downloaded(GAV gav, URI uri, Repository remoteRepository) {
				System.out.format("    [Downloaded] %s%n", gav);
			}

			@Override
			public void failed(GAV gav, String location, Repository remoteRepository, Exception exception) {
				System.out.format("    [Failed!] %s%n", exception.getMessage());
			}

		};
	}
}
