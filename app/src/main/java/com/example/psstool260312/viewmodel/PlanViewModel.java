package com.example.psstool260312.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.psstool260312.pssDo.Crew;

import java.util.HashMap;
import java.util.Map;

public class PlanViewModel extends AndroidViewModel {

    // 选择的船员
    private final MutableLiveData<Crew> selectedCrew = new MutableLiveData<>();

    // 目标值 (key: 属性字段, value: 目标值)
    private final MutableLiveData<Map<String, Float>> targetValues = new MutableLiveData<>(new HashMap<>());

    // 装备值 (key: 属性字段, value: 长度为4的数组 [装备1主, 装备1副, 装备2主, 装备2副])
    private final MutableLiveData<Map<String, float[]>> equipmentValues = new MutableLiveData<>(new HashMap<>());

    // 计算出的所需训练值 (key: 属性字段, value: 训练值)
    private final MutableLiveData<Map<String, Integer>> requiredTrain = new MutableLiveData<>(new HashMap<>());

    // 基础属性 (key: 属性字段, value: 基础值)
    private final Map<String, Float> baseValues = new HashMap<>();

    // 训练上限
    private int trainLimit = 110;

    public PlanViewModel(@NonNull Application application) {
        super(application);
    }

    // ========== Getter 方法 ==========
    public MutableLiveData<Crew> getSelectedCrew() {
        return selectedCrew;
    }

    public MutableLiveData<Map<String, Float>> getTargetValues() {
        return targetValues;
    }

    public MutableLiveData<Map<String, float[]>> getEquipmentValues() {
        return equipmentValues;
    }

    public MutableLiveData<Map<String, Integer>> getRequiredTrain() {
        return requiredTrain;
    }

    // 获取基础属性 Map
    public Map<String, Float> getBaseValues() {
        return baseValues;
    }

    // 获取训练上限
    public int getTrainLimit() {
        return trainLimit;
    }

    // ========== Setter 方法 ==========
    // 设置基础属性值
    public void setBaseValue(String attr, float value) {
        baseValues.put(attr, value);
    }

    // 设置训练上限
    public void setTrainLimit(int limit) {
        this.trainLimit = limit;
    }

    // ========== 便捷更新方法 ==========
    // 更新某个属性的目标值
    public void updateTargetValue(String attr, float value) {
        Map<String, Float> map = targetValues.getValue();
        if (map == null) map = new HashMap<>();
        map.put(attr, value);
        targetValues.setValue(map);
    }

    // 更新某个属性的装备值
    public void updateEquipmentValue(String attr, float[] eq) {
        Map<String, float[]> map = equipmentValues.getValue();
        if (map == null) map = new HashMap<>();
        map.put(attr, eq);
        equipmentValues.setValue(map);
    }

    // 更新所需训练值
    public void updateRequiredTrain(Map<String, Integer> trainMap) {
        requiredTrain.setValue(trainMap);
    }

    // ========== 重置数据 ==========
    // 重置所有数据，用于新建方案
    public void reset() {
        selectedCrew.setValue(null);
        targetValues.setValue(new HashMap<>());
        equipmentValues.setValue(new HashMap<>());
        requiredTrain.setValue(new HashMap<>());
        baseValues.clear();
        trainLimit = 110;
    }
}