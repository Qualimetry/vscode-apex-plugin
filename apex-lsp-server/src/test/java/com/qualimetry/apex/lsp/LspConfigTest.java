/*
 * Copyright 2026 SHAZAM Analytics Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qualimetry.apex.lsp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qualimetry.sonar.apex.analyzer.checks.CheckList;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LspConfigTest {

    private static final String DEFAULT_RULE = CheckList.getDefaultRuleKeys().get(0);

    private static String nonDefaultRule() {
        return CheckList.getAllRuleKeys().stream()
                .filter(k -> !CheckList.getDefaultRuleKeys().contains(k))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void defaultsEnableDefaultProfileRules() {
        LspConfig config = LspConfig.defaults();

        assertThat(config.isRuleEnabled(DEFAULT_RULE)).isTrue();
        assertThat(config.isRuleEnabled(nonDefaultRule())).isFalse();
    }

    @Test
    void nullOrNonMapSectionYieldsDefaults() {
        assertThat(LspConfig.fromConfiguration(null).isRuleEnabled(DEFAULT_RULE)).isTrue();
        assertThat(LspConfig.fromConfiguration("nonsense").isRuleEnabled(DEFAULT_RULE)).isTrue();
    }

    @Test
    void ruleOverrideDisablesDefaultRule() {
        LspConfig config = LspConfig.fromConfiguration(Map.of(
                "rules", Map.of(DEFAULT_RULE, Map.of("enabled", false))
        ));

        assertThat(config.isRuleEnabled(DEFAULT_RULE)).isFalse();
    }

    @Test
    void ruleOverrideEnablesNonDefaultRule() {
        String rule = nonDefaultRule();
        LspConfig config = LspConfig.fromConfiguration(Map.of(
                "rules", Map.of(rule, Map.of("enabled", true))
        ));

        assertThat(config.isRuleEnabled(rule)).isTrue();
    }

    @Test
    void rulesReplaceDefaultsTurnsOffUnlistedRules() {
        String listed = nonDefaultRule();
        LspConfig config = LspConfig.fromConfiguration(Map.of(
                "rulesReplaceDefaults", true,
                "rules", Map.of(listed, Map.of("enabled", true))
        ));

        assertThat(config.isRuleEnabled(listed)).isTrue();
        assertThat(config.isRuleEnabled(DEFAULT_RULE)).isFalse();
    }

    @Test
    void rulesReplaceDefaultsWithEmptyRulesKeepsDefaultProfile() {
        LspConfig config = LspConfig.fromConfiguration(Map.of(
                "rulesReplaceDefaults", true,
                "rules", Map.of()
        ));

        assertThat(config.isRuleEnabled(DEFAULT_RULE)).isTrue();
    }

    @Test
    void severityOverrideApplied() {
        LspConfig config = LspConfig.fromConfiguration(Map.of(
                "rules", Map.of(DEFAULT_RULE, Map.of("enabled", true, "severity", "critical"))
        ));

        assertThat(config.getSeverity(DEFAULT_RULE)).isEqualTo(DiagnosticSeverity.Error);
        assertThat(config.getSeverity("some-other-rule")).isEqualTo(DiagnosticSeverity.Warning);
    }

    @Test
    void parsesGsonJsonObjectSection() {
        String listed = nonDefaultRule();
        JsonObject section = JsonParser.parseString(
                "{\"rulesReplaceDefaults\": true, \"rules\": {\"" + listed + "\": {\"enabled\": true, \"severity\": \"minor\"}}}"
        ).getAsJsonObject();

        LspConfig config = LspConfig.fromConfiguration(section);

        assertThat(config.isRuleEnabled(listed)).isTrue();
        assertThat(config.isRuleEnabled(DEFAULT_RULE)).isFalse();
        assertThat(config.getSeverity(listed)).isEqualTo(DiagnosticSeverity.Information);
    }

    @Test
    void legacyDisabledListSupported() {
        LspConfig config = LspConfig.fromConfiguration(Map.of(
                "rules", Map.of("disabled", java.util.List.of(DEFAULT_RULE))
        ));

        assertThat(config.isRuleEnabled(DEFAULT_RULE)).isFalse();
    }
}
