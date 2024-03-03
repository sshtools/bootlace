package com.sshtools.bootlace.api;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public interface LocalRepository extends Repository {

	public interface LocalRepositoryBuilder extends Repository.RepositoryBuilder<LocalRepositoryBuilder, LocalRepository> {

		LocalRepositoryBuilder withRoot(Path root);
		
		LocalRepositoryBuilder withPattern(String patterrn);

		LocalRepository build();
	}

	static Path gavPath(String pattern, GAV gav) {
		return Paths.get(pattern.
				replace('/', File.separatorChar).
				replace('\\', File.separatorChar).
				replace("%G", dottedToPath(gav.groupId())).
				replace("%g", gav.groupId()).
				replace("%a", gav.artifactId()).
				replace("%v", gav.version()));
	}

	static String dottedToPath(String dotted) {
		return dotted.replace('.', File.separatorChar);
	}

	String ID = "local";
}