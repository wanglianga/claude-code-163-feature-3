package com.city.manhole.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 赔付材料核验项：赔付专员定责前需逐项核验的五类材料——
 * 事故时间、行车记录仪、交警证明、维修发票、平台当时井盖状态。
 * 立案时自动创建五项（待提交/待核验），其中“事故时间”“平台当时井盖状态”由系统
 * 在录入事故时间后自动填充快照内容，专员核验确认。
 */
@Data
@Entity
@Table(name = "claim_material", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"caseId", "materialType"})
})
public class ClaimMaterial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属赔付案件 */
    @Column(nullable = false)
    private Long caseId;

    /** 材料类型，见 Constants.MaterialType */
    @Column(nullable = false, length = 32)
    private String materialType;

    /** 是否已提交材料 */
    @Column(nullable = false)
    private Boolean submitted = false;

    /** 材料内容说明（如事故时间、发票号与金额、平台状态快照） */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 材料附件（行车记录仪视频截图、交警证明、发票照片等） */
    @Column(length = 256)
    private String fileUrl;

    /** 核验状态，见 Constants.VerifyStatus */
    @Column(nullable = false, length = 16)
    private String verifyStatus = "PENDING";

    /** 核验意见 */
    @Column(columnDefinition = "TEXT")
    private String verifyNote;

    /** 核验人（赔付专员） */
    private Long verifiedBy;

    private LocalDateTime verifiedAt;

    private LocalDateTime createdAt = LocalDateTime.now();
}
