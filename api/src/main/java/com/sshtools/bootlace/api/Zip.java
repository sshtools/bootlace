package com.sshtools.bootlace.api;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Zip {
	
	public static boolean isArchive(ZipEntry entry) {
		return entry.getName().toLowerCase().endsWith(".jar") || entry.getName().toLowerCase().endsWith(".zip");
	}
	
	public static Stream<ZipEntry> list(Path file) throws IOException {
		return list(Files.newInputStream(file));
	}
	
	public static Stream<ZipEntry> list(InputStream in) throws IOException {
		var zis = new ZipInputStream(in);
		var it = new Iterator<ZipEntry>() {
			ZipEntry zipEntry;

			@Override
			public boolean hasNext() {
				checkNext();
				return zipEntry != null;
			}

			@Override
			public ZipEntry next() {
				checkNext();
				try {
					return zipEntry;
				} finally {
					zipEntry = null;
				}
			}

			private void checkNext() {
				if (zipEntry == null) {
					try {
						zipEntry = zis.getNextEntry();
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				}
			}
		};
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED), false).onClose(() -> {
			try {
				zis.close();
			} catch (IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
		});

	}

	public static InputStream find(Path file, String path) throws IOException {
		var zis = new ZipInputStream(Files.newInputStream(file));
		ZipEntry zipEntry;
		try {
			zipEntry = zis.getNextEntry();
		} catch (RuntimeException | IOException ioe) {
			zis.close();
			throw ioe;
		}
		while (zipEntry != null) {
			if (zipEntry.getName().equals(path)) {
				return zis;
			}
			try {
				zipEntry = zis.getNextEntry();
			} catch (RuntimeException | IOException ioe) {
				zis.close();
				throw ioe;
			}
		}
		try {
			zis.closeEntry();
		} finally {
			zis.close();
		}
		throw new NoSuchFileException(file.toString() + "/" + path);
	}

	public static <R> Optional<R> unzip(Path file, BiFunction<ZipEntry, InputStream, Optional<R>> visitor)
			throws IOException {
		return unzip(file, visitor, f -> true);
	}

	public static <R> Optional<R> unzip(Path file, BiFunction<ZipEntry, InputStream, Optional<R>> visitor,
			Function<ZipEntry, Boolean> filter) throws IOException {
		try (var zis = Files.newInputStream(file)) {
			return unzip(zis, visitor, filter);
		}
	}

	public static <R> Optional<R> unzip(InputStream in, BiFunction<ZipEntry, InputStream, Optional<R>> visitor,
			Function<ZipEntry, Boolean> filter) throws IOException {
		var zis = new ZipInputStream(in);
		ZipEntry zipEntry = zis.getNextEntry();
		while (zipEntry != null) {
			if(filter.apply(zipEntry)) {
				try {
					var res = visitor.apply(zipEntry, zis);
					if (res.isPresent())
						return res;
				}
				catch(UncheckedIOException ucio) {
					throw ucio.getCause();
				}
			}
			zipEntry = zis.getNextEntry();
		}
		zis.closeEntry();
		return Optional.empty();
	}

	public static void unzip(Path file, Path destination) throws IOException {
		try (var in = Files.newInputStream(file)) {
			unzip(in, destination);
		}
	}

	public static void unzip(InputStream in, Path destination) throws IOException {
		var zis = new ZipInputStream(in);
		ZipEntry zipEntry = zis.getNextEntry();
		while (zipEntry != null) {
			var newFile = newFile(destination, zipEntry);
			if (zipEntry.isDirectory()) {
				if (!Files.isDirectory(newFile)) {
					Files.createDirectories(newFile);
				}
			} else {
				var parent = newFile.getParent();
				if (!Files.isDirectory(parent)) {
					Files.createDirectories(parent);
				}

				try (var fos = Files.newOutputStream(newFile)) {
					zis.transferTo(fos);
				}
			}
			zipEntry = zis.getNextEntry();
		}
		zis.closeEntry();
	}

	private static Path newFile(Path destinationDir, ZipEntry zipEntry) throws IOException {
		var destFile = destinationDir.resolve(zipEntry.getName());

		var destDirPath = Files.exists(destinationDir) ? destinationDir.toRealPath().toString()
				: destinationDir.toAbsolutePath().toString();
		var destFilePath = Files.exists(destFile) ? destFile.toRealPath().toString()
				: destFile.toAbsolutePath().toString();

		if (!destFilePath.startsWith(destDirPath + File.separator)) {
			throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
		}

		return destFile;
	}
}
