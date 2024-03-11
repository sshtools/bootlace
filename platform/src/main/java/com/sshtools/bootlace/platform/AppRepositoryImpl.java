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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.sshtools.bootlace.api.AppRepository;
import com.sshtools.bootlace.api.GAV;

public class AppRepositoryImpl extends LocalRepositoryImpl implements AppRepository {
	
	private static class LazyAppRepository {
		static AppRepository DEFAULT = (AppRepository)new AppRepositoryBuilder().build();
	}

	public static AppRepository appRepository() {
		return LazyAppRepository.DEFAULT;
	}

	public final static class AppRepositoryBuilder implements AppRepository.AppRepositoryBuilder {
		private Path root = Paths.get(AppRepository.ID);

		private String name = "App Repository";
		private String pattern = System.getProperty("bootlace.app.pattern", "%G/%a/%v/%a-%v.jar");

		@Override
		public AppRepositoryBuilder withName(String name) {
			this.name = name;
			return this;
		}
		
		@Override
		public AppRepositoryBuilder withPattern(String pattern) {
			this.pattern = pattern;
			return this;
		}

		@Override
		public AppRepositoryBuilder withRoot(String root) {
			return withRoot(Paths.get(root));
		}

		@Override
		public AppRepositoryBuilder withRoot(Path root) {
			this.root = root;
			return this;
		}

		@Override
		public AppRepositoryImpl build() {
			return new AppRepositoryImpl(this);
		}
	}

	private AppRepositoryImpl(AppRepositoryBuilder builder) {
		super(builder.root, builder.name, AppRepository.ID, builder.pattern);
	}

	@Override
	public boolean supported(GAV gav) {
		return gav.repositoryOr().isEmpty() || gav.repository().equals(id());
	}

	@Override
	public Path store(GAV gav, InputStream in) throws IOException {
		var path = resolveGav(gav);
		if (!Files.exists(path.getParent()))
			Files.createDirectories(path.getParent());
		try (var out = Files.newOutputStream(path)) {
			in.transferTo(out);
			return path;
		}
	}

}
