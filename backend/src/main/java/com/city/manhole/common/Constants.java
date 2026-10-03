package com.city.manhole.common;

/** 平台全部状态与角色常量 */
public final class Constants {
    private Constants() {}

    /** 角色 */
    public static final class Role {
        public static final String CITIZEN = "CITIZEN";           // 市民
        public static final String STREET_DUTY = "STREET_DUTY";   // 街道值班员
        public static final String OWNER_UNIT = "OWNER_UNIT";     // 权属单位
        public static final String REPAIR_TEAM = "REPAIR_TEAM";   // 维修队
        public static final String INSPECTOR = "INSPECTOR";       // 城管巡查员
        public static final String COMPENSATOR = "COMPENSATOR";   // 赔付专员
        public static final String ADMIN = "ADMIN";               // 城管管理员
    }

    /** 事件主流程状态 */
    public static final class IncidentStatus {
        public static final String REPORTED = "REPORTED";               // 已上报，待应急处置
        public static final String SECURED = "SECURED";                 // 已临时围护，待权属确认
        public static final String OWNER_CONFIRMED = "OWNER_CONFIRMED"; // 权属已确认，待派单维修
        public static final String REPAIRING = "REPAIRING";             // 维修中
        public static final String REPAIRED = "REPAIRED";               // 维修完成，待验收
        public static final String ACCEPTED = "ACCEPTED";               // 验收通过，待费用复核
        public static final String CLOSED = "CLOSED";                   // 已结案
    }

    /** 应急处置线状态（只看现场风险与到场时效） */
    public static final class EmergencyStatus {
        public static final String PENDING = "PENDING";       // 待派单
        public static final String DISPATCHED = "DISPATCHED"; // 已派单，待到场
        public static final String SECURED = "SECURED";       // 已完成临时围护
    }

    /** 权属追责线状态 */
    public static final class OwnerStatus {
        public static final String UNCONFIRMED = "UNCONFIRMED"; // 未确认
        public static final String INVESTIGATING = "INVESTIGATING"; // 排查中
        public static final String CONFIRMED = "CONFIRMED";     // 已确认/认领
        public static final String PENDING = "PENDING";         // 责任待定（街道先行处置）
    }

    /** 维修工单状态 */
    public static final class RepairStatus {
        public static final String ASSIGNED = "ASSIGNED";   // 已派发，待接单
        public static final String ACCEPTED = "ACCEPTED";   // 已接单，维修中
        public static final String FINISHED = "FINISHED";   // 维修完成，待验收
        public static final String RETURNED = "RETURNED";   // 验收不通过，退回重修
    }

    /** 赔付案件状态 */
    public static final class CompStatus {
        public static final String FILED = "FILED";         // 已立案，待审核
        public static final String APPROVED = "APPROVED";   // 已核定，待赔付
        public static final String REJECTED = "REJECTED";   // 已驳回
        public static final String PAID = "PAID";           // 已赔付
    }

    /** 费用分摊状态 */
    public static final class CostStatus {
        public static final String PENDING = "PENDING";       // 待复核分摊
        public static final String ALLOCATED = "ALLOCATED";   // 已分摊结案
    }

    /** 赔付责任认定 */
    public static final class Responsibility {
        public static final String OWNER = "OWNER";             // 权属单位全责
        public static final String STREET = "STREET";           // 街道（围护不及时）责任
        public static final String SHARED = "SHARED";           // 多方分摊
        public static final String UNDETERMINED = "UNDETERMINED"; // 无法认定
    }

    /** 赔付材料类型（赔付专员逐项核验） */
    public static final class MaterialType {
        public static final String ACCIDENT_TIME = "ACCIDENT_TIME";     // 事故时间
        public static final String DASHCAM = "DASHCAM";                 // 行车记录仪
        public static final String POLICE_CERT = "POLICE_CERT";         // 交警证明
        public static final String REPAIR_INVOICE = "REPAIR_INVOICE";   // 维修发票
        public static final String PLATFORM_STATUS = "PLATFORM_STATUS"; // 平台当时井盖状态
    }

    /** 材料核验状态 */
    public static final class VerifyStatus {
        public static final String PENDING = "PENDING";   // 待核验
        public static final String PASSED = "PASSED";     // 核验通过
        public static final String FAILED = "FAILED";     // 核验不通过
    }

    /** 事故时间与首次上报的关系（系统据事故时间自动判定） */
    public static final class TimeRelation {
        public static final String BEFORE_REPORT = "BEFORE_REPORT";                     // 事故在首次上报前：平台仅记录风险线索
        public static final String AFTER_REPORT_UNSECURED = "AFTER_REPORT_UNSECURED";   // 上报后、围护完成前：关联值班处置时效与权属响应
        public static final String AFTER_REPORT_SECURED = "AFTER_REPORT_SECURED";       // 围护完成后
    }

    /** 赔付后维修追踪状态（赔付审核通过后跟踪维修是否彻底完成） */
    public static final class RepairTrack {
        public static final String TRACKING = "TRACKING";       // 追踪中（维修未彻底完成）
        public static final String COMPLETED = "COMPLETED";     // 维修已彻底完成（验收通过）
        public static final String RECOLLAPSED = "RECOLLAPSED"; // 同一井盖再次塌陷
    }
}
