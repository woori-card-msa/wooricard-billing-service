package com.card.payment.billing.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BankClient 타임아웃 및 재전송 로직 테스트
 */
@DisplayName("BankClient 타임아웃 및 재전송 테스트")
class BankClientRetryTest {
    
    private MockWebServer mockWebServer;
    private BankClient bankClient;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        String baseUrl = mockWebServer.url("/").toString();
        bankClient = new BankClientImpl(baseUrl);
        objectMapper = new ObjectMapper();
    }
    
    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }
    
    @Test
    @DisplayName("잔액 조회 성공 시나리오")
    void testCheckBalanceSuccess() throws Exception {
        // Given: 정상 응답 설정
        BalanceResponse expectedResponse = new BalanceResponse(
                "1000000001",
                new BigDecimal("100000"),
                BigDecimal.ZERO,
                new BigDecimal("100000"),
                new BigDecimal("10000"),
                true,
                "00",
                "충분한 잔액"
        );
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(expectedResponse))
                .addHeader("Content-Type", "application/json"));
        
        // When: 잔액 조회 요청
        BalanceResponse response = bankClient.checkBalance("1234567890123456", new BigDecimal("10000"));
        
        // Then: 응답 검증
        assertNotNull(response);
        assertTrue(response.isSufficient());
        assertEquals(new BigDecimal("100000"), response.getBalance());
        
        // 요청이 1번만 발생했는지 확인
        assertEquals(1, mockWebServer.getRequestCount());
    }
    
    @Test
    @DisplayName("출금 성공 시나리오")
    void testRequestDebitSuccess() throws Exception {
        // Given: 정상 응답 설정
        DebitResponse expectedResponse = new DebitResponse(true, 
                "TXN-123", new BigDecimal("90000"), "출금 성공");
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(expectedResponse))
                .addHeader("Content-Type", "application/json"));
        
        // When: 출금 요청
        DebitResponse response = bankClient.requestDebit("1234567890123456", 
                new BigDecimal("10000"), "TXN-123");
        
        // Then: 응답 검증
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("TXN-123", response.getTransactionId());
        
        // 요청이 1번만 발생했는지 확인
        assertEquals(1, mockWebServer.getRequestCount());
    }
    
    @Test
    @DisplayName("잔액 조회 실패 후 재전송 성공 시나리오")
    void testCheckBalanceRetrySuccess() throws Exception {
        // Given: 첫 번째 요청은 실패, 두 번째 요청은 성공
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));
        
        BalanceResponse expectedResponse = new BalanceResponse(
                "1000000001",
                new BigDecimal("100000"),
                BigDecimal.ZERO,
                new BigDecimal("100000"),
                new BigDecimal("10000"),
                true,
                "00",
                "충분한 잔액"
        );
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(expectedResponse))
                .addHeader("Content-Type", "application/json"));
        
        // When: 잔액 조회 요청
        BalanceResponse response = bankClient.checkBalance("1234567890123456", new BigDecimal("10000"));
        
        // Then: 재전송 후 성공
        assertNotNull(response);
        assertTrue(response.isSufficient());
        
        // 요청이 2번 발생했는지 확인 (원본 + 재전송 1회)
        assertEquals(2, mockWebServer.getRequestCount());
    }
    
    @Test
    @DisplayName("출금 실패 후 재전송 성공 시나리오")
    void testRequestDebitRetrySuccess() throws Exception {
        // Given: 첫 번째 요청은 실패, 두 번째 요청은 성공
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(503)
                .setBody("Service Unavailable"));
        
        DebitResponse expectedResponse = new DebitResponse(true, 
                "TXN-123", new BigDecimal("90000"), "출금 성공");
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(expectedResponse))
                .addHeader("Content-Type", "application/json"));
        
        // When: 출금 요청
        DebitResponse response = bankClient.requestDebit("1234567890123456", 
                new BigDecimal("10000"), "TXN-123");
        
        // Then: 재전송 후 성공
        assertNotNull(response);
        assertTrue(response.isSuccess());
        
        // 요청이 2번 발생했는지 확인 (원본 + 재전송 1회)
        assertEquals(2, mockWebServer.getRequestCount());
    }
    
    @Test
    @DisplayName("잔액 조회 최대 재전송 후 최종 실패 시나리오")
    void testCheckBalanceMaxRetryFailure() {
        // Given: 모든 요청이 실패
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));
        
        // When & Then: 최종 실패 예외 발생
        assertThrows(BankClientImpl.BankClientException.class, () -> {
            bankClient.checkBalance("1234567890123456", new BigDecimal("10000"));
        });
        
        // 요청이 2번 발생했는지 확인 (원본 + 재전송 1회)
        assertEquals(2, mockWebServer.getRequestCount());
    }
    
    @Test
    @DisplayName("출금 최대 재전송 후 최종 실패 시나리오")
    void testRequestDebitMaxRetryFailure() {
        // Given: 모든 요청이 실패
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));
        
        // When & Then: 최종 실패 예외 발생
        assertThrows(BankClientImpl.BankClientException.class, () -> {
            bankClient.requestDebit("1234567890123456", new BigDecimal("10000"), "TXN-123");
        });
        
        // 요청이 2번 발생했는지 확인 (원본 + 재전송 1회)
        assertEquals(2, mockWebServer.getRequestCount());
    }
    
    @Test
    @DisplayName("잔액 조회 타임아웃 시나리오 (10초 초과)")
    void testCheckBalanceTimeout() {
        // Given: 응답 지연 설정 (11초)
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"sufficient\":true}")
                .setBodyDelay(11, TimeUnit.SECONDS));
        
        // When & Then: 타임아웃 예외 발생
        assertThrows(BankClientImpl.BankClientException.class, () -> {
            bankClient.checkBalance("1234567890123456", new BigDecimal("10000"));
        });
    }
    
    @Test
    @DisplayName("출금 타임아웃 시나리오 (30초 초과)")
    void testRequestDebitTimeout() {
        // Given: 응답 지연 설정 (31초)
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"success\":true}")
                .setBodyDelay(31, TimeUnit.SECONDS));
        
        // When & Then: 타임아웃 예외 발생
        assertThrows(BankClientImpl.BankClientException.class, () -> {
            bankClient.requestDebit("1234567890123456", new BigDecimal("10000"), "TXN-123");
        });
    }
    
    @Test
    @DisplayName("재전송 시 요청 내용이 동일한지 확인")
    void testRetryRequestContent() throws Exception {
        // Given: 첫 번째 요청은 실패, 두 번째 요청은 성공
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500));
        
        BalanceResponse expectedResponse = new BalanceResponse(
                "1000000001",
                new BigDecimal("100000"),
                BigDecimal.ZERO,
                new BigDecimal("100000"),
                new BigDecimal("10000"),
                true,
                "00",
                "충분한 잔액"
        );
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(expectedResponse))
                .addHeader("Content-Type", "application/json"));
        
        // When: 잔액 조회 요청
        bankClient.checkBalance("1234567890123456", new BigDecimal("10000"));
        
        // Then: 두 요청의 내용이 동일한지 확인
        RecordedRequest request1 = mockWebServer.takeRequest();
        RecordedRequest request2 = mockWebServer.takeRequest();
        
        assertEquals(request1.getPath(), request2.getPath());
        assertEquals(request1.getBody().readUtf8(), request2.getBody().readUtf8());
    }
}
