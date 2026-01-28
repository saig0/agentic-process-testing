package io.camunda.dev.frauddetection;

import com.icegreen.greenmail.spring.GreenMailBean;
import com.icegreen.greenmail.util.ServerSetup;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class GreenMailConfig {

    @Bean
    public GreenMailBean greenMailBean() {
        GreenMailBean greenMailBean = new GreenMailBean();
        // Enable both SMTP (to receive) and IMAP (to read)
        greenMailBean.setImapProtocol(true);

        // Pre-configure test users
        greenMailBean.setUsers(Arrays.asList(
                "demo:demo@camunda.com",
                "agent:agent@camunda.com"
        ));

        //greenMailBean.setHostname("127.0.0.1");
        return greenMailBean;
    }
}
