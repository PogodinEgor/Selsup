package org.example;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.*;


@Getter
@Setter
public class CrptApi {

    private final static String URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    private final ScheduledExecutorService scheduled = Executors.newScheduledThreadPool(1);
    private ExecutorService executorService = Executors.newCachedThreadPool();
    private final Semaphore requestSemaphore;
    private static ObjectMapper objectMapper = new ObjectMapper();
    private TimeUnit timeUnit;
    private int initialDelay;

    public CrptApi(int initialDelay, TimeUnit timeUnit, int requestLimit) {
        this.requestSemaphore = new Semaphore(requestLimit);
        this.timeUnit = timeUnit;
        this.initialDelay = initialDelay;
    }

    public void createDocument(Document document, String signature) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }
        if (signature == null || signature.trim().isEmpty()) {
            throw new IllegalArgumentException("Signature cannot be null or empty");
        }
        executorService.submit(() ->

        {
            try {
                requestSemaphore.acquire();
                sendDocument(document, signature);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });


    }

    private void sendDocument(Document document, String signature) throws InterruptedException, IOException {
        scheduled.schedule(() -> {

            String requestBody = null;
            try {
                requestBody = objectMapper.writeValueAsString(document);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(URL))
                    .header("Content-Type", "application/json")
                    .header("Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = null;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            int statusCode = response.statusCode();
            if (statusCode == 200) {
                System.out.println(response.body());  // Возвращаем строку в виде документа
            } else {
                System.out.println("Ошибка запроса: " + statusCode);
            }

            requestSemaphore.release();

        },getInitialDelay(), getTimeUnit());

    }


    @Data
    @NoArgsConstructor
    public static class Document {
        private Description description;
        private String doc_id;
        private String doc_status;
        private DocType doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<Product> products;
        private String reg_date;
        private String reg_number;
    }

    public enum DocType {
        LP_INTRODUCE_GOODS
    }

    @Data
    @NoArgsConstructor
    public static class Description {
        private String participantInn;

    }

    @Data
    @NoArgsConstructor
    public static class Product {
        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;

    }


    public static void main(String[] args) throws Exception {
        CrptApi api = new CrptApi(10,TimeUnit.SECONDS, 5);
        Document document = new Document();


        for (int i = 0; i < 10; i++) {
            api.createDocument(document, "Signature");
        }

    }

}

