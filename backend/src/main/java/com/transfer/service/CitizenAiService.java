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
import java.util.regex.Pattern;

@Service
public class CitizenAiService {

    private static final String EMERGENCY_PHONE = "120";
    /**
     * 严格匹配独立的 120，避免把 111120、1120 等错误号码误判为合法急救电话。
     */
    private static final Pattern STANDALONE_120_PATTERN = Pattern.compile("(?<!\\d)120(?!\\d)");
    /**
     * 修正 AI 偶发生成的异常急救号码，例如 1120、111120、111 120。
     */
    private static final Pattern BROKEN_120_PATTERN = Pattern.compile("(?<!\\d)1{1,}\\s*120(?!\\d)");

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

        /*
         * AI悬浮球是上报页内的开放式市民咨询入口：
         * - 不再做事故上报范围拦截；
         * - 不再因旅游、吃饭、代码等关键词返回 outOfScope=true；
         * - 仍保留伤亡关键词检测字段，便于前端在必要时做安全提示。
         */
        String aiReply = siliconFlowClient.chat(
                citizenSystemPrompt(),
                buildChatPrompt(question, locationName, description),
                700,
                0.6
        );

        String reply = enforceEmergencyPhoneWhenNeeded(
                firstNonBlank(
                        aiReply,
                        fallbackChatReply(question, casualtyDetected)
                ),
                casualtyDetected,
                900
        );

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

        /*
         * 提交成功弹窗属于安全关键提示。这里不再优先采用大模型自由生成文本，
         * 避免出现 111120、1120 等错误号码或不稳定表述。
         * 大模型仍可用于后续分析/咨询，但市民即时安全提示以服务端规则模板为准。
         */
        String fallbackAdvice = String.join(" ", actionItems);
        boolean aiGenerated = false;

        String advice = enforceEmergencyPhoneWhenNeeded(
                fallbackAdvice,
                casualtyDetected,
                260
        );

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

    private String citizenSystemPrompt() {
        return "你是事故上报页面内的AI悬浮球，也是普通市民的通用咨询助手。"
                + "用户可能会询问事故上报、定位、照片/视频上传、现场安全，也可能询问其他临时不懂的问题；请按用户问题正常回答，不要因为问题不属于事故上报就拒答。"
                + "回答应使用中文，尽量清楚、直接、适合普通市民理解。"
                + "涉及交通事故现场时，优先提醒用户确保自身安全；如果上下文出现受伤、昏迷、流血、被困、伤亡等情况，必须提示先拨打120。"
                + "涉及法律、医疗、保险等专业问题时，可以给一般性说明，但不要冒充专业人员作最终判断。";
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
                + "\n页面上下文-事故地点：" + safeOrDefault(locationName, "未填写")
                + "\n页面上下文-事故描述：" + safeOrDefault(description, "未填写")
                + "\n请根据用户问题直接回答。若问题与事故现场有关，请结合页面上下文给出安全、可执行的建议；若问题与事故无关，也不要拒答。";
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

        return "当前AI服务未配置或暂时不可用。我可以先协助你完成事故上报：确认定位、填写事故描述、上传现场照片；其他问题请稍后再试。";
    }

    private String enforceEmergencyPhoneWhenNeeded(
            String value,
            boolean casualtyDetected,
            int maxLength
    ) {
        String text = sanitizeAndLimit(
                normalizeEmergencyPhone(value),
                maxLength
        );

        if (casualtyDetected && !containsStandalone120(text)) {
            text = sanitizeAndLimit(
                    "如现场有人受伤、昏迷、流血或被困，请立即拨打120急救电话。" + text,
                    maxLength
            );
        }

        return text;
    }

    private boolean containsStandalone120(String text) {
        return text != null && STANDALONE_120_PATTERN.matcher(text).find();
    }

    private String normalizeEmergencyPhone(String value) {
        String text = normalizeFullWidthDigits(safe(value));
        text = BROKEN_120_PATTERN.matcher(text).replaceAll(EMERGENCY_PHONE);
        return text;
    }

    private String normalizeFullWidthDigits(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= '０' && ch <= '９') {
                builder.append((char) ('0' + (ch - '０')));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
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
