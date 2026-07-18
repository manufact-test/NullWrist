package com.manufacttest.pebblereardisplay.model;

import java.util.Collections;
import java.util.List;

public final class WatchfaceMetadata {
    private final String storageId;
    private final String name;
    private final String author;
    private final String version;
    private final String uuid;
    private final List<String> platforms;
    private final List<String> capabilities;
    private final boolean hasPhoneJavaScript;
    private final boolean bundled;

    public WatchfaceMetadata(
            String storageId,
            String name,
            String author,
            String version,
            String uuid,
            List<String> platforms,
            List<String> capabilities,
            boolean hasPhoneJavaScript,
            boolean bundled
    ) {
        this.storageId = storageId;
        this.name = name;
        this.author = author;
        this.version = version;
        this.uuid = uuid;
        this.platforms = Collections.unmodifiableList(platforms);
        this.capabilities = Collections.unmodifiableList(capabilities);
        this.hasPhoneJavaScript = hasPhoneJavaScript;
        this.bundled = bundled;
    }

    public String getStorageId() { return storageId; }
    public String getName() { return name; }
    public String getAuthor() { return author; }
    public String getVersion() { return version; }
    public String getUuid() { return uuid; }
    public List<String> getPlatforms() { return platforms; }
    public List<String> getCapabilities() { return capabilities; }
    public boolean hasPhoneJavaScript() { return hasPhoneJavaScript; }
    public boolean isBundled() { return bundled; }

    public String platformLabel() {
        return platforms.isEmpty() ? "platform not declared" : String.join(", ", platforms);
    }
}
