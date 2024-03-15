package com.sshtools.bootlace.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.Comparator;

public final class FilesAndFolders {

	public static void recursiveDelete(Path fileOrDirectory, FileVisitOption... options) {
		try (var walk = Files.walk(fileOrDirectory, options)) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.delete(p);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		} 
		catch(IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}
	
	public static void copy(Path source, Path destinationDir) {
		try {
			if(!Files.isDirectory(destinationDir))
				throw new IllegalArgumentException(MessageFormat.format("Destination ''{0}'' must be a directory.", destinationDir));
				var destination = destinationDir.resolve(source.getFileName());
			if(Files.isDirectory(source)) {
				copyDirectory(source, destination);
			}
			else {
				Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
			}
		}
		catch(IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}
	
	
	public static void copyDirectory(Path sourceDirectory, Path destinationDirectory) {
		try {
			Files.walk(sourceDirectory).forEach(source -> {
				Path destination = destinationDirectory.resolve(sourceDirectory.relativize(source));
				try {
					Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
		catch(IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}
}
