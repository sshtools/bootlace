package com.sshtools.bootlace.repositories;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import com.sshtools.bootlace.api.GAV;
import com.sshtools.bootlace.api.Http.HttpClientFactory;
import com.sshtools.bootlace.api.LocalRepository;

public class LocalRepositoryImpl implements  LocalRepository {
	
	public final static class LocalRepositoryBuilder implements LocalRepository.LocalRepositoryBuilder {
		
		private Path root = Paths.get(System.getProperty("user.home")).
				resolve(".m2").
				resolve("repository");
		
		private String name = "Local Repository";
		
		@Override
		public LocalRepository.LocalRepositoryBuilder withName(String name) {
			this.name = name;
			return this;
		}
		
		@Override
		public LocalRepository.LocalRepositoryBuilder withRoot(String root) {
			return withRoot(Paths.get(root));
		}
				
		@Override
		public LocalRepository.LocalRepositoryBuilder withRoot(Path root) {
			this.root = root;
			return this;
		}
		
		@Override
		public LocalRepository build() {
			return new LocalRepositoryImpl(this);
		}

		@Override
		public String id() {
			return "m2";
		}
	}
	
	private final Path root;
	private final String name;
	private final String id;
	
	private LocalRepositoryImpl(LocalRepositoryBuilder builder) {
		this.root = builder.root;
		this.name = builder.name;
		this.id = builder.id();
	}

	protected LocalRepositoryImpl(Path root, String name, String id) {
		super();
		this.root = root;
		this.name = name;
		this.id = id;
	}

	@Override
	public Optional<ResolutionResult> resolve(HttpClientFactory httpFactory, GAV gav) {
			// TODO classifier
			// TODO latest version
			return Optional.of(ResolutionResult.of(resolveGav(gav)
					.toUri()));
	}

	protected Path resolveGav(GAV gav) {
		return root.
				resolve(dottedToPath(gav.groupIdOr().orElse(""))).
				resolve(gav.artifactId()).
				resolve(gav.version()).
				resolve(gav.artifactId() + "-" + gav.version() + ".jar");
	}
	
	static String dottedToPath(String dotted) {
		return dotted.replace('.', File.separatorChar);
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public String id() {
		return id;
	}
}
