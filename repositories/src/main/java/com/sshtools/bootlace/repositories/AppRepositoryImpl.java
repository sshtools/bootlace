package com.sshtools.bootlace.repositories;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.sshtools.bootlace.api.AppRepository;
import com.sshtools.bootlace.api.GAV;

public class AppRepositoryImpl extends LocalRepositoryImpl implements AppRepository {

	public final static class AppRepositoryBuilder implements AppRepository.AppRepositoryBuilder {
		private Path root = Paths.get("repository");

		private String name = "App Repository";

		@Override
		public AppRepositoryBuilder withName(String name) {
			this.name = name;
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

		@Override
		public String id() {
			return "repository";
		}
	}

	private AppRepositoryImpl(AppRepositoryBuilder builder) {
		super(builder.root, builder.name, builder.id());
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
