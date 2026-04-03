package com.example.psstool260312.fragment;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.example.psstool260312.R;
import com.example.psstool260312.pssDo.Crew;
import com.example.psstool260312.utils.DBHelper;
import com.example.psstool260312.utils.PlanGenerator;

import java.util.HashMap;
import java.util.Map;

public class GeneratePlanFragment extends BaseStepFragment {

    // 属性英文键与中文名称的映射
    private final String[] ATTR_KEYS = {"h", "a", "r", "b", "st", "p", "s", "e", "w", "f"};
    private final String[] ATTR_NAMES = {"血量", "攻击", "维修", "能力", "耐力", "导航", "科技", "引擎", "武器", "火抗"};

    private TextView planResult;
    private Button btnTrainPlan, btnMedicinePlan, btnSavePlan;
    private Button btnMedicineBase, btnMedicineSaveFive, btnMedicinePriorFour, btnMedicineAvoidFive;
    private DBHelper dbHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        ScrollView scrollView = new ScrollView(requireContext());
        View content = inflater.inflate(R.layout.fragment_generate_plan, scrollView, false);
        scrollView.addView(content);
        return scrollView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        planResult = view.findViewById(R.id.tv_plan_result);

        btnTrainPlan = view.findViewById(R.id.btn_train_plan);
        btnMedicinePlan = view.findViewById(R.id.btn_medicine_plan);

        btnMedicineBase = view.findViewById(R.id.btn_medicine_base);
        btnMedicineSaveFive = view.findViewById(R.id.btn_medicine_save_five);
        btnMedicinePriorFour = view.findViewById(R.id.btn_medicine_prior_four);
        btnMedicineAvoidFive = view.findViewById(R.id.btn_medicine_avoid_five);

        btnSavePlan = view.findViewById(R.id.btn_save_plan);

        dbHelper = new DBHelper(requireContext());

        btnTrainPlan.setOnClickListener(v -> generatePlan("train", "base"));
        btnMedicinePlan.setOnClickListener(v -> showMedicineOptions());
        btnMedicineBase.setOnClickListener(v -> generatePlan("medicine", "base"));
        btnMedicineSaveFive.setOnClickListener(v -> generatePlan("medicine", "saveFive"));
        btnMedicinePriorFour.setOnClickListener(v -> generatePlan("medicine", "priorFour"));
        btnMedicineAvoidFive.setOnClickListener(v -> generatePlan("medicine", "avoidFive"));
        btnSavePlan.setOnClickListener(v -> saveCurrentPlan());

