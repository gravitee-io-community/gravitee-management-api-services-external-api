package io.gravitee.management.services.external.api.mulesoft.model;

/**
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MuleSoftApiEntity {
    private MuleSoftAssetEntity spec;
    private MuleSoftEndpointEntity endpoint;
    private String instanceLabel;

    public MuleSoftAssetEntity getSpec() {
        return spec;
    }

    public void setSpec(MuleSoftAssetEntity spec) {
        this.spec = spec;
    }

    public MuleSoftEndpointEntity getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(MuleSoftEndpointEntity endpoint) {
        this.endpoint = endpoint;
    }

    public String getInstanceLabel() {
        return instanceLabel;
    }

    public void setInstanceLabel(String instanceLabel) {
        this.instanceLabel = instanceLabel;
    }
}
