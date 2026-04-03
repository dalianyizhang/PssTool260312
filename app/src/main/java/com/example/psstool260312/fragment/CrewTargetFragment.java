package com.example.psstool260312.fragment;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.example.psstool260312.R;
import com.example.psstool260312.pssDo.Crew;
import com.example.psstool260312.utils.DBHelper;
import com.example.psstool260312.utils.PinyinUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CrewTargetFragment extends BaseStepFragment {

    // 属性列表（移除火抗，因为它没有训练值，但保留以显示）
    private final String[] ATTR_KEYS = {"h", "a", "r", "b", "st", "p", "s", "e", "w", "f"};
    private final String[] ATTR_NAMES = {"血量", "攻击", "维修", "能力", "耐力", "导航", "科技", "引擎", "武器", "火抗"};

    private Spinner spinnerRarity;
    private Spinner spinnerCrew;

    private TextView tvBaseTrainLimit;
    private Spinner spinnerTrainBonus;

    private int baseTrainLimit = 0;
    private int selectedBonus = 0;
    private TableLayout tableLayout;
    private DBHelper dbHelper;

    // 存储控件引用
    private List<TextView> baseViews = new ArrayList<>();
    private List<EditText> equip1Views = new ArrayList<>();
    private List<EditText> equip2Views = new ArrayList<>();
    private List<EditText> targetViews = new ArrayList<>();

    private List<String> rarities = new ArrayList<>();
    private List<Crew> allCrews = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_crew_target, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        spinnerRarity = view.findViewById(R.id.spinner_rarity);
        spinnerCrew = view.findViewById(R.id.spinner_crew);
        tvBaseTrainLimit = view.findViewById(R.id.tv_base_train_limit);
        spinnerTrainBonus = view.findViewById(R.id.spinner_train_bonus);
        tableLayout = view.findViewById(R.id.table_layout);
        dbHelper = new DBHelper(requireContext());

        // 动态创建表格行
        createTableRows();

        // 加载数据
        loadCrewData();

        // 设置额外训练下拉框
        String[] bonusOptions = {"无", "+6", "+10"};
        ArrayAdapter<String> bonusAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, bonusOptions);
        bonusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTrainBonus.setAdapter(bonusAdapter);

        spinnerTrainBonus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        selectedBonus = 0;
                        break;
                    case 1:
                        selectedBonus = 6;
                        break;
                    case 2:
                        selectedBonus = 10;
                        break;
                }
                updateTotalTrainLimit();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 帮助按钮
        ImageButton btnHelp = view.findViewById(R.id.btn_help_crew_target);
        btnHelp.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("页面说明")
                    .setMessage("1. 选择稀有度和船员，会自动加载基础属性。\n" +
                            "2. 在“装备1”“装备2”列输入对应的装备属性。\n" +
                            "3. 在“目标值”列输入该船员期望达到的属性值。\n" +
                            " - 血量应填整数，其他属性填一位小数，规划时会自动进行进位修正。\n" +
                            " - 比如血量目标20，会自动修正到19.5。")
                    .setPositiveButton("知道了", null)
                    .show();
        });

        // 监听器
        spinnerRarity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String rarity = rarities.get(position);
                updateCrewSpinner(rarity);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spinnerCrew.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Crew selected = (Crew) parent.getItemAtPosition(position);
                viewModel.getSelectedCrew().setValue(selected);
                loadCrewBaseData(selected.getId());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void createTableRows() {
        for (int i = 0; i < ATTR_KEYS.length; i++) {
            TableRow row = new TableRow(requireContext());
            row.setPadding(0, 4, 0, 4);

            // 属性名
            TextView attrName = new TextView(requireContext());
            attrName.setText(ATTR_NAMES[i]);
            attrName.setPadding(4, 8, 4, 8);
            row.addView(attrName);

            // 基础值（只读）
            TextView baseView = new TextView(requireContext());
            baseView.setText("0");
            baseView.setBackgroundColor(0xFFEEEEEE);
            baseView.setPadding(4, 8, 4, 8);
            row.addView(baseView);
            baseViews.add(baseView);

            // 装备1
            EditText equip1 = new EditText(requireContext());
            equip1.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            equip1.setHint("0");
            equip1.setPadding(4, 8, 4, 8);
            row.addView(equip1);
            equip1Views.add(equip1);

            // 装备2
            EditText equip2 = new EditText(requireContext());
            equip2.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            equip2.setHint("0");
            equip2.setPadding(4, 8, 4, 8);
            row.addView(equip2);
            equip2Views.add(equip2);

            // 目标值
            EditText target = new EditText(requireContext());
            target.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            target.setHint("0");
            target.setPadding(4, 8, 4, 8);
            row.addView(target);
            targetViews.add(target);

            tableLayout.addView(row);
        }
    }

    private void loadCrewData() {
        new Thread(() -> {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT id, name_zh, rarity FROM " + DBHelper.TABLE_CREW, null);
            List<Crew> tempAll = new ArrayList<>();
            Set<String> raritySet = new HashSet<>();
            while (cursor.moveToNext()) {
                int id = cursor.getInt(0);
                String name = cursor.getString(1);
                String rarity = cursor.getString(2);
                if (rarity == null) rarity = "未知";
                tempAll.add(new Crew(id, name, rarity));
                raritySet.add(rarity);
            }
            cursor.close();

            // 稀有度排序
            List<String> fixedOrder = Arrays.asList("普通", "精英", "独特", "史诗", "英雄", "特殊", "传奇");
            List<String> sortedRarities = new ArrayList<>();
            for (String r : fixedOrder) {
                if (raritySet.contains(r)) sortedRarities.add(r);
            }
            List<String> remaining = new ArrayList<>(raritySet);
            remaining.removeAll(sortedRarities);
            Collections.sort(remaining);
            sortedRarities.addAll(remaining);

            requireActivity().runOnUiThread(() -> {
                allCrews.clear();
                allCrews.addAll(tempAll);
                rarities.clear();
                rarities.addAll(sortedRarities);

                // 设置稀有度适配器
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_spinner_item, rarities);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerRarity.setAdapter(adapter);

                // 默认选择“传奇”稀有度（如果存在）
                int defaultIndex = rarities.indexOf("传奇");
                if (defaultIndex >= 0) {
                    spinnerRarity.setSelection(defaultIndex);
                } else if (!rarities.isEmpty()) {
                    spinnerRarity.setSelection(0); // 没有传奇则选第一个
                }

                // 如果有稀有度且没有选中，则手动触发一次选择（但 setSelection 会自动触发 onItemSelected）
            });
            db.close();
        }).start();
    }

    private void updateTotalTrainLimit() {
        int totalLimit = baseTrainLimit + selectedBonus;
        viewModel.setTrainLimit(totalLimit);
    }

    private void updateCrewSpinner(String rarity) {
        List<Crew> filtered = new ArrayList<>();
        for (Crew c : allCrews) {
            if (rarity.equals(c.getRarity())) filtered.add(c);
        }
        Collections.sort(filtered, (o1, o2) -> PinyinUtils.compare(o1.getNameZh(), o2.getNameZh()));

        ArrayAdapter<Crew> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, filtered);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCrew.setAdapter(adapter);
    }

    private void loadCrewBaseData(int crewId) {
        new Thread(() -> {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.query(DBHelper.TABLE_CREW, null, "id=?", new String[]{String.valueOf(crewId)},
                    null, null, null);
            if (cursor.moveToFirst()) {
                float h = cursor.getFloat(cursor.getColumnIndexOrThrow("h"));
                float a = cursor.getFloat(cursor.getColumnIndexOrThrow("a"));
                float r = cursor.getFloat(cursor.getColumnIndexOrThrow("r"));
                float b = cursor.getFloat(cursor.getColumnIndexOrThrow("b"));
                float p = cursor.getFloat(cursor.getColumnIndexOrThrow("p"));
                float s = cursor.getFloat(cursor.getColumnIndexOrThrow("s"));
                float e = cursor.getFloat(cursor.getColumnIndexOrThrow("e"));
                float w = cursor.getFloat(cursor.getColumnIndexOrThrow("w"));
                int f = cursor.getInt(cursor.getColumnIndexOrThrow("f"));
                int t = cursor.getInt(cursor.getColumnIndexOrThrow("t"));

                float[] baseArray = {h, a, r, b, 0f, p, s, e, w, f};

                requireActivity().runOnUiThread(() -> {
                    // 更新基础值显示
                    for (int i = 0; i < baseViews.size(); i++) {
                        baseViews.get(i).setText(formatFloat02(baseArray[i]));
                    }
                    baseTrainLimit = t;
                    tvBaseTrainLimit.setText(String.valueOf(baseTrainLimit));

                    // 重置额外训练下拉框为“无”（位置0）
                    spinnerTrainBonus.setSelection(0);
                    updateTotalTrainLimit();  // 显式调用，确保总上限立即更新

                    // 清空装备1、装备2、目标值输入框
                    for (int i = 0; i < equip1Views.size(); i++) {
                        equip1Views.get(i).setText("");
                        equip2Views.get(i).setText("");
                        targetViews.get(i).setText("");
                    }
                    // 重置 ViewModel 中的目标值和装备值
                    viewModel.getTargetValues().setValue(new HashMap<>());
                    viewModel.getEquipmentValues().setValue(new HashMap<>());

                    // 保存基础值到 ViewModel
                    for (int i = 0; i < ATTR_KEYS.length; i++) {
                        viewModel.setBaseValue(ATTR_KEYS[i], baseArray[i]);
                    }
                });
            }
            cursor.close();
            db.close();
        }).start();
    }

    private String formatFloat(float value) {
        java.math.BigDecimal bd = new java.math.BigDecimal(value);
        bd = bd.setScale(3, java.math.RoundingMode.HALF_UP);
        return bd.toString();
    }

    private String formatFloat02(float value) {
        java.math.BigDecimal bd = new java.math.BigDecimal(value);
        bd = bd.setScale(1, java.math.RoundingMode.HALF_UP);
        return bd.toString();
    }

    @Override
    public boolean validateAndSave() {
        // 收集输入值
        Map<String, Float> targets = new HashMap<>();
        Map<String, float[]> equips = new HashMap<>();

        for (int i = 0; i < ATTR_KEYS.length; i++) {
            String key = ATTR_KEYS[i];
            float target;
            try {
                target = Float.parseFloat(targetViews.get(i).getText().toString().trim());
            } catch (NumberFormatException e) {
                target = 0f;
            }
            targets.put(key, target);

            float eq1 = 0f, eq2 = 0f;
            try {
                eq1 = Float.parseFloat(equip1Views.get(i).getText().toString().trim());
            } catch (NumberFormatException ignored) {
            }
            try {
                eq2 = Float.parseFloat(equip2Views.get(i).getText().toString().trim());
            } catch (NumberFormatException ignored) {
            }
            equips.put(key, new float[]{eq1, eq2}); // 只存两个装备总值
        }

        viewModel.getTargetValues().setValue(targets);
        viewModel.getEquipmentValues().setValue(equips);

        // 检查是否至少有一个目标值 > 0
        boolean hasTarget = false;
        for (float v : targets.values()) {
            if (v > 0) {
                hasTarget = true;
                break;
            }
        }
        if (!hasTarget) {
            Toast.makeText(requireContext(), "请至少设置一个目标值", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    public void refreshData() {
        spinnerTrainBonus.setSelection(0);
        loadCrewData();  // 重新加载船员数据
    }
}
