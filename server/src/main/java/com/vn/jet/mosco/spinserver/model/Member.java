package com.vn.jet.mosco.spinserver.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Bảng từ điển lưu danh sách các thành viên (VD: SeoYeon, HyeRin...)
 * Theo chuẩn Chuẩn hóa 3NF và Local-First.
 */
@Entity
@Table(name = "members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    public Member(String name) {
        this.name = name;
    }
}
