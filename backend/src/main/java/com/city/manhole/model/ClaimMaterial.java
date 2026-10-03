package com.city.manhole.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 赔付材料：赔付专员核验的佐证材料（行车记录仪、交警证明、维修发票等）。
 */
@Data
@Entity
@Table(name = "claim_material")
public class ClaimMaterial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属赔付案件 */
    @Column(nullable = false)
    private Long caseId;

    /** 材料类型，见 Constants.MaterialType */
    @Column(nullable = false, length = 32)
    private String type;

    /** 材料名称/标题 */
    @Column(nullable = false, length = 128)
    private String title;

    /** 附件 URL（照片/扫描件，可空） */
    @Column(length = 256)
    private String fileUrl;

    /** 内容摘要（如视频时间段、证明编号、发票金额） */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 核验状态，见 Constants.MaterialStatus */
    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    /** 核验备注（存疑原因等） */
    @Column(columnDefinition = "TEXT")
    private String verifyNote;

    /** 提交人 */
    @Column(nullable = false)
    private Long uploadedBy;

    /** 核验人（赔付专员） */
    private Long verifiedBy;

    private LocalDateTime verifiedAt;

    private LocalDateTime createdAt = LocalDateTime.now();
}
