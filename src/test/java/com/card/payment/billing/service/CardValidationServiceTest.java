package com.card.payment.billing.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * CardValidationService 단위 테스트
 * 카드 유효성 검증 로직을 테스트합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CardValidationService 테스트")
class CardValidationServiceTest {
    
    @Mock
    private CardRepository cardRepository;
    
    @Mock
    private BCryptPasswordEncoder passwordEncoder;
    
    @InjectMocks
    private CardValidationService cardValidationService;
    
    private Card validCard;
    
    @BeforeEach
    void setUp() {
        // 유효한 카드 생성
        validCard = Card.builder()
                .id(1L)
                .cardNumber("4532015112830366")  // 유효한 Luhn 알고리즘 카드 번호
                .cardType(CardType.CREDIT)
                .cardStatus(CardStatus.ACTIVE)
                .expiryDate(LocalDate.now().plusYears(2))
                .creditLimit(new BigDecimal("5000000"))
                .usedAmount(BigDecimal.ZERO)
                .pin("$2a$10$abcdefghijklmnopqrstuvwxyz")  // BCrypt 해시
                .customerId("CUST001")
                .build();
    }
    
    @Test
    @DisplayName("Luhn 알고리즘 검증 - 유효한 카드 번호")
    void testValidateLuhnAlgorithm_ValidCardNumber() {
        // Given
        String validCardNumber = "4532015112830366";  // 유효한 Visa 카드 번호
        
        // When
        boolean result = cardValidationService.validateLuhnAlgorithm(validCardNumber);
        
        // Then
        assertThat(result).isTrue();
    }
    
    @Test
    @DisplayName("Luhn 알고리즘 검증 - 무효한 카드 번호")
    void testValidateLuhnAlgorithm_InvalidCardNumber() {
        // Given
        String invalidCardNumber = "4532015112830367";  // 체크섬 오류
        
        // When
        boolean result = cardValidationService.validateLuhnAlgorithm(invalidCardNumber);
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    @DisplayName("Luhn 알고리즘 검증 - null 카드 번호")
    void testValidateLuhnAlgorithm_NullCardNumber() {
        // When
        boolean result = cardValidationService.validateLuhnAlgorithm(null);
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    @DisplayName("Luhn 알고리즘 검증 - 빈 문자열")
    void testValidateLuhnAlgorithm_EmptyCardNumber() {
        // When
        boolean result = cardValidationService.validateLuhnAlgorithm("");
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    @DisplayName("Luhn 알고리즘 검증 - 하이픈 포함 카드 번호")
    void testValidateLuhnAlgorithm_CardNumberWithHyphens() {
        // Given
        String cardNumberWithHyphens = "4532-0151-1283-0366";
        
        // When
        boolean result = cardValidationService.validateLuhnAlgorithm(cardNumberWithHyphens);
        
        // Then
        assertThat(result).isTrue();
    }
    
    @Test
    @DisplayName("Luhn 알고리즘 검증 - 너무 짧은 카드 번호")
    void testValidateLuhnAlgorithm_TooShortCardNumber() {
        // Given
        String shortCardNumber = "123456789012";  // 12자리
        
        // When
        boolean result = cardValidationService.validateLuhnAlgorithm(shortCardNumber);
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    @DisplayName("카드 상태 확인 - 정상 상태")
    void testCheckCardStatus_Active() {
        // Given
        validCard.setCardStatus(CardStatus.ACTIVE);
        
        // When
        CardValidationService.ValidationResult result = cardValidationService.checkCardStatus(validCard);
        
        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getResponseCode()).isEqualTo("00");
    }
    
    @Test
    @DisplayName("카드 상태 확인 - 정지 상태")
    void testCheckCardStatus_Suspended() {
        // Given
        validCard.setCardStatus(CardStatus.SUSPENDED);
        
        // When
        CardValidationService.ValidationResult result = cardValidationService.checkCardStatus(validCard);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getResponseCode()).isEqualTo("14");
        assertThat(result.getMessage()).contains("정지");
    }
    
    @Test
    @DisplayName("카드 상태 확인 - 분실 상태")
    void testCheckCardStatus_Lost() {
        // Given
        validCard.setCardStatus(CardStatus.LOST);
        
        // When
        CardValidationService.ValidationResult result = cardValidationService.checkCardStatus(validCard);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getResponseCode()).isEqualTo("14");
        assertThat(result.getMessage()).contains("분실");
    }
    
    @Test
    @DisplayName("카드 상태 확인 - 해지 상태")
    void testCheckCardStatus_Terminated() {
        // Given
        validCard.setCardStatus(CardStatus.TERMINATED);
        
        // When
        CardValidationService.ValidationResult result = cardValidationService.checkCardStatus(validCard);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getResponseCode()).isEqualTo("14");
        assertThat(result.getMessage()).contains("해지");
    }
    
