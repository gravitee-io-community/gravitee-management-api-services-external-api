package io.gravitee.management.services.external.api.mulesoft.model;

/**
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MuleSoftEndpointEntity {
    private String uri;
    private String proxyUri;
    private Boolean isCloudHub;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getProxyUri() {
        return proxyUri;
    }

    public void setProxyUri(String proxyUri) {
        this.proxyUri = proxyUri;
    }

    public Boolean getIsCloudHub() {
        return isCloudHub;
    }

    public void setIsCloudHub(Boolean isCloudHub) {
        isCloudHub = isCloudHub;
    }
}
