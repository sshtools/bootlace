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
