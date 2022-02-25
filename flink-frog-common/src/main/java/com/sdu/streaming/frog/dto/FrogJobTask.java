package com.sdu.streaming.frog.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class FrogJobTask implements Serializable {
    // 任务名
    private String name;
    // 是否流模式
    private boolean streaming;
    // 物料(表)
    private List<String> materials;
    // 计算
    private List<String> calculates;
}
