package springtest.mod1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import springtest.api.ReplyProducer;
import springtest.api.SharedService;

@Component
public class Mod1 implements ReplyProducer {
	
	@Autowired
	private SharedService sharedService;
	
	@PostConstruct
	private void setup() {
		System.out.println("Mod1 Constructed");
	}

	@Override
	public String getReply() {
		return "Hello " + sharedService.getUuid() + " From Mod2";
	}

}
