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

import java.net.URI;
import java.util.Optional;

import com.sshtools.bootlace.api.Http.HttpClientFactory;

public interface Repository {
	

	public interface RepositoryBuilder<BLDR extends RepositoryBuilder<?, REPO>, REPO> {
		
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
