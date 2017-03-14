package com.zregvart.github.salesforce.oauth.web;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.SecretVolumeSource;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;

@Controller
@RequestMapping("/launch")
public class LaunchController {

    final OpenShiftClient k8sClient = new DefaultOpenShiftClient();

    @GetMapping(path = "{organizationId}")
    public String launch(@PathVariable final String organizationId) {
        final String ipaasAppReference = k8sClient.imageStreamTags().withName("salesforce-ipass-app:latest").get()
                .getImage().getDockerImageReference();

        final String lowerOrganizationId = organizationId.toLowerCase();

        final Map<String, String> labels = new HashMap<>();
        labels.put("salesforce", "true");
        labels.put("organizationId", organizationId);

        final Volume instanceSecret = new Volume();
        instanceSecret.setName("salesforce-connector-oauth");
        final SecretVolumeSource instanceSecretVolumeSource = new SecretVolumeSource();
        instanceSecretVolumeSource.setSecretName("salesforce." + lowerOrganizationId);
        instanceSecret.setSecret(instanceSecretVolumeSource);

        final Volume salesforceSecret = new Volume();
        salesforceSecret.setName("salesforce-ipaas-setup");
        final SecretVolumeSource salesforceSecretVolumeSource = new SecretVolumeSource();
        salesforceSecretVolumeSource.setSecretName("salesforce.ipaas.setup");
        salesforceSecret.setSecret(salesforceSecretVolumeSource);

        final Container container = new Container();
        container.setName("spring-boot");
        container.setImage(ipaasAppReference);

        final VolumeMount instanceSecretVolumeMount = new VolumeMount("/var/run/secrets/salesforce-connector",
                "salesforce-connector-oauth", true, null);
        final VolumeMount salesforceSecretVolumeMount = new VolumeMount("/var/run/secrets/salesforce",
                "salesforce-ipaas-setup", true, null);
        container.setVolumeMounts(Arrays.asList(instanceSecretVolumeMount, salesforceSecretVolumeMount));

        container.setEnv(Collections.singletonList(new EnvVar("JAVA_ENABLE_DEBUG", "true", null)));

        final PodBuilder podBuilder = new PodBuilder(true);
        final String podName = "salesforce-ipass-app-" + lowerOrganizationId;
        podBuilder.withNewMetadata().withName(podName).addToLabels(labels).endMetadata()//
                .withNewSpec().withVolumes(salesforceSecret, instanceSecret).withContainers(container)
                .withServiceAccount("salesforce-oauth-webapp").withServiceAccountName("salesforce-oauth-webapp")
                .endSpec();

        k8sClient.pods().withName(podName).delete();

        final Pod pod = podBuilder.build();
        final Pod newPod = k8sClient.pods().createOrReplace(pod);

        return "redirect:/launch/" + newPod.getMetadata().getName() + "/log";
    }

    @GetMapping(path = "{podName}/log")
    public void log(@PathVariable final String podName, final HttpServletResponse response) throws IOException {
        response.setHeader("Content-Type", "text/plain");

        k8sClient.pods().withName(podName).watchLog(response.getOutputStream());
    }
}
