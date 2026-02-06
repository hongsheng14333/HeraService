package com.core.heraservice.network;

import com.google.gson.JsonElement;
import java.util.List;
public class DataDef {
    // 设备消息
    public static class DeviceMessage {
        String type;
        String requestId;
        JsonElement data;
        long timestamp;
    }

    // 服务器消息
    public static class ServerMessage {
        String type;
        String requestId;
        JsonElement data;
        long timestamp;
    }

    // 心跳数据
    public static class HeartbeatData {
        public List<SimStatus> sims;
        public DeviceStatus device;
    }

    // SIM卡状态
    public static class SimStatus {
        public String slot;
        public int status;
        public double balance;
        public int signal;
        public String operator;
        public String cardNumber;
    }

    // 设备状态
    public static class DeviceStatus {
        public int battery;
        public boolean charging;
        public double temperature;
        public String networkType;
    }

    // 短信发送指令
    public static class SmsSendData {
        public long smsId;
        public String phoneNumber;
        public String content;
        public String simSlot;
    }

    // 短信结果
    public static class SmsResultData {
        long smsId;
        int status;
        String errorMessage;
    }

    // ========== 任务拉取模式数据类 ==========
    /**
     * 设备能力信息
     */
    public class DeviceCapabilities {
        public int maxSmsPerMinute;
        public boolean supportLongSms;

        public DeviceCapabilities(int maxSmsPerMinute, boolean supportLongSms) {
            this.maxSmsPerMinute = maxSmsPerMinute;
            this.supportLongSms = supportLongSms;
        }
    }

    /**
     * 任务查询请求数据
     */
    public class SmsTaskQueryData {
        public String simSlot;
        public int maxCount;
        public DeviceCapabilities capabilities;

        public SmsTaskQueryData(String simSlot, int maxCount, DeviceCapabilities capabilities) {
            this.simSlot = simSlot;
            this.maxCount = maxCount;
            this.capabilities = capabilities;
        }
    }

    /**
     * 分配的短信项
     */
    public class SmsAssignItem {
        public long smsId;
        public String phoneNumber;
        public String content;
        public int priority;
        public long expireAt;
    }

    /**
     * 发送配置
     */
    public class SmsSendConfig {
        public int intervalSeconds;
        public boolean retryOnFail;
    }

    /**
     * 任务分配响应数据
     */
    public class SmsTaskAssignData {
        public long taskId;
        public String taskName;
        public String simSlot;
        public int assignedCount;
        public int totalRemaining;
        public List<SmsAssignItem> smsList;
        public SmsSendConfig sendConfig;
    }

    /**
     * 任务确认数据
     */
    public class SmsTaskAcceptData {
        public long taskId;
        public String simSlot;
        public List<Long> acceptedSmsIds;
        public List<Long> rejectedSmsIds;

        public SmsTaskAcceptData(long taskId, String simSlot, List<Long> acceptedSmsIds) {
            this.taskId = taskId;
            this.simSlot = simSlot;
            this.acceptedSmsIds = acceptedSmsIds;
        }
    }

    /**
     * 任务拒绝数据
     */
    public class SmsTaskRejectData {
        public long taskId;
        public String simSlot;
        public List<Long> smsIds;
        public String reason;
        public String message;

        public SmsTaskRejectData(long taskId, String simSlot, String reason, String message) {
            this.taskId = taskId;
            this.simSlot = simSlot;
            this.reason = reason;
            this.message = message;
        }
    }

    /**
     * 单条短信发送结果
     */
    public class SmsResultItem {
        public long smsId;
        public int status;  // 2=成功 3=失败
        public long sentAt;
        public String errorCode;
        public String errorMessage;

        public SmsResultItem(long smsId, int status, long sentAt, String errorCode, String errorMessage) {
            this.smsId = smsId;
            this.status = status;
            this.sentAt = sentAt;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }
    }

    /**
     * 批量结果汇总
     */
    public class BatchResultSummary {
        public int total;
        public int success;
        public int failed;

        public BatchResultSummary(int total, int success, int failed) {
            this.total = total;
            this.success = success;
            this.failed = failed;
        }
    }

    /**
     * 批量结果上报数据
     */
    public class SmsBatchResultData {
        public long taskId;
        public String simSlot;
        public List<SmsResultItem> results;
        public BatchResultSummary summary;

        public SmsBatchResultData(long taskId, String simSlot, List<SmsResultItem> results, BatchResultSummary summary) {
            this.taskId = taskId;
            this.simSlot = simSlot;
            this.results = results;
            this.summary = summary;
        }
    }

    /**
     * 新任务通知数据
     */
    public class SmsTaskNotifyData {
        public long taskId;
        public String taskName;
        public int priority;
        public int totalCount;
        public List<String> availableForSims;
    }

    /**
     * 服务端推送的完整任务包数据 (SMS_TASK_PUSH)
     */
    public class SmsTaskPushData {
        public long taskId;
        public String taskName;
        public int sendMode;           // 发送模式 1=每卡发全部号码
        public List<SmsAssignItem> smsList;  // 预创建的短信列表
        public int intervalSeconds;    // 发送间隔(秒)
        public int maxPerCard;         // 每卡最大发送数
        public SmsSendConfig sendConfig;
    }

    /**
     * 拒绝原因代码
     */
    public class RejectReason {
        public static final String SIM_NO_SIGNAL = "SIM_NO_SIGNAL";
        public static final String SIM_NO_BALANCE = "SIM_NO_BALANCE";
        public static final String SIM_DISABLED = "SIM_DISABLED";
        public static final String DEVICE_BUSY = "DEVICE_BUSY";
        public static final String QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
        public static final String UNKNOWN = "UNKNOWN";
    }

}
