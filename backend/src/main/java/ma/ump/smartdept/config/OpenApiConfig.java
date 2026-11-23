package ma.ump.smartdept.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI : http://localhost:8080/swagger-ui.html (bouton Authorize = jeton JWT). */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "Bearer JWT";

    @Bean
    public OpenAPI smartDeptOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartDept API")
                        .version("1.0.0")
                        .description("Plateforme intelligente du département informatique : PFE (sujets, candidatures, "
                                + "affectation, jalons, soutenances, conventions), notifications (application et e-mail) "
                                + "et assistant IA. Connectez-vous avec POST /api/auth/login puis cliquez sur Authorize."))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
