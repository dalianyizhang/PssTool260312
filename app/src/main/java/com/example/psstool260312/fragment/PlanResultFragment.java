package com.example.psstool260312.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.example.psstool260312.R;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

public class PlanResultFragment extends BaseStepFragment {

    private TextView resultText, tvCrewName, summaryText;
    private final String[] ATTR_KEYS = {"h", "a", "r", "b", "st", "p", "s", "e", "w", "f"};
    private final String[] ATTR_NAMES = {"血量", "攻击", "维修", "能力", "耐力", "导航", "科技", "引擎", "武器", "火抗"};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ScrollView scrollView = new ScrollView(requireContext());
        View content = inflater.inflate(R.layout.fragment_plan_result, scrollView, false);
        scrollView.addView(content);
        return scrollView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        resultText = view.findViewById(R.id.tv_result);
        tvCrewName = view.findViewById(R.id.tv_crew_name);
        summaryText = view.findViewById(R.id.tv_summary);

        // 帮助按钮
        ImageButton btnHelp = view.findViewById(R.id.btn_help_plan_result);
        btnHelp.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("页面说明")
                    .setMessage("本页显示根据目标值、装备值、基础值计算出各属性所需的训练值。\n" +
                            "• 括号内为该属性的训练值可达到的理论精确值（保留两位小数），用于帮助判断取整误差。\n" +
                            "• 若总训练值超过训练上限，请返回上一页调整目标值规划。")
                    .setPositiveButton("知道了", null)
                    .show();
        });
    }

    @Override
    protected void observeData() {
        viewModel.getTargetValues().observe(getViewLifecycleOwner(), targets -> {
            if (targets != null) calculate();
        });
        viewModel.getEquipmentValues().observe(getViewLifecycleOwner(), equips -> {
            if (equips != null) calculate();
        });
        viewModel.getSelectedCrew().observe(getViewLifecycleOwner(), crew -> {
            if (crew != null) {
                tvCrewName.setText(crew.getNameZh());
            } else {
                tvCrewName.setText("未选择船员");
            }
            calculate();
        });
    }

    private boolean isTrainExceedLimit() {
        Map<String, Integer> required = viewModel.getRequiredTrain().getValue();
        if (required == null) return false;
        int totalTrain = 0;
        for (int v : required.values()) totalTrain += v;
        int limit = viewModel.getTrainLimit();
        return totalTrain > limit;
    }

    @SuppressLint("DefaultLocale")
    private void calculate() {
        if (viewModel.getSelectedCrew().getValue() == null) return;
        Map<String, Float> targets = viewModel.getTargetValues().getValue();
        Map<String, float[]> equips = viewModel.getEquipmentValues().getValue();
        if (targets == null || equips == null) return;

        Map<String, Float> baseValues = viewModel.getBaseValues();
        int trainLimit = viewModel.getTrainLimit();

        StringBuilder resultSb = new StringBuilder();
        Map<String, Integer> requiredTrain = new HashMap<>();
        int totalTrain = 0;

        for (String key : ATTR_KEYS) {
            float target = targets.getOrDefault(key, 0f);
            if (target <= 0) continue;

            float base = baseValues.getOrDefault(key, 0f);
            float[] eqs = equips.get(key);
            float equipSum = (eqs == null || eqs.length < 2) ? 0f : eqs[0] + eqs[1];

            float currentValue;   // 当前属性值（基础+装备）
            float trainRaw;       // 理论所需训练值（未取整）
            float finalValueRaw;  // 理论最终属性值（基于理论训练值）
            int train;            // 向上取整后的训练值

            if ("f".equals(key)) {
                // 火抗无训练值
                currentValue = base;
                trainRaw = 0;
                finalValueRaw = base;
                train = 0;
            } else if ("st".equals(key)) {
                // 耐力：训练值 = 目标 - 装备总和
                currentValue = equipSum;
                trainRaw = target - equipSum;
                train = (int) Math.ceil(Math.max(0, trainRaw));
                finalValueRaw = currentValue + train; // 理论最终值 = 当前值 + 理论训练值
            } else if ("b".equals(key)) {
                // 能力：目标 = 基础 * (100+训练)/100 * (100+装备总和)/100
                if (base != 0) {
                    currentValue = base * (1 + equipSum / 100f);
                    trainRaw = (target / (base * (1 + equipSum / 100f)) - 1) * 100;
                    train = (int) Math.ceil(Math.max(0, trainRaw));
                    finalValueRaw = base * (1 + (train / 100f)) * (1 + equipSum / 100f);
                } else {
                    currentValue = 0;
                    trainRaw = 0;
                    finalValueRaw = 0;
                    train = 0;
                }
            } else if ("h".equals(key)) {
                // 生命属性：修正值0.5
                if (base != 0) {
                    currentValue = base + equipSum;
                    float calcTarget = target - 0.5f;
                    trainRaw = ((calcTarget - equipSum) * 100f / base) - 100;
                    train = (int) Math.ceil(Math.max(0, trainRaw));
                    finalValueRaw = base * (1 + (train / 100f)) + equipSum;
                } else {
                    currentValue = 0;
                    trainRaw = 0;
                    finalValueRaw = 0;
                    train = 0;
                }
            } else {
                // 普通属性（攻击、维修、武器、引擎、科技、导航）：修正值0.05
                if (base != 0) {
                    currentValue = base + equipSum;
                    float calcTarget = target - 0.05f;
                    trainRaw = ((calcTarget - equipSum) / (base / 100f)) - 100;
                    train = (int) Math.ceil(Math.max(0, trainRaw));
                    finalValueRaw = base * (1 + (train / 100f)) + equipSum;
                } else {
                    currentValue = 0;
                    trainRaw = 0;
                    finalValueRaw = 0;
                    train = 0;
                }
            }

            requiredTrain.put(key, train);
            totalTrain += train;

            // 显示格式：属性名：训练值 X，当前值→目标值（理论最终值）
            resultSb.append(getAttrName(key))
                    .append("：训练值 ").append(train)
                    .append("，").append(formatFloat1(currentValue))
                    .append("→").append(formatFloat1(target))
                    .append("（").append(formatFloat2(finalValueRaw)).append("）\n");
        }

        viewModel.updateRequiredTrain(requiredTrain);
        if (resultText != null) {
            resultText.setText(resultSb.length() == 0 ? "无需训练值" : resultSb.toString());
        }
        if (summaryText != null) {
            summaryText.setText(String.format("训练上限：%d\n所需总训练值：%d\n是否可行：%s",
                    trainLimit, totalTrain, (totalTrain <= trainLimit) ? "✔️" : "❌"));
            if (totalTrain > trainLimit) {
                summaryText.setTextColor(0xFFFF0000);
                summaryText.append("\n\n⚠️ 警告：所需训练值超过上限，请返回修改");
            } else {
                summaryText.setTextColor(0xFF000000);
            }
        }
    }

    // 格式化保留1位小数（用于当前值和目标值）
    private String formatFloat1(float value) {
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(1, RoundingMode.HALF_UP);
        return bd.toString();
    }

    // 格式化保留2位小数（用于理论最终值）
    private String formatFloat2(float value) {
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.toString();
    }

    private String getAttrName(String key) {
        for (int i = 0; i < ATTR_KEYS.length; i++) {
            if (ATTR_KEYS[i].equals(key)) return ATTR_NAMES[i];
        }
        return key;
    }

    @Override
    public boolean validateAndSave() {
        if (isTrainExceedLimit()) {
            Toast.makeText(requireContext(), "所需训练值超限，请调整目标值", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }
}