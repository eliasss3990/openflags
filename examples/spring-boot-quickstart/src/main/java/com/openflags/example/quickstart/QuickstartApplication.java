package com.openflags.example.quickstart;

import com.openflags.core.OpenFlagsClient;
import com.openflags.core.evaluation.EvaluationContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class QuickstartApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuickstartApplication.class, args);
    }

    @RestController
    static class CheckoutController {

        private final OpenFlagsClient flags;

        CheckoutController(OpenFlagsClient flags) {
            this.flags = flags;
        }

        @GetMapping("/checkout")
        String checkout(@RequestParam(defaultValue = "anonymous") String user,
                        @RequestParam(defaultValue = "AR") String country) {
            EvaluationContext ctx = EvaluationContext.builder()
                    .targetingKey(user)
                    .attribute("country", country)
                    .build();

            boolean newCheckout = flags.getBooleanValue("new-checkout", false, ctx);
            String banner = flags.getStringValue("checkout-banner", "Welcome", ctx);

            return (newCheckout ? "[v2] " : "[v1] ") + banner + " — user=" + user + ", country=" + country;
        }
    }
}
