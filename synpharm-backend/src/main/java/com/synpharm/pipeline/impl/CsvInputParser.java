package com.synpharm.pipeline.impl;

import com.synpharm.dto.ParsedInput;
import com.synpharm.enums.InputType;
import com.synpharm.pipeline.InputParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * CSV 输入解析器
 *
 * <p>处理CSV批量文件中的单行输入，格式：配体,靶点（逗号分隔）。
 * 批量处理时，每行调用一次 parse。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Slf4j
@Component
public class CsvInputParser implements InputParser {

    @Override
    public InputType getInputType() {
        return InputType.CSV;
    }

    @Override
    public ParsedInput parse(String inputValue, String fileUrl) {
        log.debug("解析CSV行输入: {}", inputValue);

        if (inputValue == null || inputValue.trim().isEmpty()) {
            throw new IllegalArgumentException("CSV输入行不能为空");
        }

        String[] parts = inputValue.split(",");
        if (parts.length < 2) {
            throw new IllegalArgumentException("CSV行格式错误，需要逗号分隔的配体和靶点");
        }

        List<String> params = Arrays.asList(parts[0].trim(), parts[1].trim());

        return ParsedInput.builder()
                .params(params)
                .inputType(InputType.CSV.getCode())
                .build();
    }
}
