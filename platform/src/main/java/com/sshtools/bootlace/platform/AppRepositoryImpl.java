package com.sshtools.bootlace.platform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.sshtools.bootlace.api.AppRepository;
import com.sshtools.bootlace.api.GAV;

public class AppRepositoryImpl extends LocalRepositoryImpl implements AppRepository {
	
	private static class LazyAppRepository {
		static AppRepository DEFAULT = (AppRepository)new AppRepositoryBuilder().build();
	}

	public static AppRepository appRepository() {
		return LazyAppRepository.DEFAULT;
	}

	public final static class AppRepositoryBuilder implements AppRepository.AppRepositoryBuilder {
		private Path root = Paths.get(AppRepository.ID);

		private String name = "App Repository";
		private String pattern = System.getProperty("bootlace.app.pattern", "%G/%a/%v/%a-%v.jar");

		@Override
		public AppRepositoryBuilder withName(String name) {
			this.name = name;
			return this;
		}
		
		@Override
		public AppRepositoryBuilder withPattern(String pattern) {
			this.pattern = pattern;
			return this;
		}

		@Override
		public AppRepositoryBuilder withRoot(String root) {
			return withRoot(Paths.get(root));
		}

		@Override
		public AppRepositoryBuilder withRoot(Path root) {
			this.root = root;
			return this;
		}

		@Override
		public AppRepositoryImpl build() {
			return new AppRepositoryImpl(this);
		}
	}

	private AppRepositoryImpl(AppRepositoryBuilder builder) {
		super(builder.root, builder.name, AppRepository.ID, builder.pattern);
	}

	@Override
	public boolean supported(GAV gav) {
		return gav.repositoryOr().isEmpty() || gav.repository().equals(id());
	}

	@Override
	public Path store(GAV gav, InputStream in) throws IOException {
		var path = resolveGav(gav);
		if (!Files.exists(path.getParent()))
			Files.createDirectories(path.getParent());
		try (var out = Files.newOutputStream(path)) {
			in.transferTo(out);
			return path;
		}
	}

}
