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

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Represents the coordinates to a (Maven) artifact in a local or remote repository.
 */
public final class GAV {

	public final static class Builder {
		private Optional<String> repository = Optional.empty();
		private Optional<String> groupId = Optional.empty();
		private String artifactId;
		private Optional<String> version = Optional.empty();
		private Optional<String> classifier = Optional.empty();

		public Builder() {
		}

		public Builder withArtifactId(String artifactId) {
			this.artifactId = artifactId;
			return this;
		}

		public Builder ofProperties(Properties properties) {
			groupId = Optional.ofNullable(properties.getProperty("groupId"));
			artifactId = Optional.ofNullable(properties.getProperty("artifactId"))
					.orElseThrow(() -> new IllegalArgumentException("No artifact ID"));
			classifier = Optional.ofNullable(properties.getProperty("classifier "));
			version = Optional.ofNullable(properties.getProperty("version"));
			return this;
		}

		public Builder ofSpec(String str) {
			return ofParts(str.split(":"));
		}

		public Builder ofParts(Collection<String> parts) {
			try {
				var it = parts.iterator();
				var first = it.next();
				if (first.startsWith("@")) {
					withRepository(first.substring(1));
					it.next();
				} else {
					withGroupId(first);
				}
				withArtifactId(it.next());
				if (it.hasNext())
					withVersion(it.next());
				if (it.hasNext())
					withClassifier(it.next());
			} catch (NoSuchElementException nse) {
				throw new IllegalArgumentException(MessageFormat.format(
						"Truncated GAV string ''{0}'', must be in format [@repository]:groupId:artifactId[:version][:classifier]. groupId may be empty, i.e. '':artifactId:...'' or ''repository::artifact:...''",
						String.join(":", parts)));
			}

			return this;
		}

		public Builder ofParts(String... parts) {
			return ofParts(Arrays.asList(parts));
		}

		public Builder withRepository(String repository) {
			return withRepository(repository.equals("") ? Optional.empty() : Optional.ofNullable(repository));
		}

		public Builder withRepository(Optional<String> repository) {
			this.repository = repository;
			return this;
		}

		public Builder withGroupId(String groupId) {
			return withGroupId(groupId.equals("") ? Optional.empty() : Optional.ofNullable(groupId));
		}

		public Builder withGroupId(Optional<String> groupId) {
			this.groupId = groupId;
			return this;
		}

		public Builder withVersion(String version) {
			return withVersion(version.equals("") ? Optional.empty() : Optional.ofNullable(version));
		}

		public Builder withVersion(Optional<String> version) {
			this.version = version;
			return this;
		}

		public Builder withClassifier(String classifier) {
			return withClassifier(classifier.equals("") ? Optional.empty() : Optional.ofNullable(classifier));
		}

		public Builder withClassifier(Optional<String> classifier) {
			this.classifier = classifier;
			return this;
		}

		public GAV build() {
			return new GAV(repository, groupId, artifactId, version, classifier);
		}
	}

	public final static GAV ofSpec(String spec) {
		return new Builder().ofSpec(spec).build();
	}

	public final static GAV ofParts(String... parts) {
		return new Builder().ofParts(parts).build();
	}

	public final static GAV ofProperties(Properties properties) {
		return new Builder().ofProperties(properties).build();
	}

	private final Optional<String> repository;
	private final Optional<String> groupId;
	private final String artifactId;
	private final Optional<String> version;
	private final Optional<String> classifier;

	private GAV(Optional<String> repository, Optional<String> groupId, String artifactId, Optional<String> version,
			Optional<String> classifier) {
		super();
		this.repository = repository;
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.version = version;
		this.classifier = classifier;
	}

	public Optional<String> repositoryOr() {
		return repository;
	}

	public String repository() {
		return repository.orElseThrow(() -> new IllegalStateException("This GAV has no repository"));
	}

	public Optional<String> groupIdOr() {
		return groupId;
	}

	public String artifactId() {
		return artifactId;
	}

	public Optional<String> versionOr() {
		return version;
	}

	public Optional<String> classifierOr() {
		return classifier;
	}

	public String version() {
		return version.orElseThrow(() -> new IllegalStateException(MessageFormat.format("This GAV ''{0}'' has no version", this)));
	}

	public String classifier() {
		return classifier.orElseThrow(() -> new IllegalStateException(MessageFormat.format("This GAV ''{0}'' has no classifier", this)));
	}

	public String toString() {
		var b = new StringBuilder();
		repository.ifPresent((r) -> b.append("@" + r + ":"));
		b.append(groupId.orElse(""));
		b.append(":");
		b.append(artifactId);
		version.ifPresent(v -> {
			b.append(':');
			b.append(v);
			classifier.ifPresent(c -> {
				b.append(':');
				b.append(c);
			});
		});
		return b.toString();
	}

	public String groupId() {
		return groupId.orElseThrow(() -> new IllegalStateException(MessageFormat.format("This GAV ''{0}'' has no group", this)));
	}

	@Override
	public int hashCode() {
		return Objects.hash(artifactId, classifier, groupId, repository, version);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		GAV other = (GAV) obj;
		return Objects.equals(artifactId, other.artifactId) && Objects.equals(classifier, other.classifier)
				&& Objects.equals(groupId, other.groupId) && Objects.equals(repository, other.repository)
				&& Objects.equals(version, other.version);
	}
	
	public boolean isSnapshot() {
		return version.isPresent() && (version.get().endsWith("-SNAPSHOT") || version.get().contains("-SNAPSHOT-") );
	}

	public boolean hasVersion() {
		return version.isPresent();
	}

	public boolean hasGroupId() {
		return groupId.isPresent();
	}

	public boolean hasClassifier() {
		return classifier.isPresent();
	}

	public boolean hasRepository() {
		return repository.isPresent();
	}

	public GAV toWithoutVersion() {
		return new GAV(repository, groupId, artifactId, Optional.empty(), classifier);
	}

	public GAV toWithVersion(String version) {
		return toWithVersion(Optional.of(version));
	}

	public GAV toWithVersion(Optional<String> version) {
		return new GAV(repository, groupId, artifactId, version, classifier);
	}

	public GAV toWithoutRepository() {
		return new GAV(Optional.empty(), groupId, artifactId, version, classifier);
	}

	public boolean isResolved() {
		return !isSnapshot() || (isSnapshot() && !version().endsWith("-SNAPSHOT"));
	}

}
