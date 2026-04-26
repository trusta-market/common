package com.trustamarket.common.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// JPA 공통 설정. Auditing + Repository/EntityScan + QueryDSL + AuditorAware.
// DB 없는 서비스(게이트웨이 등)에서는 spring.datasource.url 미설정 시 비활성화된다.
@Configuration
@EnableJpaAuditing
@ConditionalOnProperty(name = "spring.datasource.url")
@EnableJpaRepositories(basePackages = "com.trustamarket")
@EntityScan(basePackages = "com.trustamarket")
public class JpaConfig {

    @PersistenceContext
    private EntityManager em;

    // QueryDSL 진입점. 도메인 서비스가 이미 정의했으면 덮어쓰지 않는다.
    @Bean
    @ConditionalOnMissingBean(JPAQueryFactory.class)
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(em);
    }

    // @CreatedBy / @LastModifiedBy 가 참조. 현재는 고정 UUID.
    // TODO: SecurityUtil 과 연동해 현재 로그인 유저 UUID 반환하도록 교체.
    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> Optional.of(
            UUID.fromString("00000000-0000-0000-0000-000000000000")
        );
    }
}
