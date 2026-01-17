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

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The public API of a layer. Every Bootlace application is made up of multiple layers
 * each attached to at least one parent.
 * <p>
 * Enforced by JPMS, a layer may only access classes and resources in a parent layer.
 * For every {@link Layer} there is a corresponding {@link ModuleLayer}. 
 * <p>
 * The exception for this is services, which may search child layers for service implementations.
 * 
 * 
 */
public interface Layer {
	/**
	 * The identifier of the layer.
	 * 
	 * @return ID
	 */
	String id();
	
	/**
	 * The access mode of the layer.
	 * 
	 * @return access
	 */
	Access access();

	/**
	 * An optional descriptive name for the layer.
	 * 
	 * @return name
	 */
	Optional<String> name();

	Set<String> appRepositories();

	Optional<ResolutionMonitor> monitor();

	Set<String> remoteRepositories();

	Set<String> localRepositories();
	
	LayerType type();
	
	Optional<ModuleParameters> moduleParameters();

	default Optional<LayerArtifacts> finalArtifacts() {
		return Optional.empty();
	}
	
	ClassLoader loader();
	
	/**
	 * The JPMS module layer for this bootlace layer.
	 *  
	 * @return module layer
	 */
	ModuleLayer moduleLayer();

	/**
	 * Get all of the layers that have this layer as a parent.
	 * 
	 * @return layers
	 */
	List<ChildLayer> childLayers();
}