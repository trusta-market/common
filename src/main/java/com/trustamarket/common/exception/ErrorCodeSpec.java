package com.trustamarket.common.exception;

import org.springframework.http.HttpStatus;

// 도메인 서비스가 자체 에러 코드 enum 을 정의할 때 구현하는 스펙.
// enum 이 이 인터페이스를 구현해 CustomException(ErrorCodeSpec) 생성자로 넘긴다.
public interface ErrorCodeSpec {
  // RFC 9457 의 type URI 로 사용될 에러 식별자.
  String getCode();
  HttpStatus getStatus();
  String getMessage();
  // 입력 검증성 에러라면 관련 필드명. 일반 에러는 null 반환.
  String getField();
}
