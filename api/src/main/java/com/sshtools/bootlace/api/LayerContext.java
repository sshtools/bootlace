package com.sshtools.bootlace.api;

import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Allows access to information about, and resources from any given layer that is a child
 * of this layer.
 * <p>
 * Access to parent layers will never be granted to protect layers that would otherwise be
 * isolated.
 */
public interface LayerContext {

	public interface Provider {
		LayerContext get(ModuleLayer layer);
	}

	public static LayerContext get(Class<?> clazz) {
		return get(clazz.getModule().getLayer());
	}

	public static LayerContext get(ModuleLayer layer) {
		return ServiceLoader.load(Provider.class).findFirst().orElseThrow(() -> new IllegalStateException("No layer context provider")).get(layer);
	}
	

	default Optional<ArtifactRef> findArtifact(GAV gav) {
		return layer().
				finalArtifacts().map(la -> la.artifact(gav)).
				orElse(Optional.empty());
	}
	
	Iterable<ModuleLayer> childLayers();
	
	Iterable<ModuleLayer> globalLayers();
	
	Layer layer();

	ClassLoader loader();

	Set<Layer> parents();
	
	default Optional<Layer> parentOfId(String id) {
		return parents().stream().filter(s -> s.id().equals(id)).findFirst();
	}

}
