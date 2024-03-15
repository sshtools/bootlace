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

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

/**
 * An extension layer is a container layer that consists of one or more further
 * child layers, each itself an <em>Extension</em>.
 * <p>
 * An extension consists of a primary artifact that contains the
 * <code>layers.ini</code> and any primary classes and resources, as well as any
 * required dependency artifacts.
 * <p>
 * As with all layer artifacts, both the dependencies and primary artifacts must
 * be JPMS compliant and contain a single <code>module-info.java</code> that
 * defines the modules name and any dependency modules.
 * <p>
 * The same, or different version of, a dependency artifact may exist in
 * multiple child extension layers, but you can never have a dependency artifact
 * that already exists in a parent. This restriction is enforced via the JPMS
 * module name.
 * 
 */
public interface ExtensionLayer extends ChildLayer {

	/**
	 * The path that contains the artifacts for the child layers in this layer.
	 * 
	 * @return path of layer extensions
	 */
	Path path();

	/**
	 * Get the child extension layers.
	 * 
	 * @return layers
	 */
	Collection<? extends Layer> extensions();

	/**
	 * Get an extension layer given it's id.
	 * 
	 * @param id id
	 * @return layer
	 */
	default Optional<? extends Layer> extension(String id) {
		return extensions().stream().filter(l -> l.id().equals(id)).findFirst();
	}
}
