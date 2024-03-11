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
package com.sshtools.bootlace.platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.sshtools.bootlace.api.ArtifactRef;
import com.sshtools.bootlace.api.Logs;
import com.sshtools.bootlace.api.Zip;
import com.sshtools.bootlace.api.Logs.BootLog;
import com.sshtools.bootlace.api.Logs.Log;

public final class Artifact {
	private final static Log LOG = Logs.of(BootLog.RESOLUTION);

	public final static Artifact find(ArtifactRef ref, Path jarFile) {
		Path pomFile = jarFile;
		var path = ref.path().orElse(null);
		
		/* Is the path a development target/classes directory? If so, the
		 * POM is in the same parent as 'target'.
		 */
		if(path != null && path.getFileName().toString().equals("classes")) {
			var parent = path.getParent();
			if(parent.getFileName().toString().equals("target")) {
				parent = parent.getParent();
				if(parent == null) {
					parent = Paths.get(System.getProperty("user.dir"));
				}
			}
			pomFile = parent.resolve("pom.xml");
		}
		
		if(pomFile != null && Files.exists(pomFile)) {
			LOG.info("Artifact from {0}", pomFile);
			if(pomFile.getFileName().toString().endsWith(".jar")) {
				try(var in = Zip.find(pomFile, String.format("META-INF/maven/%s/%s/pom.xml", ref.gav().groupId(), ref.gav().artifactId()))) {
					return new Artifact(ref, POM.of(in));
				}
				catch(IOException ioe) {
					throw new UncheckedIOException(ioe);
				}
			}
			else {
				return new Artifact(ref, POM.of(pomFile));
			}
		}
		else
			throw new UnsupportedOperationException(ref.toString());
	}
	
	private final ArtifactRef ref;
	private final POM pom;
	
	private Artifact(ArtifactRef ref, POM pom) {
		this.ref = ref;
		this.pom = pom;
	}
	
	public ArtifactRef ref() {
		return ref;
	}
	
	public POM pom() {
		return pom;
	}
	
	public List<ArtifactRef> resolve() {
		/* TODO resolve ALL actual artifacts ... tough! */
		throw new UnsupportedOperationException("TODO");
	}
}
