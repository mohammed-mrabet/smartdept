package ma.ump.smartdept;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SmartDept : plateforme intelligente du département informatique.
 * - @EnableAsync : les e-mails partent en arrière-plan, sans ralentir les requêtes ;
 * - @EnableScheduling : rappels automatiques des échéances PFE.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class SmartDeptApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartDeptApplication.class, args);
    }
}
