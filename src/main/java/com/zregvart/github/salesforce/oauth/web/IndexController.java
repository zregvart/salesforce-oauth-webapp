package com.zregvart.github.salesforce.oauth.web;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/")
public class IndexController {

    @Value("${camel.component.salesforce.loginConfig.clientId}")
    String clientId;

    static String createSalesforceLoginUrl(final HttpServletRequest request, final String clientId) {
        final StringBuffer codeUrl = request.getRequestURL().append("auth/code");

        return String.format(
                "https://login.salesforce.com/services/oauth2/authorize?response_type=code&client_id=%s&redirect_uri=%s",
                clientId, codeUrl);
    }

    @GetMapping
    public ModelAndView index(final HttpServletRequest request) {
        return new ModelAndView("index", "salesforce_url", createSalesforceLoginUrl(request, clientId));
    }
}
