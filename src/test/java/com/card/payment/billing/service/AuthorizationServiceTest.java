package com.card.payment.billing.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthorizationService 단위 테스트
 * 체크카드 및 신용카드 승인 로직을 테스트합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthorizationService 테스트")
class AuthorizationServiceTest {
    
    @Mock
    private CardRepository cardRepository;
    
    @Mock
    private AuthorizationRepository authorizationRepository;
    
    @Mock
    private CardValidationService cardValidationService;
    
    @Mock
    private BankClient bankClient;
    
    @InjectMocks
    private AuthorizationService authorizationService;
    
    private Card debitCard;
    private Card creditCard;
    private AuthorizationRequest authorizationRequest;
    
    @BeforeEach
    void setUp() {
        // 체크카드 생성
        debitCard = Card.builder()
                .id(1L)
                .cardNumber("4532015112830366")
                .cardType(CardType.DEBIT)
                .cardStatus(CardStatus.ACTIVE)
                .expiryDate(LocalDate.now().plusYears(2))
                .customerId("CUST001")
                .build();
        
        // 신용카드 생성
        creditCard = Card.builder()
                .id(2L)
                .cardNumber("5425233430109903")
                .cardType(CardType.CREDIT)
                .cardStatus(CardStatus.ACTIVE)
                .expiryDate(LocalDate.now().plusYears(2))
                .creditLimit(new BigDecimal("5000000"))
                .usedAmount(new BigDecimal("1000000"))
                .customerId("CUST002")
                .build();
        
        // 승인 요청 생성
        authorizationRequest = AuthorizationRequest.builder()
                .transactionId("TXN20240101001")
                .cardNumber("4532015112830366")
                .amount(new BigDecimal("50000"))
                .merchantId("MERCHANT001")
                .terminalId("TERM001")
                .build();
    }
    
