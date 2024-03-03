package com.sshtools.bootlace.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Optional;

import com.sshtools.bootlace.api.Http.HttpClientFactory;

public interface RemoteRepository extends Repository {
	
	public interface RemoteRepositoryBuilder extends Repository.RepositoryBuilder<RemoteRepositoryBuilder, RemoteRepository> {
		RemoteRepository build();

		RemoteRepositoryBuilder withId(String id);

		RemoteRepositoryBuilder withName(String name);

		RemoteRepositoryBuilder withRoot(URI root);

		RemoteRepositoryBuilder withReleases(boolean releases);

		RemoteRepositoryBuilder withSnapshots(boolean snapshots);
	}

	InputStream download(HttpClientFactory httpClient, GAV gav, URI uri, ResolutionResult result,
			Optional<ResolutionMonitor> monitor) throws IOException;

}
