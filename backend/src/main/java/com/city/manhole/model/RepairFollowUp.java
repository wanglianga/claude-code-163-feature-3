package com.city.manhole.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 赔付后维修追踪：赔付审核通过后，平台继续追踪对应井盖维修是否彻底完成；
 * 同一井盖再次塌陷时标记 RECOLLAPSED 并关联新事件，上一轮维修单位、
 * 材料规格与验收人进入新的责任判断。
 */
@Data
@Entity
@Table(name = "repair_follow_up")
public class RepairFollowUp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的赔付案件（一案一追踪） */
    @Column(nullable = false, unique = true)
    private Long caseId;

    @Column(nullable = false)
    private Long incidentId;

    /** 见 Constants.FollowUpStatus */
    @Column(nullable = false, length = 16)
    private String status = "TRACKING";

    /** 追踪记录/说明 */
    @Column(columnDefinition = "TEXT")
    private String checkNote;

    /** 再次塌陷的新事件 id（status = RECOLLAPSED 时写入） */
    private Long linkedIncidentId;

    /** 确认人（赔付专员） */
    private Long confirmedBy;

    private LocalDateTime completedAt;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
}