    @Test
    @DisplayName("체크카드 승인 - 성공")
    void testAuthorizeDebitCard_Success() {
        // Given
        when(cardValidationService.validateAndGetCard(anyString())).thenReturn(debitCard);
        
        BalanceResponse balanceResponse = new BalanceResponse(
                "1000000001",
                new BigDecimal("100000"),
                BigDecimal.ZERO,
                new BigDecimal("100000"),
                new BigDecimal("50000"),
                true,
                "00",
                "잔액 충분"
        );
        when(bankClient.checkBalance(anyString(), any(BigDecimal.class))).thenReturn(balanceResponse);
        
        DebitResponse debitResponse = new DebitResponse(
                true,
                "TXN20240101001",
                new BigDecimal("50000"),
                "출금 성공"
        );
        when(bankClient.requestDebit(anyString(), any(BigDecimal.class), anyString())).thenReturn(debitResponse);
        
        when(authorizationRepository.save(any(Authorization.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        AuthorizationResponse response = authorizationService.authorize(authorizationRequest);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.isApproved()).isTrue();
        assertThat(response.getResponseCode()).isEqualTo("00");
        assertThat(response.getApprovalNumber()).isNotNull();
        assertThat(response.getApprovalNumber()).hasSize(8);
        assertThat(response.getTransactionId()).isEqualTo("TXN20240101001");
        
        verify(bankClient).checkBalance(anyString(), any(BigDecimal.class));
        verify(bankClient).requestDebit(anyString(), any(BigDecimal.class), anyString());
        verify(authorizationRepository).save(any(Authorization.class));
    }
    
    @Test
    @DisplayName("체크카드 승인 - 잔액 부족으로 거절")
    void testAuthorizeDebitCard_InsufficientBalance() {
        // Given
        when(cardValidationService.validateAndGetCard(anyString())).thenReturn(debitCard);
        
        BalanceResponse balanceResponse = new BalanceResponse(
                "1000000001",
                new BigDecimal("10000"),
                BigDecimal.ZERO,
                new BigDecimal("10000"),
                new BigDecimal("50000"),
                false,
                "51",
                "잔액 부족"
        );
        when(bankClient.checkBalance(anyString(), any(BigDecimal.class))).thenReturn(balanceResponse);
        
        when(authorizationRepository.save(any(Authorization.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        AuthorizationResponse response = authorizationService.authorize(authorizationRequest);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.isApproved()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("51");
        assertThat(response.getMessage()).contains("잔액 부족");
        assertThat(response.getApprovalNumber()).isNull();
        
        verify(bankClient).checkBalance(anyString(), any(BigDecimal.class));
        verify(bankClient, never()).requestDebit(anyString(), any(BigDecimal.class), anyString());
        verify(authorizationRepository).save(any(Authorization.class));
    }
    
    @Test
    @DisplayName("체크카드 승인 - 출금 실패로 거절")
    void testAuthorizeDebitCard_DebitFailed() {
        // Given
        when(cardValidationService.validateAndGetCard(anyString())).thenReturn(debitCard);
        
        BalanceResponse balanceResponse = new BalanceResponse(
                "1000000001",
                new BigDecimal("100000"),
                BigDecimal.ZERO,
                new BigDecimal("100000"),
                new BigDecimal("50000"),
                true,
                "00",
                "잔액 충분"
        );
        when(bankClient.checkBalance(anyString(), any(BigDecimal.class))).thenReturn(balanceResponse);
        
        DebitResponse debitResponse = new DebitResponse(
                false,
                "TXN20240101001",
                null,
                "출금 처리 실패"
        );
        when(bankClient.requestDebit(anyString(), any(BigDecimal.class), anyString())).thenReturn(debitResponse);
        
        when(authorizationRepository.save(any(Authorization.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        AuthorizationResponse response = authorizationService.authorize(authorizationRequest);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.isApproved()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("51");
        assertThat(response.getMessage()).contains("출금 실패");
        assertThat(response.getApprovalNumber()).isNull();
        
        verify(bankClient).checkBalance(anyString(), any(BigDecimal.class));
        verify(bankClient).requestDebit(anyString(), any(BigDecimal.class), anyString());
        verify(authorizationRepository).save(any(Authorization.class));
    }
    
    @Test
    @DisplayName("신용카드 승인 - 성공")
    void testAuthorizeCreditCard_Success() {
        // Given
        authorizationRequest.setCardNumber("5425233430109903");
        
        when(cardValidationService.validateAndGetCard(anyString())).thenReturn(creditCard);
        when(cardRepository.findByCardNumberWithLock(anyString())).thenReturn(Optional.of(creditCard));
        when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorizationRepository.save(any(Authorization.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        AuthorizationResponse response = authorizationService.authorize(authorizationRequest);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.isApproved()).isTrue();
        assertThat(response.getResponseCode()).isEqualTo("00");
        assertThat(response.getApprovalNumber()).isNotNull();
        assertThat(response.getApprovalNumber()).hasSize(8);
        assertThat(response.getTransactionId()).isEqualTo("TXN20240101001");
        
        // 사용 금액이 증가했는지 확인
        assertThat(creditCard.getUsedAmount()).isEqualTo(new BigDecimal("1050000"));
        
        verify(cardRepository).findByCardNumberWithLock(anyString());
        verify(cardRepository).save(any(Card.class));
        verify(authorizationRepository).save(any(Authorization.class));
    }
    
    @Test
    @DisplayName("신용카드 승인 - 한도 초과로 거절")
    void testAuthorizeCreditCard_LimitExceeded() {
        // Given
        authorizationRequest.setCardNumber("5425233430109903");
        authorizationRequest.setAmount(new BigDecimal("5000000"));  // 한도 초과 금액
        
        when(cardValidationService.validateAndGetCard(anyString())).thenReturn(creditCard);
        when(cardRepository.findByCardNumberWithLock(anyString())).thenReturn(Optional.of(creditCard));
        when(authorizationRepository.save(any(Authorization.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        AuthorizationResponse response = authorizationService.authorize(authorizationRequest);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.isApproved()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("61");
        assertThat(response.getMessage()).contains("한도 초과");
        assertThat(response.getApprovalNumber()).isNull();
        
        // 사용 금액이 증가하지 않았는지 확인
        assertThat(creditCard.getUsedAmount()).isEqualTo(new BigDecimal("1000000"));
        
        verify(cardRepository).findByCardNumberWithLock(anyString());
        verify(cardRepository, never()).save(any(Card.class));
        verify(authorizationRepository).save(any(Authorization.class));
    }
    
    @Test
    @DisplayName("신용카드 승인 - 한도 정확히 사용")
    void testAuthorizeCreditCard_ExactLimit() {
        // Given
        authorizationRequest.setCardNumber("5425233430109903");
        authorizationRequest.setAmount(new BigDecimal("4000000"));  // 정확히 남은 한도
        
        when(cardValidationService.validateAndGetCard(anyString())).thenReturn(creditCard);
        when(cardRepository.findByCardNumberWithLock(anyString())).thenReturn(Optional.of(creditCard));
        when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorizationRepository.save(any(Authorization.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        AuthorizationResponse response = authorizationService.authorize(authorizationRequest);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.isApproved()).isTrue();
        assertThat(response.getResponseCode()).isEqualTo("00");
        
        // 사용 금액이 한도와 같아졌는지 확인
        assertThat(creditCard.getUsedAmount()).isEqualTo(new BigDecimal("5000000"));
        
        verify(cardRepository).save(any(Card.class));
    }
    
    @Test
    @DisplayName("카드 유효성 검증 실패 - 카드 정지")
    void testAuthorize_CardValidationFailed() {
        // Given
        when(cardValidationService.validateAndGetCard(anyString()))
                .thenThrow(new CardValidationService.CardValidationException("14", "카드 정지"));
        when(authorizationRepository.save(any(Authorization.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        AuthorizationResponse response = authorizationService.authorize(authorizationRequest);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.isApproved()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("14");
        assertThat(response.getMessage()).contains("카드 정지");
        assertThat(response.getApprovalNumber()).isNull();
        
        verify(cardValidationService).validateAndGetCard(anyString());
        verify(authorizationRepository).save(any(Authorization.class));
    }
    
    @Test
    @DisplayName("카드 유효성 검증 실패 - 유효기간 만료")
    void testAuthorize_CardExpired() {
        // Given
        when(cardValidationService.validateAndGetCard(anyString()))
                .thenThrow(new CardValidationService.CardValidationException("54", "유효기간 만료"));
        when(authorizationRepository.save(any(Authorization.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        AuthorizationResponse response = authorizationService.authorize(authorizationRequest);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.isApproved()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("54");
        assertThat(response.getMessage()).contains("유효기간 만료");
        
        verify(authorizationRepository).save(any(Authorization.class));
    }
    
    @Test
    @DisplayName("체크카드 승인 - 은행 서비스 예외 발생")
    void testAuthorizeDebitCard_BankServiceException() {
        // Given
        when(cardValidationService.validateAndGetCard(anyString())).thenReturn(debitCard);
        when(bankClient.checkBalance(anyString(), any(BigDecimal.class)))
                .thenThrow(new RuntimeException("은행 서비스 연결 실패"));
        when(authorizationRepository.save(any(Authorization.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        AuthorizationResponse response = authorizationService.authorize(authorizationRequest);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.isApproved()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("96");
        assertThat(response.getMessage()).contains("시스템 오류");
        
        verify(authorizationRepository).save(any(Authorization.class));
    }
    
    @Test
    @DisplayName("신용카드 승인 - 카드 조회 실패")
    void testAuthorizeCreditCard_CardNotFoundWithLock() {
        // Given
        authorizationRequest.setCardNumber("5425233430109903");
        
        when(cardValidationService.validateAndGetCard(anyString())).thenReturn(creditCard);
        when(cardRepository.findByCardNumberWithLock(anyString())).thenReturn(Optional.empty());
        when(authorizationRepository.save(any(Authorization.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        AuthorizationResponse response = authorizationService.authorize(authorizationRequest);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.isApproved()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("96");
        assertThat(response.getMessage()).contains("시스템 오류");
        
        verify(cardRepository).findByCardNumberWithLock(anyString());
        verify(authorizationRepository).save(any(Authorization.class));
    }
}
