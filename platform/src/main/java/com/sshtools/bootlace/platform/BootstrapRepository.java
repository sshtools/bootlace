package com.sshtools.bootlace.platform;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import com.sshtools.bootlace.api.GAV;
import com.sshtools.bootlace.api.Http.HttpClientFactory;
import com.sshtools.bootlace.api.LocalRepository;
import com.sshtools.bootlace.api.Logs;
import com.sshtools.bootlace.api.Logs.BootLog;
import com.sshtools.bootlace.api.Logs.Log;
import com.sshtools.bootlace.api.Repository;

public final class BootstrapRepository implements LocalRepository {
	private final static Log LOG = Logs.of(BootLog.RESOLUTION);
	
	public final static Path m2Local() {
		return Paths.get(System.getProperty("user.home")).
				resolve(".m2").
				resolve("repository");
	}
	
	public final static class BootstrapRepositoryBuilder implements Repository.RepositoryBuilder<BootstrapRepositoryBuilder, BootstrapRepository> {
		
		private Set<Path> roots = new LinkedHashSet<>(); 
		private String name = "Bootstrap";
		
		@Override
		public BootstrapRepository build() {
			return new BootstrapRepository(this);
		}

		@Override
		public String id() {
			return "bootstrap";
		}

		public BootstrapRepositoryBuilder withName(String name) {
			this.name = name;
			return this;
		}

		public BootstrapRepositoryBuilder withRoot(String root) {
			return withRoot(Paths.get(root));
		}

		public BootstrapRepositoryBuilder withRoot(Path root) {
			return withRoots(root);
		}
		
		public BootstrapRepositoryBuilder withRoots(Path... roots) {
			this.roots = Set.of(roots);
			return this;
		}
	}
	
	private final String name;
	private Set<Path> roots;

	private BootstrapRepository(BootstrapRepositoryBuilder bldr) {
		this.name = bldr.name;
		this.roots = bldr.roots;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public String id() {
		return "bootstrap";
	}

	@Override
	public Optional<ResolutionResult> resolve(HttpClientFactory httpFactory, GAV gav) {
		if(roots.isEmpty()) {
			if(LOG.debug())
				LOG.debug("No roots configured, trying default locations");

			var prop = System.getProperty("bootlace.bootstrap.repository");
			if (prop != null) {
				
				if(LOG.debug())
					LOG.debug("System property specified, using that");
				
				return Optional.of(ResolutionResult.of(resolveGav(Path.of(prop), gav).toUri()));
			}
	
			var bootstrap = Paths.get("bootlace.bootstrap.directory", "bootstrap");
			if (Files.exists(bootstrap)) {
				
				if(LOG.debug())
					LOG.debug("Bootstrap directory exists, using that");
				
				return Optional.of(ResolutionResult.of(bootstrap.toUri()));
			}
	
			if(gav.groupIdOr().isPresent()) {
				var m2 = Paths.get(System.getProperty("bootlace.bootstrap.m2",
						System.getProperty("user.home") + File.separator + ".m2" + File.separator + "repository"));
				if (Files.exists(m2))
					return Optional.of(ResolutionResult.of(resolveGav(m2, gav).toUri()));
			}
		}
		else {
			LOG.debug("Bootstrap roots configured, trying each");
			for(var path :roots) {
				var art = resolveGav(path, gav);
				
				if(LOG.debug())
					LOG.debug("Bootstrap root ''{0}'' resolved ''{1}'' to ''{2}''", path, gav, art);
				
				if(Files.exists(art)) {
					
					if(LOG.debug())
						LOG.debug("Exists! Using ''{0}'' for ''{1}''", art, gav);
					
					return Optional.of(ResolutionResult.of(art.toUri()));
				}
				
			}
		}
		
		if(LOG.debug())
			LOG.debug("Artifact not in bootstrap repository.");
			

		return Optional.empty();
	}

	protected Path resolveGav(Path root, GAV gav) {
		return root.resolve(Repositories.dottedToPath(gav.groupId())).resolve(gav.artifactId()).resolve(gav.version())
				.resolve(gav.artifactId() + "-" + gav.version() + ".jar");
	}

}