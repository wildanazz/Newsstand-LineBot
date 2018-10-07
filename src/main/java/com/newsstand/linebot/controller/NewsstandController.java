package com.newsstand.linebot.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.client.LineSignatureValidator;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.message.FlexMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.flex.container.FlexContainer;
import com.linecorp.bot.model.message.quickreply.QuickReply;
import com.linecorp.bot.model.message.quickreply.QuickReplyItem;
import com.linecorp.bot.model.objectmapper.ModelObjectMapper;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

@RestController
public class NewsstandController {

    @Autowired
    @Qualifier("lineMessagingClient")
    private LineMessagingClient lineMessagingClient;

    @Autowired
    @Qualifier("lineSignatureValidator")
    private LineSignatureValidator lineSignatureValidator;

    @Autowired
    @Qualifier("Ocp-Apim-Subscription-Key")
    private String apiKey;

    @RequestMapping(value="/webhook", method= RequestMethod.POST)
    public ResponseEntity<String> callback(
            @RequestHeader("X-Line-Signature") String xLineSignature,
            @RequestBody String eventsPayload)
    {
        try {
            if (!lineSignatureValidator.validateSignature(eventsPayload.getBytes(), xLineSignature)) {
                throw new RuntimeException("Invalid Signature Validation");
            }

            ObjectMapper objectMapper = ModelObjectMapper.createNewObjectMapper();
            EventsModel eventsModel = objectMapper.readValue(eventsPayload, EventsModel.class);

            eventsModel.getEvents().forEach((event) -> {
                if (event instanceof MessageEvent) {
                    handleOneOnOneChats((MessageEvent) event);
                }
            });

            return new ResponseEntity<>(HttpStatus.OK);
        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    private void handleOneOnOneChats(MessageEvent event) {
        if (event.getMessage() instanceof TextMessageContent) {
            handleTextMessage(event);
        } else {
            replyText(event.getReplyToken(), "Unknown Message");
        }
    }

    private void handleTextMessage(MessageEvent event) {
        TextMessageContent textMessageContent = (TextMessageContent) event.getMessage();
        if (textMessageContent.getText().equalsIgnoreCase("/topnews")) {
            List<Article> articleList = searchNews(apiKey, textMessageContent.getText(), "/topnews");
            replyFlexMessage(event.getReplyToken(), articleList);
        } else if (textMessageContent.getText().equalsIgnoreCase("/topindo")) {
            List<Article> articleList = searchNews(apiKey, textMessageContent.getText(), "/topindo");
            replyFlexMessage(event.getReplyToken(), articleList);
        } else if (textMessageContent.getText().equalsIgnoreCase("/topworld")) {
            List<Article> articleList = searchNews(apiKey, textMessageContent.getText(), "/topworld");
            replyFlexMessage(event.getReplyToken(), articleList);
        } else if (textMessageContent.getText().equalsIgnoreCase("--help")) {
            System.out.println("Keyword Reply Message");
        }else if (textMessageContent.getText().contains("+")) {
            String searchQuery = textMessageContent.getText().replace("+","");
            List<Article> articleList = searchNews(apiKey, searchQuery, "+");
            replyFlexMessage(event.getReplyToken(), articleList);
        } else {
            List<Article> articleList = searchNews(apiKey, textMessageContent.getText(), "search");
            replyFlexMessage(event.getReplyToken(), articleList);
        }
    }

    private void reply(ReplyMessage replyMessage) {
        try {
            lineMessagingClient.replyMessage(replyMessage).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private void replyText(String replyToken, String messageToUser) {
        TextMessage textMessage = new TextMessage(messageToUser);
        ReplyMessage replyMessage = new ReplyMessage(replyToken, textMessage);
        reply(replyMessage);
    }

    private void replyFlexMessage(String replyToken, List<Article> articleList) {
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            String flexTemplate = IOUtils.toString(classLoader.getResourceAsStream("flex_message.json"));

            ObjectMapper objectMapper = ModelObjectMapper.createNewObjectMapper();
            List<QuickReplyItem> replyItems = quickReplyMessage();
            String newFlexTemplate = writingToJSON(flexTemplate, objectMapper, articleList).toString();

            FlexContainer flexContainer = objectMapper.readValue(newFlexTemplate, FlexContainer.class);
            ReplyMessage replyMessage = new ReplyMessage(replyToken, new FlexMessage("Flex Message", flexContainer).toBuilder().quickReply(QuickReply.items(replyItems)).build());
            reply(replyMessage);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<QuickReplyItem> quickReplyMessage() {
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            String quickReplyTemplate = IOUtils.toString(classLoader.getResourceAsStream("quick_reply.json"));

            ObjectMapper objectMapper = ModelObjectMapper.createNewObjectMapper();
            return objectMapper.readValue(quickReplyTemplate, new TypeReference<List<QuickReplyItem>>(){});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Article> searchNews(String apiKey, String searchQuery, String param) {
        try {
            URL url;
            String host = "https://api.cognitive.microsoft.com";
            String path = "/bing/v7.0/news";
            if (param.equalsIgnoreCase("/topnews")) {
                url = new URL(host + path);
            } else if (param.equalsIgnoreCase("/topindo")) {
                url = new URL(host + path + "/search?q=indonesia");
            } else if (param.equalsIgnoreCase("/topworld")) {
                url = new URL(host + path + "?mkt=en-US&category=World");
            } else if (param.equalsIgnoreCase("+")) {
                url = new URL(host + path + "?mkt=en-US&category=" +  URLEncoder.encode(searchQuery, "UTF-8"));
            } else {
                url = new URL(host + path + "/search?q=" +  URLEncoder.encode(searchQuery, "UTF-8"));
            }
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Ocp-Apim-Subscription-Key", apiKey);
            InputStream stream = connection.getInputStream();
            String response = new Scanner(stream).useDelimiter("\\A").next();

            ObjectMapper objectMapper = ModelObjectMapper.createNewObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(response);
            String articles = jsonNode.get("value").toString();
            List<Article> articleList = objectMapper.readValue(articles, new TypeReference<List<Article>>() {});

            stream.close();
            return articleList;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static JsonNode writingToJSON(String flexTemplate, ObjectMapper objectMapper, List<Article> articleList) {
        try {
            JsonNode rootNode = objectMapper.readTree(flexTemplate);

            int articleCounter = 0;
            for (int i = 0; i < 15 ; i = i + 3) {
                if(articleList.get(articleCounter).getImage() != null) {
                    JsonNode imageNode = rootNode.path("body").path("contents").get(0).path("contents").get(i).path("contents").get(0);
                    ((ObjectNode) imageNode).put("url", articleList.get(articleCounter).getImage().getThumbnail().getContentUrl());
                }
                if(articleList.get(articleCounter).getName() != null) {
                    JsonNode titleNode = rootNode.path("body").path("contents").get(0).path("contents").get(i).path("contents").get(1).path("contents").get(0);
                    ((ObjectNode) titleNode).put("text", articleList.get(articleCounter).getName());
                }
                if(articleList.get(articleCounter).getUrl() != null) {
                    JsonNode sourceNode = rootNode.path("body").path("contents").get(0).path("contents").get(i).path("contents").get(1).path("contents").get(2);
                    ((ObjectNode) sourceNode).put("text", articleList.get(articleCounter).getUrl());

                    JsonNode linkNode = rootNode.path("body").path("contents").get(0).path("contents").get(i).path("contents").get(1).path("contents").get(2).path("action");
                    ((ObjectNode) linkNode).put("uri", articleList.get(articleCounter).getUrl());
                }
                if(articleList.get(articleCounter).getDescription() != null) {
                    JsonNode descriptionNode = rootNode.path("body").path("contents").get(0).path("contents").get(i+1);
                    ((ObjectNode) descriptionNode).put("text", articleList.get(articleCounter).getDescription());
                }
                articleCounter++;
            }

            return rootNode;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}