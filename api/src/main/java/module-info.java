import com.sshtools.bootlace.api.LayerContext;
import com.sshtools.bootlace.api.AppRepository.AppRepositoryBuilder;
import com.sshtools.bootlace.api.LocalRepository.LocalRepositoryBuilder;
import com.sshtools.bootlace.api.PluginContext;
import com.sshtools.bootlace.api.RemoteRepository.RemoteRepositoryBuilder;

module com.sshtools.bootlace.api {
	exports com.sshtools.bootlace.api;
	requires transitive java.net.http;
	requires transitive java.xml;
	uses LocalRepositoryBuilder;
	uses RemoteRepositoryBuilder;
	uses AppRepositoryBuilder;
	uses LayerContext.Provider;
	uses PluginContext.Provider;
}