package com.eum.boardserver.repository;

import com.eum.boardserver.dto.NoticeSearchCondition;
import com.eum.boardserver.entity.Notice;

import java.util.List;

public interface NoticeRepositoryCustom {
    // 동적 검색을 위한 메서드 (카테고리, 키워드 등)
    List<Notice> searchNotices(NoticeSearchCondition condition);
}
