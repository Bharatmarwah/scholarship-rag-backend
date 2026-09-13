package com.bharat.scholarship_rag_backend.controller;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/api/")
public class ChatController {

    private final OpenAiChatModel openAiChatModel;

    public ChatController(OpenAiChatModel openAiChatModel){
        this.openAiChatModel = openAiChatModel;
    }


    @GetMapping("/chat")
    public String chat(@RequestParam String query) {
        return openAiChatModel.chat(query);
    }

}
