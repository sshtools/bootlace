package com.sshtools.bootlace.api;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.sshtools.bootlace.api.Logs.BootLog;
import com.sshtools.bootlace.api.Logs.Log;

public final class Artifact {
	private final static Log LOG = Logs.of(BootLog.RESOLUTION);

	public final static Artifact find(ArtifactRef ref) {
		Path pomFile = null;
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
			return new Artifact(ref, POM.of(pomFile));
		}
		else
			throw new UnsupportedOperationException();
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
