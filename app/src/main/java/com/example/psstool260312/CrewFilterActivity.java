package com.example.psstool260312;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.psstool260312.utils.DBHelper;
import com.example.psstool260312.utils.PinyinUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CrewFilterActivity extends AppCompatActivity {

    private DBHelper dbHelper;
    private SQLiteDatabase db;

    // 筛选控件
    private EditText etName;
    private Button btnEquipment, btnRarity, btnSkill, btnCollection;
    private Button btnFilter;

    // 多选数据存储
    private List<Integer> crewIds = new ArrayList<>();
    private Set<String> selectedEquipments = new HashSet<>();
    private Set<String> selectedRarities = new HashSet<>();
    private Set<String> selectedSkills = new HashSet<>();
    private Set<String> selectedCollections = new HashSet<>();

    // 所有选项集合（从数据库加载）
    private List<String> allEquipments = new ArrayList<>();
    private List<String> allRarities = new ArrayList<>();
    private List<String> allSkills = new ArrayList<>();
    private List<String> allCollections = new ArrayList<>();

    // 表格相关
    private TableLayout tableLayout;
    private List<String> allColumns = Arrays.asList(
            "name_zh", "rarity", "equipments", "special", "collection",
            "h", "a", "r", "b", "p", "s", "e", "w", "f"
    );
    private Map<String, String> columnNames = new HashMap<String, String>() {{
        put("name_zh", "姓名");
        put("rarity", "稀有度");
        put("equipments", "装备");
        put("special", "技能");
        put("collection", "团队");
        put("h", "血量");
        put("a", "攻击");
        put("r", "维修");
        put("b", "能力");
        put("p", "导航");
        put("s", "科技");
        put("e", "引擎");
        put("w", "武器");
        put("f", "火抗");
    }};
    private Set<String> visibleColumns = new HashSet<>(allColumns); // 默认全部可见
    private List<Map<String, String>> crewData = new ArrayList<>();
    private String currentSortColumn = null;
    private boolean sortAscending = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crew_filter);

        dbHelper = new DBHelper(this);
        db = dbHelper.getReadableDatabase();

        initViews();
        loadFilterOptions();
        setupFilterButtons();
        setupColumnVisibility();
        performFilter(); // 初始加载所有船员
    }

    private void initViews() {
        etName = findViewById(R.id.et_name);
        btnEquipment = findViewById(R.id.btn_equipment);
        btnRarity = findViewById(R.id.btn_rarity);
        btnSkill = findViewById(R.id.btn_skill);
        btnCollection = findViewById(R.id.btn_collection);
        btnFilter = findViewById(R.id.btn_filter);
        tableLayout = findViewById(R.id.table_layout);

        btnFilter.setOnClickListener(v -> performFilter());
    }

    private void loadFilterOptions() {
        // 从数据库加载所有可能的选项
        // 稀有度
        Cursor c = db.rawQuery("SELECT DISTINCT rarity FROM " + DBHelper.TABLE_CREW + " WHERE rarity IS NOT NULL", null);
        allRarities.clear();
        while (c.moveToNext()) allRarities.add(c.getString(0));
        c.close();

        // 技能 (special)
        c = db.rawQuery("SELECT DISTINCT special FROM " + DBHelper.TABLE_CREW + " WHERE special IS NOT NULL", null);
        allSkills.clear();
        while (c.moveToNext()) allSkills.add(c.getString(0));
        c.close();

        // 团队 (collection)
        c = db.rawQuery("SELECT DISTINCT collection FROM " + DBHelper.TABLE_CREW + " WHERE collection IS NOT NULL", null);
        allCollections.clear();
        while (c.moveToNext()) allCollections.add(c.getString(0));
        c.close();

        // 装备：需要拆分所有船员的 equipments 字段（格式如 "肩+胸+手+腿+宠"）
        Set<String> equipSet = new HashSet<>();
        c = db.rawQuery("SELECT equipments FROM " + DBHelper.TABLE_CREW + " WHERE equipments IS NOT NULL", null);
        while (c.moveToNext()) {
            String eq = c.getString(0);
            if (!TextUtils.isEmpty(eq)) {
                String[] parts = eq.split("\\+");
                for (String part : parts) {
                    if (!TextUtils.isEmpty(part)) equipSet.add(part.trim());
                }
            }
        }
        c.close();
        allEquipments.clear();
        allEquipments.addAll(equipSet);

        // ========== 自定义排序 ==========
        // 1. 装备固定顺序
        List<String> fixedEquipmentOrder = Arrays.asList("头", "肩", "胸", "手", "腿", "宠");
        Collections.sort(allEquipments, (o1, o2) -> {
            int idx1 = fixedEquipmentOrder.indexOf(o1);
            int idx2 = fixedEquipmentOrder.indexOf(o2);
            if (idx1 >= 0 && idx2 >= 0) return Integer.compare(idx1, idx2);
            if (idx1 >= 0) return -1;  // o1 在固定列表中，o2 不在
            if (idx2 >= 0) return 1;   // o2 在固定列表中，o1 不在
            return o1.compareTo(o2);   // 都不在，自然顺序
        });

        // 2. 稀有度固定顺序
        List<String> fixedRarityOrder = Arrays.asList("普通", "精英", "独特", "史诗", "英雄", "特殊", "传奇");
        Collections.sort(allRarities, (o1, o2) -> {
            int idx1 = fixedRarityOrder.indexOf(o1);
            int idx2 = fixedRarityOrder.indexOf(o2);
            if (idx1 >= 0 && idx2 >= 0) return Integer.compare(idx1, idx2);
            if (idx1 >= 0) return -1;
            if (idx2 >= 0) return 1;
            return o1.compareTo(o2);
        });

        // 3. 技能排序："无" 放最前，其余按拼音
        allSkills.sort((o1, o2) -> {
            if ("无".equals(o1)) return -1;
            if ("无".equals(o2)) return 1;
            return PinyinUtils.compare(o1, o2);
        });

        // 4. 团队排序："无" 第一，"新增/缺失" 第二，其余按拼音
        allCollections.sort((o1, o2) -> {
            if ("无".equals(o1)) return -1;
            if ("无".equals(o2)) return 1;
            if ("新增/缺失".equals(o1)) return -1;
            if ("新增/缺失".equals(o2)) return 1;
            return PinyinUtils.compare(o1, o2);
        });
    }

    private void setupFilterButtons() {
        btnEquipment.setOnClickListener(v -> showMultiChoiceDialog("选择装备", allEquipments, selectedEquipments, btnEquipment));
        btnRarity.setOnClickListener(v -> showMultiChoiceDialog("选择稀有度", allRarities, selectedRarities, btnRarity));
        btnSkill.setOnClickListener(v -> showMultiChoiceDialog("选择技能", allSkills, selectedSkills, btnSkill));
        btnCollection.setOnClickListener(v -> showMultiChoiceDialog("选择团队", allCollections, selectedCollections, btnCollection));
    }

    private void showMultiChoiceDialog(String title, List<String> items, Set<String> selected, Button button) {
        boolean[] checkedItems = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            if (selected.contains(items.get(i))) checkedItems[i] = true;
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMultiChoiceItems(items.toArray(new String[0]), checkedItems, (dialog, which, isChecked) -> {
                    if (isChecked) selected.add(items.get(which));
                    else selected.remove(items.get(which));
                })
                .setPositiveButton("确定", (dialog, which) -> {
                    String summary = selected.isEmpty() ? "未选择" : selected.size() + "项";
                    button.setText(title + ": " + summary);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void setupColumnVisibility() {
        // 动态生成表头（带复选框控制列显示）
        LinearLayout controlsLayout = findViewById(R.id.column_controls);
        controlsLayout.removeAllViews();
        for (String col : allColumns) {
            CheckBox cb = new CheckBox(this);
            cb.setText(columnNames.get(col));
            cb.setChecked(visibleColumns.contains(col));
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) visibleColumns.add(col);
                else visibleColumns.remove(col);
                refreshTable();
            });
            controlsLayout.addView(cb);
        }
    }

    private void performFilter() {
        String nameFilter = etName.getText().toString().trim();
        StringBuilder where = new StringBuilder("1=1");
        List<String> args = new ArrayList<>();

        if (!TextUtils.isEmpty(nameFilter)) {
            where.append(" AND name_zh LIKE ?");
            args.add("%" + nameFilter + "%");
        }

        // 稀有度：包含任一选中的
        if (!selectedRarities.isEmpty()) {
            where.append(" AND rarity IN (");
            for (int i = 0; i < selectedRarities.size(); i++) {
                if (i > 0) where.append(",");
                where.append("?");
                args.add(selectedRarities.toArray(new String[0])[i]);
            }
            where.append(")");
        }

        // 技能：包含任一
        if (!selectedSkills.isEmpty()) {
            where.append(" AND special IN (");
            for (int i = 0; i < selectedSkills.size(); i++) {
                if (i > 0) where.append(",");
                where.append("?");
                args.add(selectedSkills.toArray(new String[0])[i]);
            }
            where.append(")");
        }

        // 团队：包含任一
        if (!selectedCollections.isEmpty()) {
            where.append(" AND collection IN (");
            for (int i = 0; i < selectedCollections.size(); i++) {
                if (i > 0) where.append(",");
                where.append("?");
                args.add(selectedCollections.toArray(new String[0])[i]);
            }
            where.append(")");
        }

        // 装备：必须包含所有选中的装备（AND 关系）
        if (!selectedEquipments.isEmpty()) {
            for (String eq : selectedEquipments) {
                where.append(" AND equipments LIKE ?");
                args.add("%" + eq + "%");
            }
        }

        String sql = "SELECT id, " + TextUtils.join(",", allColumns) + " FROM " + DBHelper.TABLE_CREW + " WHERE " + where.toString();
        Cursor cursor = db.rawQuery(sql, args.toArray(new String[0]));

        crewData.clear();
        crewIds.clear();
        while (cursor.moveToNext()) {
            int id = cursor.getInt(0);  // id 在第一列
            crewIds.add(id);
            Map<String, String> row = new HashMap<>();
            // 从第1列开始（跳过 id 列）
            for (int i = 0; i < allColumns.size(); i++) {
                String col = allColumns.get(i);
                int index = i + 1;  // 因为 cursor 的第0列是 id
                String value = cursor.getString(index);
                row.put(col, value == null ? "" : value);
            }
            crewData.add(row);
        }
        cursor.close();

        refreshTable();
        Toast.makeText(this, "共找到 " + crewData.size() + " 名船员", Toast.LENGTH_SHORT).show();
    }

    private void refreshTable() {
        // 排序
        if (currentSortColumn != null && columnNames.containsKey(currentSortColumn)) {
            sortCrewData();
        }

        // 清除旧内容，保留表头
        tableLayout.removeAllViews();
        drawHeader();

        for (int i = 0; i < crewData.size(); i++) {
            Map<String, String> row = crewData.get(i);
            TableRow tr = new TableRow(this);
            int colIndex = 0;
            for (String col : allColumns) {
                if (visibleColumns.contains(col)) {
                    TextView tv = new TextView(this);
                    tv.setText(row.get(col));
                    tv.setPadding(8, 8, 8, 8);
                    tv.setBackgroundResource(android.R.drawable.divider_horizontal_bright);
                    // 如果是姓名列，设置点击监听
                    if ("name_zh".equals(col)) {
                        final int crewId = crewIds.get(i);
                        tv.setClickable(true);
                        tv.setFocusable(true);
                        tv.setOnClickListener(v -> showCrewInfoDialog(crewId));
                    }
                    tr.addView(tv);
                    colIndex++;
                }
            }
            tableLayout.addView(tr);
        }
    }

    private void drawHeader() {
        TableRow headerRow = new TableRow(this);
        for (String col : allColumns) {
            if (visibleColumns.contains(col)) {
                TextView tv = new TextView(this);
                tv.setText(columnNames.get(col));
                tv.setPadding(8, 8, 8, 8);
                tv.setBackgroundResource(android.R.drawable.editbox_background);
                tv.setTextColor(0xFF0000FF);
                tv.setClickable(true);
                tv.setOnClickListener(v -> {
                    if (currentSortColumn != null && currentSortColumn.equals(col)) {
                        sortAscending = !sortAscending;
                    } else {
                        currentSortColumn = col;
                        sortAscending = true;
                    }
                    refreshTable();
                });
                headerRow.addView(tv);
            }
        }
        tableLayout.addView(headerRow);
    }

    private void sortCrewData() {
        // 稀有度固定顺序列表
        List<String> rarityOrder = Arrays.asList("普通", "精英", "独特", "史诗", "英雄", "特殊", "传奇");

        Collections.sort(crewData, (o1, o2) -> {
            String v1 = o1.get(currentSortColumn);
            String v2 = o2.get(currentSortColumn);

            // 稀有度列特殊处理
            if ("rarity".equals(currentSortColumn)) {
                int idx1 = rarityOrder.indexOf(v1);
                int idx2 = rarityOrder.indexOf(v2);
                // 未在列表中的稀有度（如未知）放到最后
                if (idx1 == -1) idx1 = Integer.MAX_VALUE;
                if (idx2 == -1) idx2 = Integer.MAX_VALUE;
                int cmp = Integer.compare(idx1, idx2);
                return sortAscending ? cmp : -cmp;
            }

            // 其他列：尝试按数字排序
            try {
                float f1 = Float.parseFloat(v1);
                float f2 = Float.parseFloat(v2);
                int cmp = Float.compare(f1, f2);
                return sortAscending ? cmp : -cmp;
            } catch (NumberFormatException e) {
                // 非数字：按拼音排序
                String p1 = PinyinUtils.getFirstLetter(v1);
                String p2 = PinyinUtils.getFirstLetter(v2);
                int cmp = p1.compareTo(p2);
                return sortAscending ? cmp : -cmp;
            }
        });
    }

    private void showCrewInfoDialog(int crewId) {
        new Thread(() -> {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.query(DBHelper.TABLE_CREW, null,
                    "id=?", new String[]{String.valueOf(crewId)},
                    null, null, null);
            if (!cursor.moveToFirst()) {
                cursor.close();
                db.close();
                return;
            }

            // 读取字段
            String name = cursor.getString(cursor.getColumnIndexOrThrow("name_zh"));
            String equipments = cursor.getString(cursor.getColumnIndexOrThrow("equipments"));
            String special = cursor.getString(cursor.getColumnIndexOrThrow("special"));
            String collection = cursor.getString(cursor.getColumnIndexOrThrow("collection"));
            int trainLimit = cursor.getInt(cursor.getColumnIndexOrThrow("t"));
            float h = cursor.getFloat(cursor.getColumnIndexOrThrow("h"));
            float a = cursor.getFloat(cursor.getColumnIndexOrThrow("a"));
            float r = cursor.getFloat(cursor.getColumnIndexOrThrow("r"));
            float b = cursor.getFloat(cursor.getColumnIndexOrThrow("b"));
            float p = cursor.getFloat(cursor.getColumnIndexOrThrow("p"));
            float s = cursor.getFloat(cursor.getColumnIndexOrThrow("s"));
            float e = cursor.getFloat(cursor.getColumnIndexOrThrow("e"));
            float w = cursor.getFloat(cursor.getColumnIndexOrThrow("w"));
            float f = cursor.getFloat(cursor.getColumnIndexOrThrow("f"));

            cursor.close();

            // 加载图片
            File imageFile = new File(getFilesDir(), "crew_images/" + crewId + ".png");
            Bitmap bitmap = null;
            if (imageFile.exists() && imageFile.length() > 0) {
                bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
                // 缩放图片
                if (bitmap != null) {
                    int targetWidth = (int) (100 * getResources().getDisplayMetrics().density);
                    int targetHeight = (int) (120 * getResources().getDisplayMetrics().density);
                    float scale = Math.min((float) targetWidth / bitmap.getWidth(),
                            (float) targetHeight / bitmap.getHeight());
                    int scaledWidth = Math.round(bitmap.getWidth() * scale);
                    int scaledHeight = Math.round(bitmap.getHeight() * scale);
                    Bitmap scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, false);
                    if (scaled != bitmap) bitmap.recycle();
                    bitmap = scaled;
                }
            }

            final Bitmap finalBitmap = bitmap;
            final String finalName = name;
            final String finalEquipments = equipments;
            final String finalSpecial = special;
            final String finalCollection = collection;
            final int finalTrainLimit = trainLimit;

            runOnUiThread(() -> {
                // 创建 Dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                View dialogView = getLayoutInflater().inflate(R.layout.dialog_crew_info, null);
                builder.setView(dialogView);
                builder.setPositiveButton("关闭", null);
                AlertDialog dialog = builder.create();

                // 绑定数据
                TextView tvName = dialogView.findViewById(R.id.tv_crew_name);
                TextView tvEquip = dialogView.findViewById(R.id.tv_equipments);
                TextView tvSpecial = dialogView.findViewById(R.id.tv_special);
                TextView tvCollection = dialogView.findViewById(R.id.tv_collection);
                TextView tvTrain = dialogView.findViewById(R.id.tv_training_limit);
                TextView tvH = dialogView.findViewById(R.id.tv_h);
                TextView tvA = dialogView.findViewById(R.id.tv_a);
                TextView tvR = dialogView.findViewById(R.id.tv_r);
                TextView tvB = dialogView.findViewById(R.id.tv_b);
                TextView tvP = dialogView.findViewById(R.id.tv_p);
                TextView tvS = dialogView.findViewById(R.id.tv_s);
                TextView tvE = dialogView.findViewById(R.id.tv_e);
                TextView tvW = dialogView.findViewById(R.id.tv_w);
                TextView tvF = dialogView.findViewById(R.id.tv_f);
                ImageView ivImage = dialogView.findViewById(R.id.iv_crew_image);

                tvName.setText(finalName);
                tvEquip.setText("装备位: " + (finalEquipments != null ? finalEquipments : "无"));
                tvSpecial.setText("技能: " + (finalSpecial != null ? finalSpecial : "无"));
                tvCollection.setText("团队: " + (finalCollection != null ? finalCollection : "无"));
                tvTrain.setText("训练上限: " + finalTrainLimit);
                tvH.setText(String.format("血量: %.1f", h));
                tvA.setText(String.format("攻击: %.1f", a));
                tvR.setText(String.format("维修: %.1f", r));
                tvB.setText(String.format("能力: %.1f", b));
                tvP.setText(String.format("导航: %.1f", p));
                tvS.setText(String.format("科技: %.1f", s));
                tvE.setText(String.format("引擎: %.1f", e));
                tvW.setText(String.format("武器: %.1f", w));
                tvF.setText(String.format("火抗: %.1f", f));

                if (finalBitmap != null) {
                    ivImage.setImageBitmap(finalBitmap);
                    ivImage.setScaleType(ImageView.ScaleType.CENTER);
                } else {
                    ivImage.setImageResource(R.drawable.ic_launcher_foreground);
                    ivImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
                }

                dialog.show();
            });
            db.close();
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        db.close();
    }
}
