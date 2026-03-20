package com.card.payment.billing.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * BankClient Mock 테스트
 * BankClient 인터페이스의 Mock 동작을 테스트합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BankClient Mock 테스트")
class BankClientTest {
    
    @Mock
    private BankClient bankClient;
    
    private String cardNumber;
    private BigDecimal amount;
    private String transactionId;
    
    @BeforeEach
    void setUp() {
        cardNumber = "4532015112830366";
        amount = new BigDecimal("50000");
        transactionId = "TXN20240101001";
    }
    
    @Test
    @DisplayName("잔액 조회 - 잔액 충분")
    void testCheckBalance_Sufficient() {
        // Given
        BalanceResponse expectedResponse = new BalanceResponse(
                "1000000001",
                new BigDecimal("100000"),
                BigDecimal.ZERO,
                new BigDecimal("100000"),
                amount,
                true,
                "00",
                "잔액 충분"
        );
        
        when(bankClient.checkBalance(cardNumber, amount)).thenReturn(expectedResponse);
        
        // When
        BalanceResponse response = bankClient.checkBalance(cardNumber, amount);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSufficient()).isTrue();
        assertThat(response.getAvailableAmount()).isEqualByComparingTo(new BigDecimal("100000"));
        
        verify(bankClient).checkBalance(cardNumber, amount);
    }
    
    @Test
    @DisplayName("잔액 조회 - 잔액 부족")
    void testCheckBalance_Insufficient() {
        // Given
        BalanceResponse expectedResponse = new BalanceResponse(
                "1000000001",
                new BigDecimal("10000"),
                BigDecimal.ZERO,
                new BigDecimal("10000"),
                amount,
                false,
                "51",
                "잔액 부족"
        );
        
        when(bankClient.checkBalance(cardNumber, amount)).thenReturn(expectedResponse);
        
        // When
        BalanceResponse response = bankClient.checkBalance(cardNumber, amount);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSufficient()).isFalse();
        assertThat(response.getAvailableAmount()).isEqualByComparingTo(new BigDecimal("10000"));
        
        verify(bankClient).checkBalance(cardNumber, amount);
    }
    
    @Test
    @DisplayName("잔액 조회 - 다양한 금액")
    void testCheckBalance_VariousAmounts() {
        // Given
        BigDecimal smallAmount = new BigDecimal("1000");
        BigDecimal largeAmount = new BigDecimal("1000000");
        
        BalanceResponse smallAmountResponse = new BalanceResponse(
                "1000000001",
                new BigDecimal("50000"),
                BigDecimal.ZERO,
                new BigDecimal("50000"),
                smallAmount,
                true,
                "00",
                "잔액 충분"
        );
        
        BalanceResponse largeAmountResponse = new BalanceResponse(
                "1000000001",
                new BigDecimal("50000"),
                BigDecimal.ZERO,
                new BigDecimal("50000"),
                largeAmount,
                false,
                "51",
                "잔액 부족"
        );
        
        when(bankClient.checkBalance(cardNumber, smallAmount)).thenReturn(smallAmountResponse);
        when(bankClient.checkBalance(cardNumber, largeAmount)).thenReturn(largeAmountResponse);
        
        // When
        BalanceResponse smallResponse = bankClient.checkBalance(cardNumber, smallAmount);
        BalanceResponse largeResponse = bankClient.checkBalance(cardNumber, largeAmount);
        
        // Then
        assertThat(smallResponse.isSufficient()).isTrue();
        assertThat(largeResponse.isSufficient()).isFalse();
        
        verify(bankClient).checkBalance(cardNumber, smallAmount);
        verify(bankClient).checkBalance(cardNumber, largeAmount);
    }
    
    @Test
    @DisplayName("출금 요청 - 성공")
    void testRequestDebit_Success() {
        // Given
        DebitResponse expectedResponse = new DebitResponse(
                true,
                transactionId,
                new BigDecimal("50000"),
                "출금 성공"
        );
        
        when(bankClient.requestDebit(cardNumber, amount, transactionId)).thenReturn(expectedResponse);
        
        // When
        DebitResponse response = bankClient.requestDebit(cardNumber, amount, transactionId);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTransactionId()).isEqualTo(transactionId);
        assertThat(response.getMessage()).isEqualTo("출금 성공");
        assertThat(response.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("50000"));
        
        verify(bankClient).requestDebit(cardNumber, amount, transactionId);
    }
    
    @Test
    @DisplayName("출금 요청 - 실패")
    void testRequestDebit_Failed() {
        // Given
        DebitResponse expectedResponse = new DebitResponse(
                false,
                transactionId,
                null,
                "출금 실패: 계좌 잠김"
        );
        
        when(bankClient.requestDebit(cardNumber, amount, transactionId)).thenReturn(expectedResponse);
        
        // When
        DebitResponse response = bankClient.requestDebit(cardNumber, amount, transactionId);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("출금 실패");
        assertThat(response.getBalanceAfter()).isNull();
        
        verify(bankClient).requestDebit(cardNumber, amount, transactionId);
    }
    
    @Test
    @DisplayName("출금 요청 - 다양한 거래 ID")
    void testRequestDebit_VariousTransactionIds() {
        // Given
        String txnId1 = "TXN001";
        String txnId2 = "TXN002";
        
        DebitResponse response1 = new DebitResponse(
                true,
                txnId1,
                new BigDecimal("50000"),
                "출금 성공"
        );
        
        DebitResponse response2 = new DebitResponse(
                true,
                txnId2,
                new BigDecimal("50000"),
                "출금 성공"
        );
        
        when(bankClient.requestDebit(cardNumber, amount, txnId1)).thenReturn(response1);
        when(bankClient.requestDebit(cardNumber, amount, txnId2)).thenReturn(response2);
        
        // When
        DebitResponse result1 = bankClient.requestDebit(cardNumber, amount, txnId1);
        DebitResponse result2 = bankClient.requestDebit(cardNumber, amount, txnId2);
        
        // Then
        assertThat(result1.getTransactionId()).isEqualTo(txnId1);
        assertThat(result2.getTransactionId()).isEqualTo(txnId2);
        
        verify(bankClient).requestDebit(cardNumber, amount, txnId1);
        verify(bankClient).requestDebit(cardNumber, amount, txnId2);
    }
    
    @Test
    @DisplayName("잔액 조회 호출 횟수 검증")
    void testCheckBalance_VerifyInvocations() {
        // Given
        BalanceResponse response = new BalanceResponse(
                "1000000001",
                new BigDecimal("100000"),
                BigDecimal.ZERO,
                new BigDecimal("100000"),
                amount,
                true,
                "00",
                "잔액 충분"
        );
        
        when(bankClient.checkBalance(anyString(), any(BigDecimal.class))).thenReturn(response);
        
        // When
        bankClient.checkBalance(cardNumber, amount);
        bankClient.checkBalance(cardNumber, amount);
        
        // Then
        verify(bankClient, times(2)).checkBalance(cardNumber, amount);
    }
    
    @Test
    @DisplayName("출금 요청 호출 횟수 검증")
    void testRequestDebit_VerifyInvocations() {
        // Given
        DebitResponse response = new DebitResponse(
                true,
                transactionId,
                new BigDecimal("50000"),
                "출금 성공"
        );
        
        when(bankClient.requestDebit(anyString(), any(BigDecimal.class), anyString())).thenReturn(response);
        
        // When
        bankClient.requestDebit(cardNumber, amount, transactionId);
        
        // Then
        verify(bankClient, times(1)).requestDebit(cardNumber, amount, transactionId);
        verify(bankClient, never()).requestDebit(eq(cardNumber), eq(amount), eq("DIFFERENT_TXN"));
    }
    
    @Test
    @DisplayName("잔액 조회 - ArgumentMatchers 사용")
    void testCheckBalance_WithArgumentMatchers() {
        // Given
        BalanceResponse response = new BalanceResponse(
                "1000000001",
                new BigDecimal("100000"),
                BigDecimal.ZERO,
                new BigDecimal("100000"),
                new BigDecimal("12345"),
                true,
                "00",
                "잔액 충분"
        );
        
        when(bankClient.checkBalance(anyString(), any(BigDecimal.class))).thenReturn(response);
        
        // When
        BalanceResponse result = bankClient.checkBalance("ANY_CARD", new BigDecimal("12345"));
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSufficient()).isTrue();
        
        verify(bankClient).checkBalance(anyString(), any(BigDecimal.class));
    }
    
    @Test
    @DisplayName("출금 요청 - ArgumentMatchers 사용")
    void testRequestDebit_WithArgumentMatchers() {
        // Given
        DebitResponse response = new DebitResponse(
                true,
                "ANY_TXN",
                new BigDecimal("50000"),
                "출금 성공"
        );
        
        when(bankClient.requestDebit(anyString(), any(BigDecimal.class), anyString())).thenReturn(response);
        
        // When
        DebitResponse result = bankClient.requestDebit("ANY_CARD", new BigDecimal("12345"), "ANY_TXN");
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        
        verify(bankClient).requestDebit(anyString(), any(BigDecimal.class), anyString());
    }
}
