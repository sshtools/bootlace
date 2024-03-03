package com.sshtools.bootlace.platform;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import com.sshtools.bootlace.api.GAV;
import com.sshtools.bootlace.api.Http.HttpClientFactory;
import com.sshtools.bootlace.api.LocalRepository;

public class LocalRepositoryImpl implements  LocalRepository {
	
	private static class LazyLocalRepository {
		static LocalRepository DEFAULT = (LocalRepository)new LocalRepositoryBuilder().build();
	}

	public static LocalRepository localRepository() {
		return LazyLocalRepository.DEFAULT;
	}
	
	public final static class LocalRepositoryBuilder implements LocalRepository.LocalRepositoryBuilder {
		
		private Path root = BootstrapRepository.m2Local();
		
		private String name = "Local Repository";
		private String pattern = System.getProperty("bootlace.local.pattern", "%G/%a/%v/%a-%v.jar");
		
		@Override
		public LocalRepository.LocalRepositoryBuilder withName(String name) {
			this.name = name;
			return this;
		}
		
		@Override
		public LocalRepository.LocalRepositoryBuilder withPattern(String pattern) {
			this.pattern = pattern;
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
	}
	
	private final Path root;
	private final String name;
	private final String id;
	private final String pattern;
	
	private LocalRepositoryImpl(LocalRepositoryBuilder builder) {
		this.root = builder.root;
		this.name = builder.name;
		this.pattern = builder.pattern;
		this.id = ID;
	}

	protected LocalRepositoryImpl(Path root, String name, String id, String pattern) {
		super();
		this.root = root;
		this.name = name;
		this.id = id;
		this.pattern = pattern;
	}

	@Override
	public Optional<ResolutionResult> resolve(HttpClientFactory httpFactory, GAV gav) {
			// TODO classifier
			// TODO latest version
			return Optional.of(ResolutionResult.of(resolveGav(gav)
					.toUri()));
	}

	protected Path resolveGav(GAV gav) {
		return root.resolve(LocalRepository.gavPath(pattern, gav));
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
