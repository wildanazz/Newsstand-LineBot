package com.newsstand.linebot.controller;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Image {

    private final Thumbnail thumbnail;

    @JsonCreator
    public Image(@JsonProperty("thumbnail") Thumbnail thumbnail) {
        this.thumbnail = thumbnail;
    }

    public Thumbnail getThumbnail() {
        return thumbnail;
    }

    static class Thumbnail {

        private final String contentUrl;
        private final String width;
        private final String height;

        @JsonCreator
        public Thumbnail(
                @JsonProperty("contentUrl") String contentUrl,
                @JsonProperty("width") String width,
                @JsonProperty("height") String height)
        {
            this.contentUrl = contentUrl;
            this.width = width;
            this.height = height;
        }

        public String getContentUrl() {
            return contentUrl;
        }

        public String getWidth() {
            return width;
        }

        public String getHeight() {
            return height;
        }
    }
}
