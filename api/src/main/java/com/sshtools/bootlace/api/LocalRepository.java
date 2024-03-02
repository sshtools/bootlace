package com.sshtools.bootlace.api;

import java.nio.file.Path;

public interface LocalRepository extends Repository {

	public interface LocalRepositoryBuilder extends Repository.RepositoryBuilder<LocalRepositoryBuilder, LocalRepository> {

		LocalRepositoryBuilder withRoot(Path root);

		LocalRepository build();
	}
}