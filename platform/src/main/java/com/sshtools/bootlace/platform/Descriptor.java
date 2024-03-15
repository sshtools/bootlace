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
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.sshtools.bootlace.api.Exceptions;
import com.sshtools.bootlace.api.Zip;
import com.sshtools.jini.INI;
import com.sshtools.jini.INI.Section;

public final class Descriptor {

	public final static class Builder {
		private INI ini = INI.create();

		public Builder fromINI(INI ini) {
			this.ini = ini;
			return this;
		}

		public Builder fromArtifact(Path path) throws IOException {
			if(Files.isDirectory(path)) {
				try {
					return fromINI(Bootlace.createINIReader().build().read(path.resolve(DESCRIPTOR_RESOURCE_NAME)));
				} catch(NoSuchFileException nsfe) {
					throw new Exceptions.NotALayer(path);
				} catch (ParseException e) {
					throw new IllegalStateException("Failed to parse.", e);
				}
			}
			else {
				return fromINI(loadDescriptor(path));
			}
		}

		private INI loadDescriptor(Path path) throws IOException {
			try (var in = Files.newInputStream(path)) {
				return loadDescriptor(in).orElseThrow(() -> new Exceptions.NotALayer(path));
			}
		}

		private Optional<INI> loadDescriptor(InputStream in) throws IOException {
			return Zip.unzip(in, (entry, zin) -> {
				if (Zip.isArchive(entry)) {
					try {
						return loadDescriptor(zin);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				} else {
					try {
						return Optional.of(Bootlace.createINIReader().build().read(new InputStreamReader(zin)));
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					} catch (ParseException e) {
						throw new IllegalStateException("Failed to parse descriptor.", e);
					}
				}
			}, f -> Zip.isArchive(f) || f.getName().equals(DESCRIPTOR_RESOURCE_NAME));
		}

		public Descriptor build() {
			return new Descriptor(this);
		}
	}

	public static final String DESCRIPTOR_RESOURCE_NAME = "layers.ini";

	private final Section component;
	private final String id;
	private final List<Section> children;
	private final Optional<Section> artifacts;
	private final Optional<Section> repositories;
	private final Optional<Section> meta;

	private Descriptor(Builder bldr) {
		component = bldr.ini.section("component");
		artifacts = bldr.ini.sectionOr("artifacts");
		meta = bldr.ini.sectionOr("meta");
		id = component.get("id");
		children = Arrays.asList(bldr.ini.sectionOr("layer").map(l -> l.allSections()).orElse(new Section[0]));
		repositories = bldr.ini.sectionOr("repository");
	}

	public List<Section> layers() {
		return children;
	}

	public Section component() {
		return component;
	}

	public String id() {
		return id;
	}
	
	public Optional<Section> repositories() {
		return repositories;
	}
	
	public Optional<Section> meta() {
		return meta;
	}
	
	public Optional<Section> repository(String id) {
		return repositories.map(sec -> sec.section(id));
	}
	
	public Optional<Section> artifacts() {
		return artifacts;
	}
}
