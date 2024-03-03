package com.sshtools.bootlace.platform;

import java.net.URI;
import java.util.Optional;

import com.sshtools.bootlace.api.Repository;

public record RepositoryDef(Class<? extends Repository> type, String id, String name, URI root, Optional<Boolean> releases, Optional<Boolean> snapshots, Optional<String> pattern) {
}
