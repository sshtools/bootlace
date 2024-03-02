package com.sshtools.bootlace.api;

import java.net.URI;
import java.util.Optional;

import com.sshtools.bootlace.api.Http.HttpClientFactory;

public interface Repository {
	

	public interface RepositoryBuilder<BLDR extends RepositoryBuilder<?, REPO>, REPO> {
		
		String id();

		BLDR withName(String name);

		BLDR withRoot(String root);
		
		REPO build();
	}

	public interface ResolutionResult {

		static ResolutionResult of(URI uri) {
			return new ResolutionResult() {

				@Override
				public URI uri() {
					return uri;
				}
			};
		}

		URI uri();
	}
		
		default boolean supported(GAV gav) {
			return gav.repositoryOr().map(r -> r.equals(id())).orElse(false);
		}

	Optional<ResolutionResult> resolve(HttpClientFactory httpFactory, GAV gav);

	String name();
	
	String id();

}
