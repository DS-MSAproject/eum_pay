package com.eum.productserver.entity;

import com.eum.productserver.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

//@Builder
@Getter
@Entity
@Table(name = "categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long id;

    @Column(name = "category_name", nullable = false, unique = true)
    private String categoryName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private List<Category> children = new ArrayList<>();

    @OneToMany(mappedBy = "category")
    private List<Product> products = new ArrayList<>();

    // 정렬 순서
    private Integer displayOrder;

    @Builder
    public Category(String categoryName, Integer displayOrder) {
        this.categoryName = categoryName;
        this.displayOrder = (displayOrder != null) ? displayOrder : 0;
    }

    // 이름 수정
    public void updateCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    // 순서 수정
    public void updateDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public void setParent(Category parent) {
        // 기존 관계가 있다면 제거 (중복 방지)
        if (this.parent != null) {
            this.parent.getChildren().remove(this);
        }

        this.parent = parent;

        // 새 부모가 null이 아니면 자식 리스트에 추가
        if (parent != null && !parent.getChildren().contains(this)) {
            parent.getChildren().add(this);
        }
    }
}