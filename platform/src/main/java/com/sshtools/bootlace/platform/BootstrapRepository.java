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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import com.sshtools.bootlace.api.GAV;
import com.sshtools.bootlace.api.Http.HttpClientFactory;
import com.sshtools.bootlace.api.LocalRepository;
import com.sshtools.bootlace.api.Logs;
import com.sshtools.bootlace.api.Logs.BootLog;
import com.sshtools.bootlace.api.Logs.Log;
import com.sshtools.bootlace.api.Repository;

public final class BootstrapRepository implements LocalRepository {
	private final static Log LOG = Logs.of(BootLog.RESOLUTION);
	
	public final static String ID = "bootstrap";
	
	private static class LazyBootstrapRepository {
		static BootstrapRepository DEFAULT = (BootstrapRepository)new BootstrapRepository.BootstrapRepositoryBuilder().build();
	}

	public static BootstrapRepository bootstrapRepository() {
		return LazyBootstrapRepository.DEFAULT;
	}
	
	public final static Path m2Local() {
		return Paths.get(System.getProperty("bootlace.bootstrap.m2",
				System.getProperty("user.home") + File.separator + ".m2" + File.separator + "repository"));
	}
	
	public final static class BootstrapRepositoryBuilder implements Repository.RepositoryBuilder<BootstrapRepositoryBuilder, BootstrapRepository> {
		
		private Set<Path> roots = new LinkedHashSet<>(); 
		private String name = "Bootstrap";
		
		@Override
		public BootstrapRepository build() {
			return new BootstrapRepository(this);
		}

		public BootstrapRepositoryBuilder withName(String name) {
			this.name = name;
			return this;
		}

		public BootstrapRepositoryBuilder withRoot(String root) {
			return withRoot(Paths.get(root));
		}

		public BootstrapRepositoryBuilder withRoot(Path root) {
			return withRoots(root);
		}
		
		public BootstrapRepositoryBuilder withRoots(Path... roots) {
			this.roots = Set.of(roots);
			return this;
		}
	}
	
	private final String name;
	private Set<Path> roots;


	private BootstrapRepository(BootstrapRepositoryBuilder bldr) {
		this.name = bldr.name;
		this.roots = bldr.roots;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public String id() {
		return BootstrapRepository.ID;
	}

	@Override
	public Optional<ResolutionResult> resolve(HttpClientFactory httpFactory, GAV gav) {
		if(roots.isEmpty()) {
			if(LOG.debug())
				LOG.debug("No roots configured, trying default locations");

			var prop = System.getProperty("bootlace.bootstrap.repository");
			if (prop != null) {
				
				if(LOG.debug())
					LOG.debug("System property specified, using that");
				
				return Optional.of(ResolutionResult.of(resolveGav(Path.of(prop), gav).toUri()));
			}
	
			var bootstrap = Paths.get("bootlace.bootstrap.directory", "bootstrap");
			if (Files.exists(bootstrap)) {
				
				if(LOG.debug())
					LOG.debug("Bootstrap directory exists, using that");
				
				return Optional.of(ResolutionResult.of(bootstrap.toUri()));
			}
	
//			if(gav.groupIdOr().isPresent()) {
//				var m2 = m2Local();
//				if (Files.exists(m2)) {
//					return Optional.of(ResolutionResult.of(resolveGav(m2, gav).toUri()));
//				}
//			}
		}
		else {
			LOG.debug("Bootstrap roots configured, trying each");
			for(var path :roots) {
				var art = resolveGav(path, gav);
				
				if(LOG.debug())
					LOG.debug("Bootstrap root `{0}` resolved `{1}` to `{2}`", path, gav, art);
				
				if(Files.exists(art)) {
					
					if(LOG.debug())
						LOG.debug("Exists! Using `{0}` for `{1}`", art, gav);
					
					return Optional.of(ResolutionResult.of(art.toUri()));
				}
				
			}
		}
		
		if(LOG.debug())
			LOG.debug("Artifact not in bootstrap repository.");
			

		return Optional.empty();
	}

	protected Path resolveGav(Path root, GAV gav) {
		return root.resolve(LocalRepository.gavPath(gav));
	}

}