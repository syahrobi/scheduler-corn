package id.teravin.ifc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableScheduling
@SpringBootApplication
@EnableTransactionManagement
public class IntegratedFceChannelApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntegratedFceChannelApplication.class, args);
    }

}
