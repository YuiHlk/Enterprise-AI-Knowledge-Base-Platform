package com.test.qa.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt 模板动态渲染引擎
 *
 * 将模板中的 {{variable}} 占位符替换为实际值。
 * 模板示例："请根据以下资料回答：{{context}}\n\n问题：{{question}}"
 *
 * 工程注意：
 * - 模板与变量不匹配时（变量缺失/多余）可能导致 Prompt 异常，需要日志告警
 * - 不做 HTML 转义，因为这是发给 LLM 的纯文本
 */
@Service
public class PromptRenderService {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    /**
     * 渲染模板
     *
     * @param template  包含 {{variable}} 占位符的模板字符串
     * @param variables 变量名 → 变量值的映射
     * @return 渲染后的完整 Prompt
     */
    public String render(String template, Map<String, String> variables) {
        if (template == null || template.isEmpty()) {
            return "";
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement = variables.getOrDefault(varName, "");
            // 转义替换值中的特殊字符，避免正则替换异常
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
