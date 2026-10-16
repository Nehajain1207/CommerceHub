package com.neha.commercehub.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/** Talks to Razorpay's Orders API over HTTPS. Works with test-mode keys (rzp_test_...). */
public class RazorpayGateway implements PaymentGateway {

    private static final String ORDERS_URL = "https://api.razorpay.com/v1/orders";

    private final String keyId;
    private final String keySecret;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public RazorpayGateway(String keyId, String keySecret, ObjectMapper objectMapper) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.objectMapper = objectMapper;
    }

    @Override
    public String createOrder(String receipt, long amountPaise) {

        try {

            String body = objectMapper.writeValueAsString(Map.of(
                    "amount", amountPaise,
                    "currency", "INR",
                    "receipt", receipt));

            String credentials = Base64.getEncoder()
                    .encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder(URI.create(ORDERS_URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Basic " + credentials)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 == 5 || response.statusCode() == 429) {
                throw new TransientGatewayException("Razorpay answered " + response.statusCode());
            }

            if (response.statusCode() / 100 != 2) {
                throw new PaymentGatewayException(
                        "Razorpay answered " + response.statusCode() + ": " + response.body());
            }

            JsonNode json = objectMapper.readTree(response.body());
            String id = json.path("id").asText("");

            if (id.isEmpty()) {
                throw new PaymentGatewayException("Razorpay response had no order id: " + response.body());
            }

            return id;

        } catch (PaymentGatewayException ex) {

            throw ex;

        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {

            throw new PaymentGatewayException("Could not read Razorpay's answer: " + ex.getMessage());

        } catch (java.io.IOException ex) {

            // Timeouts and dropped connections: worth retrying
            throw new TransientGatewayException("Could not reach Razorpay: " + ex.getMessage());

        } catch (InterruptedException ex) {

            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("Interrupted while contacting Razorpay.");

        } catch (Exception ex) {

            throw new PaymentGatewayException("Could not reach Razorpay: " + ex.getMessage());
        }
    }

    @Override
    public String publicKey() {
        return keyId;
    }

    @Override
    public String signingSecret() {
        return keySecret;
    }

    @Override
    public boolean isDemo() {
        return false;
    }
}
