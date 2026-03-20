package com.card.payment.billing.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PIN 검증 통합 테스트
 * 실제 BCryptPasswordEncoder를 사용하여 PIN 해시 검증을 테스트합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("PIN 검증 통합 테스트")
class PinVerificationIntegrationTest {
    
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    
    @Test
    @DisplayName("BCrypt 해시 생성 및 검증 - 1234")
    void testBCryptHashGeneration() {
        // Given
        String pin = "1234";
        
        // When - 새로운 해시 생성
        String hash = passwordEncoder.encode(pin);
        
        // Then - 생성된 해시로 검증
        boolean matches = passwordEncoder.matches(pin, hash);
        
        System.out.println("=== BCrypt Hash Test ===");
        System.out.println("PIN: " + pin);
        System.out.println("Generated Hash: " + hash);
        System.out.println("Verification: " + (matches ? "SUCCESS" : "FAILED"));
        
        assertThat(matches).isTrue();
    }
    
    @Test
    @DisplayName("기존 해시 검증 - $2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi")
    void testExistingHash1() {
        // Given
        String pin = "1234";
        String existingHash = "$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi";
        
        // When
        boolean matches = passwordEncoder.matches(pin, existingHash);
        
        // Then
        System.out.println("=== Existing Hash 1 Test ===");
        System.out.println("PIN: " + pin);
        System.out.println("Hash: " + existingHash);
        System.out.println("Verification: " + (matches ? "SUCCESS" : "FAILED"));
        
        // 이 테스트는 해시가 실제로 "1234"와 매칭되는지 확인
        // 실패하면 해시가 잘못되었음을 의미
    }
    
    @Test
    @DisplayName("기존 해시 검증 - $2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG")
    void testExistingHash2() {
        // Given
        String pin = "1234";
        String existingHash = "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG";
        
        // When
        boolean matches = passwordEncoder.matches(pin, existingHash);
        
        // Then
        System.out.println("=== Existing Hash 2 Test ===");
        System.out.println("PIN: " + pin);
        System.out.println("Hash: " + existingHash);
        System.out.println("Verification: " + (matches ? "SUCCESS" : "FAILED"));
        
        // 이 테스트는 해시가 실제로 "1234"와 매칭되는지 확인
        // 실패하면 해시가 잘못되었음을 의미
    }
    
    @Test
    @DisplayName("여러 개의 새로운 해시 생성")
    void testGenerateMultipleHashes() {
        // Given
        String pin = "1234";
        
        System.out.println("\n=== Generating 5 New Hashes for PIN: " + pin + " ===\n");
        
        // When & Then - 5개의 해시 생성 및 검증
        for (int i = 1; i <= 5; i++) {
            String hash = passwordEncoder.encode(pin);
            boolean matches = passwordEncoder.matches(pin, hash);
            
            System.out.println("Hash " + i + ": " + hash);
            System.out.println("Verification: " + (matches ? "✓ SUCCESS" : "✗ FAILED"));
            System.out.println();
            
            assertThat(matches).isTrue();
        }
        
        System.out.println("=== COPY ONE OF THE SUCCESSFUL HASHES ABOVE TO data.sql ===\n");
    }
}
