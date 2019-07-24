package io.gravitee.management.services.external.api.mulesoft.model;

import java.util.List;

/**
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MuleSoftAssetEntity {
    private String id;
    private String organizationId;
    private String groupId;
    private String assetId;
    private String version;
    private String name;
    private String classifier;
    private String apiVersion;
    private List<MuleSoftAssetEntity> instances;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getClassifier() {
        return classifier;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public List<MuleSoftAssetEntity> getInstances() {
        return instances;
    }

    public void setInstances(List<MuleSoftAssetEntity> instances) {
        this.instances = instances;
    }
}
