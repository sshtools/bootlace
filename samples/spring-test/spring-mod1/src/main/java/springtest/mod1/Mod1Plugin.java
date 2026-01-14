package springtest.mod1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.sshtools.bootlace.api.Plugin;
import com.sshtools.bootlace.api.PluginContext;

@Component
public class Mod1Plugin implements Plugin {
	
	@Autowired
	private Mod1 mod1;

	@Override
	public void afterOpen(PluginContext context) throws Exception {
		System.out.println("Opened plugin Mod1 - " + mod1.getReply());
	}

}
