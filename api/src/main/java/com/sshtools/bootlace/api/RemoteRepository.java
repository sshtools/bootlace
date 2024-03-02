package com.sshtools.bootlace.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Optional;

import com.sshtools.bootlace.api.Http.HttpClientFactory;

public interface RemoteRepository extends Repository {
	public interface RemoteRepositoryBuilder extends Repository.RepositoryBuilder<RemoteRepositoryBuilder, RemoteRepository> {
		RemoteRepository build();

		RemoteRepositoryBuilder withRoot(URI root);
	}

	InputStream download(HttpClientFactory httpClient, GAV gav, URI uri, ResolutionResult result,
			Optional<ResolutionMonitor> monitor) throws IOException;

}
