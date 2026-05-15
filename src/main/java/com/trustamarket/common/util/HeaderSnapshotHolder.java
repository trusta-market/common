package com.trustamarket.common.util;

import java.util.List;
import java.util.Map;

// async thread 에서 X-User-* 헤더 등을 안전히 전파하기 위한 snapshot holder.
//
// 원본 HttpServletRequest 는 요청 종료 후 컨테이너가 회수 → 자식 thread 에서 접근 시
// IllegalStateException ("request is not active anymore") 발생 가능.
// 미리 헤더 값을 캡처해서 별도 ThreadLocal 에 박아 두면 라이프사이클과 독립적으로 안전하게 읽음.
//
// 책임 분담:
// - MdcTaskDecorator: 부모 thread 의 헤더 → snapshot 캡처 → 자식 thread 에 set, 종료 시 clear
// - FeignConfig.requestHeaderInterceptor: snapshot 있으면 그것 사용, 없으면 RequestContextHolder fallback
public final class HeaderSnapshotHolder {

    // FeignConfig 와 MdcTaskDecorator 가 공통 참조 — 헤더 추가/제거 시 이 한 곳만 수정.
    public static final List<String> PROPAGATE_HEADERS = List.of(
            "Authorization",
            "X-User-UUID",
            "X-User-Email",
            "X-User-Name",
            "X-User-Role",
            "X-User-Slack-Id",
            "X-User-Enabled",
            "X-Trace-Id"
    );

    private static final ThreadLocal<Map<String, String>> HOLDER = new ThreadLocal<>();

    private HeaderSnapshotHolder() {
    }

    public static void set(Map<String, String> snapshot) {
        HOLDER.set(snapshot);
    }

    public static Map<String, String> get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
