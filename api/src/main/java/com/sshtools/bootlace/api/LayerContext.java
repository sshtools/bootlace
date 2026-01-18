/**
 * Copyright © 2023 JAdaptive Limited (support@jadaptive.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the “Software”), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.sshtools.bootlace.api;

import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.BiFunction;

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
	
	/**
	 * This NEEDS to be removed. Access to child layers here is a hack to make npm2mvn work
	 * without mapping them manually. Find another way! 
	 */
	@Deprecated
	Iterable<ModuleLayer> childLayers();
	
	Layer layer();

	ClassLoader loader();

	Set<Layer> parents();
	
	default Optional<Layer> parentOfId(String id) {
		return parents().stream().filter(s -> s.id().equals(id)).findFirst();
	}

	<S> ServiceLoader<S> loadFirst(Class<S> srvType, BiFunction<ModuleLayer, Class<S>, ServiceLoader<S>> loader);

	<S> Iterable<S> loadAll(Class<S> srvType, BiFunction<ModuleLayer, Class<S>, ServiceLoader<S>> loader);


}
