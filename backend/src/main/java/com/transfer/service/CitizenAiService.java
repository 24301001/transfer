package com.transfer.service;

import com.transfer.adapter.SiliconFlowClient;
import com.transfer.dto.CitizenAiChatRequest;
import com.transfer.dto.CitizenAiChatResponse;
import com.transfer.dto.CitizenImmediateAdviceResponse;
import com.transfer.model.Incident;
import com.transfer.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CitizenAiService {

    private static final String EMERGENCY_PHONE = "120";

    private final SiliconFlowClient siliconFlowClient;
    private final IncidentRepository incidentRepository;

    public CitizenAiService(
            SiliconFlowClient siliconFlowClient,
            IncidentRepository incidentRepository
    ) {
        this.siliconFlowClient = siliconFlowClient;
        this.incidentRepository = incidentRepository;
    }

    @Transactional(readOnly = true)
    public CitizenAiChatResponse chat(
            CitizenAiChatRequest request
    ) {
        String question = safe(request.question()).trim();
        Incident incident = request.incidentId() == null
                ? null
                : incidentRepository
                .findById(request.incidentId())
                .orElse(null);

        String locationName = firstNonBlank(
                incident == null ? null : incident.getLocationName(),
                request.locationName()
        );

        String description = firstNonBlank(
                incident == null ? null : incident.getDescription(),
                request.description()
        );

        boolean casualtyDetected = hasCasualtyRisk(
                question,
                description,
                incident == null ? null : incident.getInitialAccidentType(),
                incident == null ? null : incident.getConfirmedAccidentType()
        );

        if (!isInReportScope(question)) {
            String reply = casualtyDetected
                    ? "我只能协助事故上报和现场安全。有人受伤时请先拨打120，并在确保自身安全的前提下等待救援。"
                    : "我只能协助事故上报、定位、照片/视频上传和现场安全。请按页面要求填写地点、事故描述并上传现场照片；如有人员受伤请先拨打120。";

            return new CitizenAiChatResponse(
                    reply,
                    true,
                    casualtyDetected,
                    casualtyDetected,
                    casualtyDetected ? EMERGENCY_PHONE : null
            );
        }

        String aiReply = siliconFlowClient.chat(
                citizenSystemPrompt(),
                buildChatPrompt(question, locationName, description),
                220,
                0.2
        );

        String reply = sanitizeAndLimit(
                firstNonBlank(
                        aiReply,
                        fallbackChatReply(question, casualtyDetected)
                ),
                220
        );

        if (casualtyDetected && !reply.contains("120")) {
            reply = "如有人受伤，请先拨打120。" + reply;
            reply = sanitizeAndLimit(reply, 220);
        }

        return new CitizenAiChatResponse(
                reply,
                false,
                casualtyDetected,
                casualtyDetected,
                casualtyDetected ? EMERGENCY_PHONE : null
        );
    }

    public CitizenImmediateAdviceResponse generateImmediateAdvice(
            Incident incident
    ) {
        boolean casualtyDetected = hasCasualtyRisk(
                incident.getDescription(),
                incident.getInitialAccidentType(),
                incident.getConfirmedAccidentType()
        );

        List<String> actionItems = buildCitizenActionItems(
                incident,
                casualtyDetected
        );

        String fallbackAdvice = String.join(" ", actionItems);

        String aiReply = siliconFlowClient.chat(
                immediateAdviceSystemPrompt(),
                buildImmediateAdvicePrompt(
                        incident,
                        casualtyDetected,
                        actionItems
                ),
                260,
                0.2
        );

        boolean aiGenerated = aiReply != null && !aiReply.isBlank();

        String advice = sanitizeAndLimit(
                firstNonBlank(aiReply, fallbackAdvice),
                260
        );

        if (casualtyDetected && !advice.contains("120")) {
            advice = "如发现人员受伤，请立即拨打120。" + advice;
            advice = sanitizeAndLimit(advice, 260);
        }

        return new CitizenImmediateAdviceResponse(
                "信息已提交，请先保证自身安全，保持手机畅通。",
                advice,
                actionItems,
                casualtyDetected,
                casualtyDetected,
                casualtyDetected ? EMERGENCY_PHONE : null,
                aiGenerated
        );
    }

    public boolean hasCasualtyRisk(String... texts) {
        String text = join(texts).toLowerCase(Locale.ROOT);
        return containsAny(
                text,
                "受伤",
                "伤者",
                "伤员",
                "伤亡",
                "死亡",
                "流血",
                "出血",
                "昏迷",
                "晕倒",
                "倒地",
                "骨折",
                "被困",
                "夹住",
                "无法动弹",
                "呼吸困难",
                "孕妇",
                "儿童受伤",
                "小孩受伤",
                "injury",
                "injured",
                "bleeding",
                "unconscious",
                "trapped",
                "fatal"
        );
    }

    private boolean isInReportScope(String question) {
        String text = safe(question).toLowerCase(Locale.ROOT).trim();

        if (text.isBlank()) {
            return true;
        }

        /*
         * 明确越界的问题必须直接拦截。
         * 不能因为当前页面存在事故描述，就把“旅游/娱乐/股票”等问题错误判定为事故上报问题。
         */
        if (containsAny(
                text,
                "旅游",
                "景点",
                "去哪玩",
                "吃饭",
                "餐厅",
                "酒店",
                "电影",
                "游戏",
                "购物",
                "股票",
                "基金",
                "彩票",
                "作业",
                "论文",
                "代码",
                "维修费",
                "修车",
                "赔偿",
                "赔钱",
                "定责",
                "责任划分",
                "罚款",
                "扣分",
                "保险理赔",
                "医学诊断",
                "用药",
                "weather",
                "travel",
                "stock",
                "movie",
                "game"
        )) {
            return false;
        }

        if (containsAny(
                text,
                "事故",
                "车祸",
                "追尾",
                "碰撞",
                "撞",
                "剐蹭",
                "刮蹭",
                "上报",
                "提交",
                "上传",
                "照片",
                "图片",
                "视频",
                "定位",
                "位置",
                "地点",
                "地址",
                "报警",
                "120",
                "110",
                "122",
                "交警",
                "受伤",
                "伤亡",
                "流血",
                "昏迷",
                "被困",
                "安全",
                "三角牌",
                "警示牌",
                "救护",
                "撤离",
                "高速",
                "车道",
                "占道",
                "堵车",
                "拥堵",
                "report",
                "upload",
                "location",
                "accident",
                "crash",
                "photo",
                "video"
        )) {
            return true;
        }

        /*
         * 上报页面内常见的短问题。
         * 只放页面操作相关语义，不再用“长度小于 12”兜底。
         */
        return containsAny(
                text,
                "怎么办",
                "怎么做",
                "怎么处理",
                "怎么填",
                "填什么",
                "下一步",
                "需要什么",
                "不会填",
                "不懂",
                "可以吗",
                "要不要"
        );
    }

    private String citizenSystemPrompt() {
        return "你是交通事故上报页面内的市民辅助AI。只能回答事故上报、定位、照片/视频上传、现场安全、报警求助相关问题。"
                + "不要回答法律定责、赔偿金额、医学诊断、维修报价、复杂交通调度和与事故无关的问题。"
                + "回复必须中文、简短、安抚、可执行，不超过120字。有人受伤、昏迷、流血、被困时必须提示先拨打120。";
    }

    private String immediateAdviceSystemPrompt() {
        return "你是交通事故上报后的即时安全提示AI，面向普通市民。"
                + "只输出安抚性文字和普通市民立即能做的简单处置，不输出正式指挥调度方案。"
                + "不要编造交警、清障、道路恢复等系统未提供的信息。中文回复，不超过150字。"
                + "检测到人员受伤、昏迷、流血、被困、伤亡时必须提示立即拨打120。";
    }

    private String buildChatPrompt(
            String question,
            String locationName,
            String description
    ) {
        return "用户问题：" + safe(question)
                + "\n事故地点：" + safeOrDefault(locationName, "未填写")
                + "\n事故描述：" + safeOrDefault(description, "未填写")
                + "\n请只给事故上报页面内可执行的简短帮助。";
    }

    private String buildImmediateAdvicePrompt(
            Incident incident,
            boolean casualtyDetected,
            List<String> actionItems
    ) {
        return "事故地点：" + safeOrDefault(incident.getLocationName(), "未填写")
                + "\n事故描述：" + safeOrDefault(incident.getDescription(), "未填写")
                + "\n事故类型：" + safeOrDefault(incident.getInitialAccidentType(), "未填写")
                + "\n是否检测到人员受伤风险：" + casualtyDetected
                + "\n允许建议：" + actionItems
                + "\n请生成提交成功后的即时安全提示，禁止输出正式调度方案。";
    }

    private List<String> buildCitizenActionItems(
            Incident incident,
            boolean casualtyDetected
    ) {
        List<String> items = new ArrayList<>();

        if (casualtyDetected) {
            items.add("如有人受伤、昏迷、流血或被困，请立即拨打120。");
        }

        items.add("确保自身安全，撤离到护栏外或安全区域，不要站在车道内。 ");
        items.add("在安全前提下开启双闪，并在来车方向放置警示标志。 ");
        items.add("不要随意移动车辆和伤者，除非现场存在起火、爆炸等即时危险。 ");
        items.add("保持手机畅通，等待交警或救援人员联系。 ");

        if (incident.getBaiduLongitude() == null
                || incident.getBaiduLatitude() == null) {
            items.add("请确认定位是否准确，必要时补充道路名称或明显地标。 ");
        }

        return items;
    }

    private String fallbackChatReply(
            String question,
            boolean casualtyDetected
    ) {
        if (casualtyDetected) {
            return "请先保证自身安全；有人受伤、昏迷、流血或被困时，立即拨打120。随后按页面填写地点、描述并上传现场照片。";
        }

        if (containsAny(question, "定位", "位置", "地点")) {
            return "请点击自动定位，在地图上确认事故点；如定位偏差，请手动拖动或补充道路名称、方向和附近地标。";
        }

        if (containsAny(question, "照片", "图片", "上传", "视频")) {
            return "请优先上传能看清事故车辆、车道占用和现场环境的照片；视频为可选，确保安全后再录制。";
        }

        return "请先确保安全，再填写事故地点、事故描述、事故类型并上传照片；如有人受伤，请先拨打120。";
    }

    private String sanitizeAndLimit(
            String value,
            int maxLength
    ) {
        String text = safe(value)
                .replace("```", "")
                .replace("#", "")
                .replace("*", "")
                .replace("\r", " ")
                .replace("\n", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength - 1) + "…";
    }

    private boolean containsAny(
            String text,
            String... words
    ) {
        if (text == null) {
            return false;
        }

        String normalized = text.toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (word != null
                    && normalized.contains(
                    word.toLowerCase(Locale.ROOT)
            )) {
                return true;
            }
        }
        return false;
    }

    private String join(String... texts) {
        if (texts == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (String text : texts) {
            if (text != null && !text.isBlank()) {
                builder.append(text).append(' ');
            }
        }
        return builder.toString();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safeOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank()
                ? defaultValue
                : value.trim();
    }
}
