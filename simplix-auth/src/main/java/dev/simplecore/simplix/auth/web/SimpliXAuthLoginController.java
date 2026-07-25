package dev.simplecore.simplix.auth.web;

import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "simplix.auth.security", name = "enable-web-security", havingValue = "true", matchIfMissing = true)
public class SimpliXAuthLoginController {
    
    private final SimpliXAuthProperties properties;
    
    @GetMapping("${simplix.auth.security.login-page-path:/login}")
    public String login(Model model) {
        // The form posts to the configured processing URL: an application that relocates its
        // server-rendered pages moves that URL with them.
        String processingUrl = properties.getSecurity().getLoginProcessingUrl();
        model.addAttribute("loginProcessingUrl", processingUrl.startsWith("/") ? processingUrl : "/" + processingUrl);
        return properties.getSecurity().getLoginPageTemplate();
    }
} 