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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import com.sshtools.bootlace.api.GAV;
import com.sshtools.bootlace.api.Http.HttpClientFactory;
import com.sshtools.bootlace.api.LocalRepository;
import com.sshtools.bootlace.api.XML;

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
		
		@Override
		public LocalRepository.LocalRepositoryBuilder withName(String name) {
			this.name = name;
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
	
	private LocalRepositoryImpl(LocalRepositoryBuilder builder) {
		this.root = builder.root;
		this.name = builder.name;
		this.id = ID;
	}

	protected LocalRepositoryImpl(Path root, String name, String id) {
		super();
		this.root = root;
		this.name = name;
		this.id = id;
	}

	@Override
	public Optional<ResolutionResult> resolve(HttpClientFactory httpFactory, GAV gav) {
			// TODO classifier
		var ngav = gav;
		if(!gav.hasVersion()) {
			var meta = resolveGav(gav).resolve("maven-metadata-local.xml");
			if(Files.exists(meta)) {
				try(var in = Files.newInputStream(meta)) {
					ngav = gav.toWithVersion(XML.of(in).child("versioning").map(c -> {
						var rel = c.value("release");
						if(rel.isPresent()) {
							return rel.get();
						}
						else {
							var vers = c.child("versions");
							if(vers.isPresent()) {
								return vers.get().children().getLast().toString();
							}
							else {
								throw new IllegalArgumentException("Local maven metadata for " + gav + " exists but has no versions. Latest version cannot be deduced.");
							}
						}
					}).orElseThrow(() -> new IllegalArgumentException("Local maven metadata for " + gav + " exists but has no versions. Lateest version cannot be deduced.")));
				}
				catch(IOException ioe) {
					throw new UncheckedIOException(ioe);
				}
			}
			else {
				return Optional.empty();
			}
		}
		
		return Optional.of(ResolutionResult.of(resolveGav(ngav).toUri()));
	}

	protected Path resolveGav(GAV gav) {
		return root.resolve(LocalRepository.gavPath(gav));
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