    @Test
    @DisplayName("카드 상태 확인 - null 카드")
    void testCheckCardStatus_NullCard() {
        // When
        CardValidationService.ValidationResult result = cardValidationService.checkCardStatus(null);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getResponseCode()).isEqualTo("14");
    }
    
    @Test
    @DisplayName("유효기간 검증 - 유효한 카드")
    void testValidateExpiryDate_ValidCard() {
        // Given
        validCard.setExpiryDate(LocalDate.now().plusYears(2));
        
        // When
        CardValidationService.ValidationResult result = cardValidationService.validateExpiryDate(validCard);
        
        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getResponseCode()).isEqualTo("00");
    }
    
    @Test
    @DisplayName("유효기간 검증 - 만료된 카드")
    void testValidateExpiryDate_ExpiredCard() {
        // Given
        validCard.setExpiryDate(LocalDate.now().minusMonths(1));
        
        // When
        CardValidationService.ValidationResult result = cardValidationService.validateExpiryDate(validCard);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getResponseCode()).isEqualTo("54");
        assertThat(result.getMessage()).contains("만료");
    }
    
    @Test
    @DisplayName("유효기간 검증 - 당월 말일까지 유효")
    void testValidateExpiryDate_CurrentMonth() {
        // Given
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        validCard.setExpiryDate(currentMonth);
        
        // When
        CardValidationService.ValidationResult result = cardValidationService.validateExpiryDate(validCard);
        
        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getResponseCode()).isEqualTo("00");
    }
    
    @Test
    @DisplayName("유효기간 검증 - null 카드")
    void testValidateExpiryDate_NullCard() {
        // When
        CardValidationService.ValidationResult result = cardValidationService.validateExpiryDate(null);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getResponseCode()).isEqualTo("54");
    }
    
    @Test
    @DisplayName("PIN 검증 - 일치하는 PIN")
    void testValidatePin_MatchingPin() {
        // Given
        String inputPin = "1234";
        when(passwordEncoder.matches(inputPin, validCard.getPin())).thenReturn(true);
        
        // When
        CardValidationService.ValidationResult result = cardValidationService.validatePin(validCard, inputPin);
        
        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getResponseCode()).isEqualTo("00");
    }
    
    @Test
    @DisplayName("PIN 검증 - 불일치하는 PIN")
    void testValidatePin_MismatchingPin() {
        // Given
        String inputPin = "9999";
        when(passwordEncoder.matches(inputPin, validCard.getPin())).thenReturn(false);
        
        // When
        CardValidationService.ValidationResult result = cardValidationService.validatePin(validCard, inputPin);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getResponseCode()).isEqualTo("55");
        assertThat(result.getMessage()).contains("PIN");
    }
    
    @Test
    @DisplayName("PIN 검증 - null PIN 입력")
    void testValidatePin_NullInputPin() {
        // When
        CardValidationService.ValidationResult result = cardValidationService.validatePin(validCard, null);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getResponseCode()).isEqualTo("55");
    }
    
    @Test
    @DisplayName("PIN 검증 - 빈 문자열 PIN 입력")
    void testValidatePin_EmptyInputPin() {
        // When
        CardValidationService.ValidationResult result = cardValidationService.validatePin(validCard, "");
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getResponseCode()).isEqualTo("55");
    }
    
    @Test
    @DisplayName("카드 전체 유효성 검증 - 성공")
    void testValidateCard_Success() {
        // Given
        String cardNumber = "4532015112830366";
        when(cardRepository.findByCardNumber(cardNumber)).thenReturn(Optional.of(validCard));
        
        // When
        CardValidationService.ValidationResult result = cardValidationService.validateCard(cardNumber);
        
        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getResponseCode()).isEqualTo("00");
    }
    
    @Test
    @DisplayName("카드 전체 유효성 검증 - Luhn 알고리즘 실패")
    void testValidateCard_LuhnFailed() {
        // Given
        String invalidCardNumber = "4532015112830367";
        
        // When
        CardValidationService.ValidationResult result = cardValidationService.validateCard(invalidCardNumber);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getResponseCode()).isEqualTo("14");
    }
    
    @Test
    @DisplayName("카드 전체 유효성 검증 - 카드 정보 없음")
    void testValidateCard_CardNotFound() {
        // Given
        String cardNumber = "4532015112830366";
        when(cardRepository.findByCardNumber(cardNumber)).thenReturn(Optional.empty());
        
        // When
        CardValidationService.ValidationResult result = cardValidationService.validateCard(cardNumber);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getResponseCode()).isEqualTo("14");
    }
    
    @Test
    @DisplayName("카드 전체 유효성 검증 - 카드 정지")
    void testValidateCard_CardSuspended() {
        // Given
        String cardNumber = "4532015112830366";
        validCard.setCardStatus(CardStatus.SUSPENDED);
        when(cardRepository.findByCardNumber(cardNumber)).thenReturn(Optional.of(validCard));
        
        // When
        CardValidationService.ValidationResult result = cardValidationService.validateCard(cardNumber);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getResponseCode()).isEqualTo("14");
    }
    
    @Test
    @DisplayName("카드 전체 유효성 검증 - 유효기간 만료")
    void testValidateCard_CardExpired() {
        // Given
        String cardNumber = "4532015112830366";
        validCard.setExpiryDate(LocalDate.now().minusMonths(1));
        when(cardRepository.findByCardNumber(cardNumber)).thenReturn(Optional.of(validCard));
        
        // When
        CardValidationService.ValidationResult result = cardValidationService.validateCard(cardNumber);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getResponseCode()).isEqualTo("54");
    }
    
    @Test
    @DisplayName("카드 유효성 검증 및 카드 객체 반환 - 성공")
    void testValidateAndGetCard_Success() {
        // Given
        String cardNumber = "4532015112830366";
        when(cardRepository.findByCardNumber(cardNumber)).thenReturn(Optional.of(validCard));
        
        // When
        Card result = cardValidationService.validateAndGetCard(cardNumber);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCardNumber()).isEqualTo(cardNumber);
        assertThat(result.getCardStatus()).isEqualTo(CardStatus.ACTIVE);
    }
    
    @Test
    @DisplayName("카드 유효성 검증 및 카드 객체 반환 - 실패 시 예외 발생")
    void testValidateAndGetCard_ThrowsException() {
        // Given
        String cardNumber = "4532015112830367";  // 무효한 Luhn
        
        // When & Then
        assertThatThrownBy(() -> cardValidationService.validateAndGetCard(cardNumber))
                .isInstanceOf(CardValidationService.CardValidationException.class)
                .hasMessageContaining("카드 번호 형식 오류");
    }
    
    @Test
    @DisplayName("카드 유효성 검증 및 카드 객체 반환 - 카드 정지 시 예외 발생")
    void testValidateAndGetCard_CardSuspended_ThrowsException() {
        // Given
        String cardNumber = "4532015112830366";
        validCard.setCardStatus(CardStatus.SUSPENDED);
        when(cardRepository.findByCardNumber(cardNumber)).thenReturn(Optional.of(validCard));
        
        // When & Then
        assertThatThrownBy(() -> cardValidationService.validateAndGetCard(cardNumber))
                .isInstanceOf(CardValidationService.CardValidationException.class)
                .hasMessageContaining("카드 정지")
                .extracting("responseCode")
                .isEqualTo("14");
    }
}
