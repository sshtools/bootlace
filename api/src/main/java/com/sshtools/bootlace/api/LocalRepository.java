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