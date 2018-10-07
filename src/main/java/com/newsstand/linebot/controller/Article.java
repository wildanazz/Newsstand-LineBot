package com.newsstand.linebot.controller;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Article {

    private final String name;
    private final String url;
    private final Image image;
    private final String description;

    @JsonCreator
    public Article(
            @JsonProperty("name") String name,
            @JsonProperty("url") String url,
            @JsonProperty("image") Image image,
            @JsonProperty("description") String description)
    {
        this.name = name;
        this.url = url;
        this.image = image;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public Image getImage() {
        return image;
    }

    public String getDescription() {
        return description;
    }
}
