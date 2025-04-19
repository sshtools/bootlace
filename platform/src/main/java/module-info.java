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
import com.sshtools.bootlace.api.AppRepository;
import com.sshtools.bootlace.api.AppRepository.AppRepositoryBuilder;
import com.sshtools.bootlace.api.LayerContext;
import com.sshtools.bootlace.api.LocalRepository;
import com.sshtools.bootlace.api.LocalRepository.LocalRepositoryBuilder;
import com.sshtools.bootlace.api.Plugin;
import com.sshtools.bootlace.api.PluginContext;
import com.sshtools.bootlace.api.RemoteRepository;
import com.sshtools.bootlace.api.RemoteRepository.RemoteRepositoryBuilder;
import com.sshtools.bootlace.platform.AppRepositoryImpl;
import com.sshtools.bootlace.platform.LayerContextImpl;
import com.sshtools.bootlace.platform.LocalRepositoryImpl;
import com.sshtools.bootlace.platform.PluginContextProviderImpl;
import com.sshtools.bootlace.platform.RemoteRepositoryImpl;

module com.sshtools.bootlace.platform {
	requires transitive com.sshtools.bootlace.api;
	requires transitive com.sshtools.jini;
	exports com.sshtools.bootlace.platform;
	requires transitive java.net.http;
	uses Plugin;
	uses LocalRepositoryBuilder;
	uses RemoteRepositoryBuilder;
	uses AppRepositoryBuilder;
	
	uses LayerContext.Provider;
	provides LayerContext.Provider with LayerContextImpl.Provider;
	provides PluginContext.Provider with PluginContextProviderImpl;
	
	provides LocalRepository.LocalRepositoryBuilder with 
		LocalRepositoryImpl.LocalRepositoryBuilder;
	provides AppRepository.AppRepositoryBuilder with
		AppRepositoryImpl.AppRepositoryBuilder;
	provides RemoteRepository.RemoteRepositoryBuilder with
		RemoteRepositoryImpl.RemoteRepositoryBuilder;
	
}