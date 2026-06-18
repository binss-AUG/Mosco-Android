package com.vn.jet.mosco.spinserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.StringTokenizer;

@Service
public class SimulatedStreamingService {
    private static final Logger logger = LoggerFactory.getLogger(SimulatedStreamingService.class);

    public SseEmitter streamFakeTyping(String fullText) {
        SseEmitter emitter = new SseEmitter(60000L); // 60s timeout

        new Thread(() -> {
            try {
                if (fullText == null || fullText.isEmpty()) {
                    emitter.complete();
                    return;
                }

                // Simulate thinking delay (already incurred by LLM/Translation, but let's add a small buffer if needed, 
                // actually we don't need to sleep here because the 3s delay from translation already acted as "thinking").
                // We just stream the text token by token.
                
                StringTokenizer tokenizer = new StringTokenizer(fullText, " \n\r\t.,!?:;", true);
                
                while (tokenizer.hasMoreTokens()) {
                    String token = tokenizer.nextToken();
                    if (!token.isEmpty()) {
                        emitter.send(SseEmitter.event().data(Map.of("text", token), MediaType.APPLICATION_JSON));
                        // 30ms delay per token to simulate fast typing
                        Thread.sleep(30);
                    }
                }
                
                emitter.complete();
            } catch (Exception e) {
                logger.error("Error in fake streaming: {}", e.getMessage());
                try {
                    emitter.completeWithError(e);
                } catch (Exception ex) {}
            }
        }).start();

        return emitter;
    }
}
