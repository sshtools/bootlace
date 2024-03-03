package com.sshtools.bootlace.api;

import java.io.InputStream;
import java.net.URI;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class SnapshotMetaData {

	private static final String TIMESTAMP_FMT = "yyyyMMdd.HHmmss";
	private static final String UPDATED_FMT = "yyyyMMddHHmmss";

	public record Snapshot(Instant timestamp, int buildNumber) {
	}

	public record SnapshotVersionKey(String extension, String classifier) {
	}

	public record SnapshotVersion(String value, Instant updated) {
	}

	public static SnapshotMetaData of(InputStream in) {
		return new SnapshotMetaData(in);
	}

	private final GAV gav;
	private final Snapshot snapshot;
	private final Instant updated;
	private final Map<SnapshotVersionKey, SnapshotVersion> versions = new HashMap<>();

	private SnapshotMetaData(InputStream in) {

		var project = XML.of(in);

		/* GAV */
		var artifactId = project.value("artifactId")
				.orElseThrow(() -> new IllegalArgumentException("Meta-data has no artifactId"));
		var groupId = project.value("groupId")
				.orElseThrow(() -> new IllegalArgumentException("Meta-data has no groupId"));
		gav = GAV.ofParts(groupId, artifactId);

		var versioning = project.child("versioning").get();
		versioning.dumpEl();;
		try {
			updated = new SimpleDateFormat(UPDATED_FMT).parse(versioning.value("lastUpdated").get()).toInstant();

			var shapshotXml = versioning.child("snapshot").get();
			var fmt = new SimpleDateFormat(TIMESTAMP_FMT);
			var date = fmt.parse(shapshotXml.value("timestamp").get());
			snapshot = new Snapshot(date.toInstant(), Integer.parseInt(shapshotXml.value("buildNumber").get()));

			for (var snapshotVerXml : versioning.child("snapshotVersions").get().children()) {
				var classifier = snapshotVerXml.value("classifier").orElse("");
				var extension = snapshotVerXml.value("extension").orElse("");
				versions.put(new SnapshotVersionKey(extension, classifier), new SnapshotVersion(
						snapshotVerXml.value("value").get(),
						new SimpleDateFormat(UPDATED_FMT).parse(snapshotVerXml.value("updated").get()).toInstant()));
			}
		} catch (ParseException e) {
			throw new IllegalArgumentException("Could not parse timestamp.");
		}

	}

	public GAV gav() {
		return gav;
	}

	public Snapshot snapshot() {
		return snapshot;
	}

	public Instant updated() {
		return updated;
	}

	public SnapshotVersion get(String extension) {
		return get("", extension);
	}
	public SnapshotVersion get(String classifier, String extension) {
		var ver = versions.get(new SnapshotVersionKey(extension, classifier));
		if(ver == null) {
			throw new IllegalArgumentException(MessageFormat.format("No such classifer / extension as {} / {}", classifier == null ? "<none>" : classifier, extension));
		}
		return ver;
	}

	public String latestJarFilename() {
		return String.format("%s-%s.jar", gav.artifactId(), latestJarVersion());
	}

	public String latestJarVersion() {
		return get("", "jar").value();
	}
}
