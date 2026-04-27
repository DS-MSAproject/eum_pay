package com.eum.reviewserver.entity;

public class ReviewMediaPayload {

    private String url;
    private String mediaType;

    public ReviewMediaPayload() {
    }

    public ReviewMediaPayload(String url, String mediaType) {
        this.url = url;
        this.mediaType = mediaType;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }
}
