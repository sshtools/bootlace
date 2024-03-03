package com.sshtools.bootlace.platform;

import java.net.URI;
import java.util.Optional;

public record RemoteRepositoryDef(String id, String name, URI root, Optional<Boolean> releases, Optional<Boolean> snapshots) {
}
