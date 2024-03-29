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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import com.sshtools.bootlace.api.GAV;
import com.sshtools.bootlace.api.Http.HttpClientFactory;
import com.sshtools.bootlace.api.LocalRepository;

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
		private String pattern = System.getProperty("bootlace.local.pattern", "%G/%a/%v/%a-%v.jar");
		
		@Override
		public LocalRepository.LocalRepositoryBuilder withName(String name) {
			this.name = name;
			return this;
		}
		
		@Override
		public LocalRepository.LocalRepositoryBuilder withPattern(String pattern) {
			this.pattern = pattern;
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
	private final String pattern;
	
	private LocalRepositoryImpl(LocalRepositoryBuilder builder) {
		this.root = builder.root;
		this.name = builder.name;
		this.pattern = builder.pattern;
		this.id = ID;
	}

	protected LocalRepositoryImpl(Path root, String name, String id, String pattern) {
		super();
		this.root = root;
		this.name = name;
		this.id = id;
		this.pattern = pattern;
	}

	@Override
	public Optional<ResolutionResult> resolve(HttpClientFactory httpFactory, GAV gav) {
			// TODO classifier
			// TODO latest version
			return Optional.of(ResolutionResult.of(resolveGav(gav)
					.toUri()));
	}

	protected Path resolveGav(GAV gav) {
		return root.resolve(LocalRepository.gavPath(pattern, gav));
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
