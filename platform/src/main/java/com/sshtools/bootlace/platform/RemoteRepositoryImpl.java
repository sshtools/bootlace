/**
 * Copyright © 2023 JAdaptive Limited (support@jadaptive.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the “Software”), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.sshtools.bootlace.platform;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.NoSuchFileException;
import java.util.Optional;

import com.sshtools.bootlace.api.GAV;
import com.sshtools.bootlace.api.Http;
import com.sshtools.bootlace.api.Http.HttpClientFactory;
import com.sshtools.bootlace.api.RemoteRepository;
import com.sshtools.bootlace.api.ResolutionMonitor;

public final class RemoteRepositoryImpl implements RemoteRepository {

	public final static class RemoteRepositoryBuilder implements RemoteRepository.RemoteRepositoryBuilder {
		private URI root = URI.create("https://repo1.maven.org/maven2");
		private String name = "Remote Repository";
		private String id = "central";
		private Optional<Boolean> releases = Optional.empty();
		private Optional<Boolean> snapshots  = Optional.empty();

		@Override
		public RemoteRepositoryBuilder withName(String name) {
			this.name = name;
			return this;
		}

		@Override
		public RemoteRepositoryBuilder withRoot(String root) {
			return withRoot(URI.create(root));
		}

		@Override
		public RemoteRepositoryBuilder withId(String id) {
			this.id = id;
			return this;
		}

		@Override
		public RemoteRepositoryBuilder withRoot(URI root) {
			this.root = root;
			return this;
		}

		@Override
		public RemoteRepository build() {
			return new RemoteRepositoryImpl(this);
		}

		@Override
		public RemoteRepositoryBuilder withReleases(boolean releases) {
			this.releases = Optional.of(releases);
			return this;
		}

		@Override
		public RemoteRepositoryBuilder withSnapshots(boolean snapshots) {
			this.snapshots = Optional.of(snapshots);
			return this;
		}
	}

	private final URI root;
	private final String name;
	private final String id;
	private final boolean releases;
	private final boolean snapshots;

	public RemoteRepositoryImpl(RemoteRepositoryBuilder builder) {
		this.root = builder.root;
		this.name = builder.name;
		this.id = builder.id;
		
		releases = builder.releases.orElse(builder.snapshots.isEmpty());
		snapshots = builder.snapshots.orElse(builder.releases.isEmpty());
	}

	@Override
	public boolean supported(GAV gav) {
		return gav.repositoryOr().isEmpty() || gav.repository().equals(id());
	}

	@Override
	public Optional<ResolutionResult> resolve(HttpClientFactory factory, GAV gav) {
		if((gav.isSnapshot() && !snapshots) ||
		   (!gav.isSnapshot() && !releases)) {
			return Optional.empty();
		}
		
		return Optional.of(ResolutionResult.of(URI.create(root.toString() + '/' + dottedToPath(gav.groupId()) + '/'
				+ gav.artifactId() + '/' + gav.version() + '/' + gav.artifactId() + "-" + gav.version() + ".jar")));
	}

	static String dottedToPath(String dotted) {
		return dotted.replace('.', File.separatorChar);
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public InputStream download(HttpClientFactory httpClientFactory, GAV gav, URI uri, ResolutionResult result,
			Optional<ResolutionMonitor> monitor) throws IOException {
		
		if((gav.isSnapshot() && !snapshots) ||
		   (!gav.isSnapshot() && !releases)) {
			throw new NoSuchFileException(uri.toString());
		}
		
		var httpClient = httpClientFactory.get().build();
		
		if(gav.isSnapshot() && !gav.isResolved()) {
			var metaUri = uri.resolve("maven-metadata.xml");
			var request = HttpRequest.newBuilder().
					GET().
					uri(metaUri).
					header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:123.0) Gecko/20100101 Firefox/123.0").
					build();
			System.out.println(metaUri);
			var handler = HttpResponse.BodyHandlers.ofInputStream();
			try {
				var response = httpClient.send(request, handler);
				switch (response.statusCode()) {
				case 200:
					try(var in = response.body()) {
						var meta = SnapshotMetaData.of(in);
						uri = uri.resolve(meta.latestJarFilename());
						gav = gav.toWithVersion(meta.latestJarVersion());
					}
					break;
				case 404:
					throw new NoSuchFileException(metaUri.toString());
				default:
					throw new IOException("Unexpected status " + response.statusCode() + " for " + metaUri);
				}
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}
		}
		
		var fUri = uri;
		var fGav = gav;
		
		var request = HttpRequest.newBuilder().GET().uri(fUri).build();
		var handler = HttpResponse.BodyHandlers.ofInputStream();
		try {
			var response = httpClient.send(request, handler);
			switch (response.statusCode()) {
			case 200:
				monitor.ifPresent(m -> m.found(fGav, fUri, this, Http.contentLength(response)));
				return response.body();
			case 404:
				throw new NoSuchFileException(fUri.toString());
			default:
				throw new IOException("Unexpected status " + response.statusCode() + " for " + fUri);
			}
		} catch (InterruptedException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public String id() {
		return id;
	}
}
