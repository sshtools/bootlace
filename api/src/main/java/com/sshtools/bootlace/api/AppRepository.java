package com.sshtools.bootlace.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public interface AppRepository extends Repository {
	public static final String ID = "repository";
	
	public interface AppRepositoryBuilder extends Repository.RepositoryBuilder<AppRepositoryBuilder, AppRepository> {
		
		AppRepositoryBuilder withRoot(Path root);

		AppRepositoryBuilder withPattern(String pattern);

		@Override
		AppRepository build();
	}

	Path store(GAV gav, InputStream in) throws IOException;

}