        // 帮助按钮
        ImageButton btnHelp = view.findViewById(R.id.btn_help_generate_plan);
        btnHelp.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("页面说明")
                    .setMessage("根据训练值规划表，生成具体的培养步骤。\n" +
                            "• 全训练方案：优先使用各级训练（绿/蓝/金）逐步加点，训练无法提升后会选择黑脸五药继续提升。\n" +
                            "• 全吃药方案：仅使用药品（1~5级药）进行快速培养，\n"+
                            " - 补齐属性时，耐力属性通常会被最后考虑，但要用5级耐力药时除外。\n" +
                            " - 5级药中，禁用耐力药，少用血药，其他无限制。\n" +
                            "• 点击“复制”按钮可将当前方案（含船员名、训练分配、步骤）复制到剪贴板。")
                    .setPositiveButton("知道了", null)
                    .show();
        });

        btnSavePlan.setVisibility(View.GONE);  // 暂时隐藏保存方案按钮

        hideMedicineOptions();
    }

    private void showMedicineOptions() {
        btnMedicineBase.setVisibility(View.VISIBLE);
        btnMedicineSaveFive.setVisibility(View.VISIBLE);
        btnMedicinePriorFour.setVisibility(View.VISIBLE);
//        btnMedicineAvoidFive.setVisibility(View.VISIBLE);
    }

    private void hideMedicineOptions() {
        btnMedicineBase.setVisibility(View.GONE);
        btnMedicineSaveFive.setVisibility(View.GONE);
        btnMedicinePriorFour.setVisibility(View.GONE);
//        btnMedicineAvoidFive.setVisibility(View.GONE);
    }

    private void generatePlan(String type, String strategy) {
        hideMedicineOptions();

        if (viewModel.getSelectedCrew().getValue() == null) {
            Toast.makeText(requireContext(), "请先选择船员", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, Integer> requiredEn = viewModel.getRequiredTrain().getValue();
        if (requiredEn == null || requiredEn.isEmpty()) {
            Toast.makeText(requireContext(), "请先完成目标值和装备设置", Toast.LENGTH_SHORT).show();
            return;
        }

        // 将英文键转换为中文键，构造 target
        Map<String, Integer> target = new HashMap<>();
        for (int i = 0; i < ATTR_KEYS.length; i++) {
            String enKey = ATTR_KEYS[i];
            String cnKey = ATTR_NAMES[i];
            Integer value = requiredEn.get(enKey);
            if (value != null && value > 0) {
                target.put(cnKey, value);
            }
        }

        int trainLimit = viewModel.getTrainLimit();
        // 当前所有属性为0
        Map<String, Integer> current = new HashMap<>();
        for (String cnKey : target.keySet()) {
            current.put(cnKey, 0);
        }

        PlanGenerator.PlanResult result = PlanGenerator.generatePlan(type, strategy, trainLimit, current, target);
        if (result.steps.isEmpty()) {
            planResult.setText("无法生成方案，请检查目标值是否合理");
        } else {
            StringBuilder sb = new StringBuilder();
            // 显示步骤列表
            for (String step : result.steps) {
                sb.append(step).append("\n");
            }
            // 添加统计信息
            sb.append("\n【统计信息】\n");
            sb.append("总训练点数：").append(result.totalTrain).append("\n");
            sb.append("五级药总消耗：").append(result.fiveDrugTotal).append("个\n");
            sb.append("五级药分配：\n");
            for (Map.Entry<String, Integer> entry : result.fiveDrugPerAttr.entrySet()) {
                sb.append(" - ").append(entry.getKey()).append("：").append(entry.getValue()).append("个\n");
            }
            sb.append("\n最终各属性加点：\n");
            for (Map.Entry<String, Integer> entry : result.finalValues.entrySet()) {
                sb.append("  ").append(entry.getKey()).append("：").append(entry.getValue()).append("\n");
            }
            planResult.setText(sb.toString());
        }
    }

    private void saveCurrentPlan() {
        Crew crew = viewModel.getSelectedCrew().getValue();
        if (crew == null) {
            Toast.makeText(requireContext(), "请先选择船员", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, Float> targets = viewModel.getTargetValues().getValue();
        Map<String, float[]> equips = viewModel.getEquipmentValues().getValue();
        Map<String, Integer> required = viewModel.getRequiredTrain().getValue();
        if (targets == null || equips == null || required == null) {
            Toast.makeText(requireContext(), "请先完成目标设置", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("保存方案");
        final EditText input = new EditText(requireContext());
        input.setHint("请输入方案名称");
        builder.setView(input);
        builder.setPositiveButton("保存", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            saveToDatabase(name, crew.getId(), targets, equips, required);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void saveToDatabase(String configName, int crewId,
                                Map<String, Float> targets,
                                Map<String, float[]> equips,
                                Map<String, Integer> required) {
        new Thread(() -> {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("config_name", configName);
            cv.put("crew_id", crewId);
            // 保存目标值、装备值和训练值（使用英文键）
            for (int i = 0; i < ATTR_KEYS.length; i++) {
                String key = ATTR_KEYS[i];
                cv.put("target_" + key, targets.getOrDefault(key, 0f));
                float[] eq = equips.get(key);
                if (eq != null && eq.length >= 2) {
                    cv.put(key + "_eq1m", eq[0]); // 装备1总值
                    cv.put(key + "_eq2m", eq[1]); // 装备2总值
                    cv.put(key + "_eq1s", 0f);
                    cv.put(key + "_eq2s", 0f);
                }
                cv.put(key + "_train", required.getOrDefault(key, 0));
            }
            long id = db.insert(DBHelper.TABLE_SAVED_CONFIGS, null, cv);
            requireActivity().runOnUiThread(() -> {
                if (id != -1) Toast.makeText(requireContext(), "保存成功", Toast.LENGTH_SHORT).show();
                else Toast.makeText(requireContext(), "保存失败", Toast.LENGTH_SHORT).show();
            });
            db.close();
        }).start();
    }

    public String getPlanText() {
        return planResult != null ? planResult.getText().toString() : "";
    }

    public String getFullPlanText() {
        StringBuilder sb = new StringBuilder();

        // 1. 船员名字
        Crew crew = viewModel.getSelectedCrew().getValue();
        if (crew != null) {
            sb.append("【").append(crew.getNameZh()).append("】训练方案\n\n");
        } else {
            sb.append("【未选择船员】\n\n");
        }

        // 2. 训练值分配
        Map<String, Integer> required = viewModel.getRequiredTrain().getValue();
        if (required != null && !required.isEmpty()) {
            sb.append("训练值分配：\n");
            for (int i = 0; i < ATTR_KEYS.length; i++) {
                String key = ATTR_KEYS[i];
                int train = required.getOrDefault(key, 0);
                if (train > 0) {
                    sb.append("  ").append(ATTR_NAMES[i]).append("：").append(train).append("\n");
                }
            }
            sb.append("\n");
        }

        // 3. 生成的步骤
        String stepsText = planResult != null ? planResult.getText().toString() : "";
        if (!stepsText.isEmpty()) {
            sb.append("培养步骤：\n");
            sb.append(stepsText);
        } else {
            sb.append("尚未生成方案。");
        }

        return sb.toString();
    }


    @Override
    public boolean validateAndSave() {
        return true;
    }
}