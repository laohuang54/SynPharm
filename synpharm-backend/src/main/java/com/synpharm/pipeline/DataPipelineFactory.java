package com.synpharm.pipeline;

import com.synpharm.dto.ParsedInput;
import com.synpharm.dto.response.AlgoResponse;
import com.synpharm.dto.response.PredictResultResponse;
import com.synpharm.enums.AlgoType;
import com.synpharm.enums.InputType;
import com.synpharm.enums.OutputType;
import com.synpharm.exception.BusinessException;
import com.synpharm.exception.PipelineException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DataPipelineFactory implements PipelineFactory {

    private final Map<InputType, InputParser> parserMap = new HashMap<>();
    private final Map<AlgoType, AlgoExecutor> executorMap = new HashMap<>();
    private final Map<OutputType, OutputFormatter> formatterMap = new HashMap<>();

    public DataPipelineFactory(
            List<InputParser> parsers,
            List<AlgoExecutor> executors,
            List<OutputFormatter> formatters) {
        
        for (InputParser parser : parsers) {
            parserMap.put(parser.getInputType(), parser);
            log.info("注册输入解析器: {}", parser.getInputType().getCode());
        }
        
        for (AlgoExecutor executor : executors) {
            executorMap.put(executor.getAlgoType(), executor);
            log.info("注册算法执行器: {}", executor.getAlgoType().getCode());
        }
        
        for (OutputFormatter formatter : formatters) {
            formatterMap.put(formatter.getOutputType(), formatter);
            log.info("注册输出格式化器: {}", formatter.getOutputType().getCode());
        }
        
        log.info("数据流管道工厂初始化完成: {}个解析器, {}个执行器, {}个格式化器", 
                parserMap.size(), executorMap.size(), formatterMap.size());
    }

    @Override
    public PredictResultResponse process(String inputType, String algoType, String outputType,
                                         String inputValue, String fileUrl) {
        return process(
                InputType.fromCode(inputType),
                AlgoType.fromCode(algoType),
                OutputType.fromCode(outputType),
                inputValue,
                fileUrl
        );
    }

    @Override
    public PredictResultResponse process(InputType inputType, AlgoType algoType, OutputType outputType,
                                         String inputValue, String fileUrl) {
        log.info("执行数据流: {}:{}:{}", inputType.getCode(), algoType.getCode(), outputType.getCode());
        
        InputParser parser = parserMap.get(inputType);
        if (parser == null) {
            throw new BusinessException("不支持的输入类型: " + inputType.getCode());
        }
        
        AlgoExecutor executor = executorMap.get(algoType);
        if (executor == null) {
            throw new BusinessException("不支持的算法类型: " + algoType.getCode());
        }
        
        OutputFormatter formatter = formatterMap.get(outputType);
        if (formatter == null) {
            throw new BusinessException("不支持的输出类型: " + outputType.getCode());
        }
        
        ParsedInput parsedInput;
        try {
            parsedInput = parser.parse(inputValue, fileUrl, algoType.getCode());
        } catch (Exception e) {
            throw PipelineException.parse(e.getMessage(), e);
        }
        
        AlgoResponse algoResult;
        try {
            algoResult = executor.execute(parsedInput);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw PipelineException.execute(e.getMessage(), e);
        }
        
        PredictResultResponse formattedOutput;
        try {
            formattedOutput = formatter.format(algoResult);
        } catch (Exception e) {
            throw PipelineException.format(e.getMessage(), e);
        }
        
        log.info("数据流执行完成");
        return formattedOutput;
    }

    @Override
    public List<PredictResultResponse> batchProcess(String inputType, String algoType, String outputType,
                                                    List<String> inputs) {
        return batchProcess(
                InputType.fromCode(inputType),
                AlgoType.fromCode(algoType),
                OutputType.fromCode(outputType),
                inputs
        );
    }

    @Override
    public List<PredictResultResponse> batchProcess(InputType inputType, AlgoType algoType, OutputType outputType,
                                                    List<String> inputs) {
        log.info("执行批量数据流: {}:{}:{}, 数量: {}", inputType.getCode(), algoType.getCode(), 
                outputType.getCode(), inputs.size());
        
        InputParser parser = parserMap.get(inputType);
        if (parser == null) {
            throw new BusinessException("不支持的输入类型: " + inputType.getCode());
        }
        
        AlgoExecutor executor = executorMap.get(algoType);
        if (executor == null) {
            throw new BusinessException("不支持的算法类型: " + algoType.getCode());
        }
        
        OutputFormatter formatter = formatterMap.get(outputType);
        if (formatter == null) {
            throw new BusinessException("不支持的输出类型: " + outputType.getCode());
        }
        
        List<ParsedInput> parsedInputs = new ArrayList<>();
        for (String input : inputs) {
            try {
                parsedInputs.add(parser.parse(input, null, algoType.getCode()));
            } catch (Exception e) {
                log.warn("解析输入失败: {}", input, e);
            }
        }
        
        List<AlgoResponse> algoResults;
        try {
            algoResults = executor.batchExecute(parsedInputs);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw PipelineException.execute(e.getMessage(), e);
        }
        
        List<PredictResultResponse> formattedOutputs;
        try {
            formattedOutputs = formatter.batchFormat(algoResults);
        } catch (Exception e) {
            throw PipelineException.format(e.getMessage(), e);
        }
        
        log.info("批量数据流执行完成, 结果数量: {}", formattedOutputs.size());
        return formattedOutputs;
    }

    @Override
    public boolean supports(InputType inputType, AlgoType algoType, OutputType outputType) {
        return parserMap.containsKey(inputType) 
                && executorMap.containsKey(algoType) 
                && formatterMap.containsKey(outputType);
    }

    @Override
    public boolean supports(String inputType, String algoType, String outputType) {
        try {
            return supports(
                    InputType.fromCode(inputType),
                    AlgoType.fromCode(algoType),
                    OutputType.fromCode(outputType)
            );
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}