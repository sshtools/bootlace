package com.sshtools.bootlace.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
