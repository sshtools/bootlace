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

import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Builder;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Http utilities.
 */
public class Http {
	
	private final static class LazyHttpClientFactory {
		private final static HttpClientFactory DEFAULT = new HttpClientFactory() {
			
			@Override
			public Builder get() {
				return createDefaultHttpClientBuilder();
			}
		};
	}
	
	public interface HttpClientFactory extends Supplier<HttpClient.Builder> {
		
	}
	
	public static Optional<Long> contentLength(HttpResponse<?> response) {
		var length = response.headers().firstValueAsLong("Content-Length");
		return length.isEmpty() ? Optional.empty() : Optional.of(length.getAsLong());
	}
	
	public static HttpClientFactory defaultClientFactory() {
		return LazyHttpClientFactory.DEFAULT;
	}

	public static HttpClient.Builder createDefaultHttpClientBuilder() {
		return HttpClient.newBuilder().
				version(HttpClient.Version.HTTP_1_1).
				connectTimeout(Duration.ofSeconds(10));
	}

	public static String urlEncode(String val) {
		try {
			return URLEncoder.encode(val, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new UncheckedIOException(e);
		}
	}
}
