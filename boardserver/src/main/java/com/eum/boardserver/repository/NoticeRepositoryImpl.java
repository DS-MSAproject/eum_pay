package com.eum.boardserver.repository;

import com.eum.boardserver.dto.NoticeSearchCondition;
import static com.eum.boardserver.entity.QNotice.notice;

import com.eum.boardserver.entity.Notice;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static org.springframework.util.StringUtils.hasText;

@RequiredArgsConstructor
public class NoticeRepositoryImpl implements NoticeRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Notice> searchNotices(NoticeSearchCondition condition) {
        return queryFactory
                .selectFrom(notice)
                .where(
                        searchKeyword(condition.searchType(), condition.keyword()), // 통합 검색 분기
                        categoryEq(condition.category()),
                        isPinnedEq(condition.isPinned())
                )
                .orderBy(notice.isPinned.desc(), notice.createdAt.desc())
                .fetch();
    }

    // 💡 핵심: searchType에 따른 동적 조건 생성
    private BooleanExpression searchKeyword(String searchType, String keyword) {
        if (!hasText(keyword)) return null;

        return switch (searchType) {
            case "TITLE" -> notice.title.contains(keyword);
            case "CONTENT" -> notice.content.contains(keyword);
            case "ALL" -> notice.title.contains(keyword).or(notice.content.contains(keyword));
            default -> notice.title.contains(keyword); // 기본값은 제목 검색
        };
    }

    private BooleanExpression titleEq(String title) {
        return hasText(title) ? notice.title.eq(title) : null;
    }

    private BooleanExpression contentContains(String content) {
        return hasText(content) ? notice.content.contains(content) : null;
    }

    private BooleanExpression categoryEq(String category) {
        return hasText(category) ? notice.category.eq(category) : null;
    }

    private BooleanExpression isPinnedEq(Boolean isPinned) {
        return isPinned != null ? notice.isPinned.eq(isPinned) : null;
    }
}
