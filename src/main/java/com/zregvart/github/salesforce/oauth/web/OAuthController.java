package com.zregvart.github.salesforce.oauth.web;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Controller
@RequestMapping("/auth")
public class OAuthController {

    @Value("${camel.component.salesforce.loginConfig.clientId}")
    String clientId;

    @Value("${camel.component.salesforce.loginConfig.clientSecret}")
    String clientSecret;

    final OkHttpClient httpClient = new OkHttpClient();

    final OpenShiftClient k8sClient = new DefaultOpenShiftClient();

    final ObjectReader reader;

    public OAuthController() {
        final ObjectMapper mapper = new ObjectMapper();

        reader = mapper.readerFor(new TypeReference<Map<String, ?>>() {
        });
    }

    static String decode(final String urlencoded) {
        try {
            return URLDecoder.decode(urlencoded, "ASCII");
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @GetMapping(path = "/code")
    public String create(@RequestParam final String code, final HttpServletRequest request,
            final RedirectAttributes redirectAttributes) throws Exception {
        final String callbackUrl = request.getRequestURL().toString();

        final RequestBody tokenRequestBody = new FormBody.Builder().add("grant_type", "authorization_code")
                .add("client_id", clientId).add("client_secret", clientSecret).add("redirect_uri", callbackUrl)
                .add("code", code).build();

        final Request tokenRequest = new Request.Builder().url("https://login.salesforce.com/services/oauth2/token")
                .addHeader("Accept", "application/json").post(tokenRequestBody).build();

        final Response tokenResponse = httpClient.newCall(tokenRequest).execute();

        final Map<String, String> tokens = reader.readValue(tokenResponse.body().charStream());

        final Request infoRequest = new Request.Builder().url(tokens.get("id"))
                .addHeader("Authorization", "Bearer " + tokens.get("access_token")).build();
        final Response infoResponse = httpClient.newCall(infoRequest).execute();

        final Map<String, ?> info = reader.readValue(infoResponse.body().charStream());

        final String organizationId = (String) info.get("organization_id");

        final Map<String, String> labels = new HashMap<>();
        labels.put("kind", "oauth");
        labels.put("component", "salesforce");
        labels.put("organization_id", organizationId);

        final String secretName = "salesforce." + organizationId.toLowerCase();
        final Map<String, String> secretData = Collections
                .singletonMap("camel.component.salesforce.loginConfig.refreshToken", tokens.get("refresh_token"));

        k8sClient.secrets().createOrReplaceWithNew().withNewMetadata().withName(secretName).addToLabels(labels).and()
                .addToStringData(secretData).done();

        redirectAttributes.addFlashAttribute("display_name", info.get("display_name"))
                .addFlashAttribute("thumbnail", info.get("thumbnail"))
                .addFlashAttribute("organizationId", organizationId);

        return "redirect:/success";
    }

}
