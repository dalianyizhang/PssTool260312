package com.example.psstool260312;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager2.widget.ViewPager2;

import com.example.psstool260312.adapter.CrewPagerAdapter;
import com.example.psstool260312.fragment.ProgressDialogFragment;
import com.example.psstool260312.pssDo.Crew;
import com.example.psstool260312.service.DataImportService;
import com.example.psstool260312.utils.DBHelper;
import com.example.psstool260312.utils.PinyinUtils;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CraftInfoActivity extends AppCompatActivity {
    private Spinner raritySpinner, crewSpinner;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private CrewPagerAdapter pagerAdapter;
    private List<String> rarities = new ArrayList<>();
    private List<Crew> allCrews = new ArrayList<>();
    private int selectedCrewId = -1;
    private String selectedRarity = "";
    private ProgressDialogFragment progressDialog;
    private BroadcastReceiver importReceiver;

    // 悬浮层控件
    private View overlayContainer;
    private TextView popupCrewName;
    private TextView popupEquipments, popupSpecial, popupCollection;
    private TextView popupH, popupA, popupR, popupB;
    private TextView popupP, popupS, popupE, popupW;
    private ImageView popupCrewImage;

    // 线程
    private Thread loadDataThread;

    // 记录数据加载状态
    private boolean isDataLoaded = false;
    private boolean isShowingNoDataDialog = false;
    private AlertDialog noDataDialog;

    private static final int REQUEST_CODE_IMPORT = 1001;
    private static final int REQUEST_STORAGE_PERMISSION = 1002;
    private static final String PREFS_NAME = "data_info";
    private static final String PREFS_KEY_VALID_UNTIL = "valid_until";
    private static final String PREFS_KEY_SUPPRESS_UNTIL = "suppress_until";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_craft_info);

        // 初始化UI
        raritySpinner = findViewById(R.id.raritySpinner);
        crewSpinner = findViewById(R.id.crewSpinner);
        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);

        // 初始化悬浮层
        overlayContainer = findViewById(R.id.overlay_container);
        popupCrewName = findViewById(R.id.popup_crew_name);
        popupCrewImage = findViewById(R.id.popup_crew_image);
        popupEquipments = findViewById(R.id.popup_equipments);
        popupSpecial = findViewById(R.id.popup_special);
        popupCollection = findViewById(R.id.popup_collection);
        popupH = findViewById(R.id.popup_h);
        popupA = findViewById(R.id.popup_a);
        popupR = findViewById(R.id.popup_r);
        popupB = findViewById(R.id.popup_b);
        popupP = findViewById(R.id.popup_p);
        popupS = findViewById(R.id.popup_s);
        popupE = findViewById(R.id.popup_e);
        popupW = findViewById(R.id.popup_w);

        overlayContainer.setOnClickListener(v -> hidePopup());

        // 设置ViewPager适配器
        pagerAdapter = new CrewPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        // 关联TabLayout和ViewPager
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    if (position == 0) tab.setText("作为材料");
                    else if (position == 1) tab.setText("作为产物");
                    else tab.setText("船员信息");
                }
        ).attach();

        // 注册广播接收器
        registerImportReceiver();
        // 检查数据库是否有数据
        checkDatabase();
        // 检查数据有效期
        checkDataValidity();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isDataLoaded) checkDatabase();
    }

    @Override
    public void onBackPressed() {
        if (overlayContainer.getVisibility() == View.VISIBLE) hidePopup();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (noDataDialog != null && noDataDialog.isShowing()) {
            noDataDialog.dismiss();
            noDataDialog = null;
        }
        if (loadDataThread != null && loadDataThread.isAlive()) {
            loadDataThread.interrupt();
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(importReceiver);
    }

    // ================== 悬浮层控制 ==================
    @SuppressLint("SetTextI18n")
    public void showCrewInfo(String crewName) {
        DBHelper dbHelper = new DBHelper(this);
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT id, equipments, special, collection, h, a, r, b, p, s, e, w FROM " + DBHelper.TABLE_CREW + " WHERE name_zh=?",
                new String[]{crewName}
        );

        if (cursor.moveToFirst()) {
            int crewId = cursor.getInt(0);
            String equipments = cursor.getString(1);
            String special = cursor.getString(2);
            String collection = cursor.getString(3);
            float h = cursor.getFloat(4);
            float a = cursor.getFloat(5);
            float r = cursor.getFloat(6);
            float b = cursor.getFloat(7);
            float p = cursor.getFloat(8);
            float s = cursor.getFloat(9);
            float e = cursor.getFloat(10);
            float w = cursor.getFloat(11);

            popupCrewName.setText(crewName);
            popupEquipments.setText("装备位: " + (equipments != null ? equipments : "无"));
            popupSpecial.setText("技能: " + (special != null ? special : "无"));
            popupCollection.setText("团队: " + (collection != null ? collection : "无"));
            popupH.setText("血量: " + h);
            popupA.setText("攻击: " + a);
            popupR.setText("维修: " + r);
            popupB.setText("能力: " + b);
            popupP.setText("导航: " + p);
            popupS.setText("科技: " + s);
            popupE.setText("引擎: " + e);
            popupW.setText("武器: " + w);

            loadCrewImage(crewId);
            overlayContainer.setVisibility(View.VISIBLE);
        } else {
            Toast.makeText(this, "未找到船员信息", Toast.LENGTH_SHORT).show();
        }
        cursor.close();
        db.close();
    }

    public void hidePopup() {
        overlayContainer.setVisibility(View.GONE);
    }

    private void loadCrewImage(int crewId) {
        File imageFile = new File(getFilesDir(), "crew_images/" + crewId + ".png");
        Bitmap originalBitmap = null;
        if (imageFile.exists() && imageFile.length() > 0) {
            originalBitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
        }

        if (originalBitmap == null) {
            popupCrewImage.setImageResource(R.drawable.ic_launcher_foreground);
            popupCrewImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
            return;
        }

        int targetWidth = popupCrewImage.getWidth();
        int targetHeight = popupCrewImage.getHeight();
        if (targetWidth <= 0 || targetHeight <= 0) {
            float density = getResources().getDisplayMetrics().density;
            targetWidth = (int) (80 * density);
            targetHeight = (int) (100 * density);
        }

        float scale = Math.min((float) targetWidth / originalBitmap.getWidth(),
                (float) targetHeight / originalBitmap.getHeight());
        int scaledWidth = Math.round(originalBitmap.getWidth() * scale);
        int scaledHeight = Math.round(originalBitmap.getHeight() * scale);
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, false);
        popupCrewImage.setImageBitmap(scaledBitmap);
        popupCrewImage.setScaleType(ImageView.ScaleType.CENTER);
    }

    // ================== 菜单 ==================
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.craft_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_import) {
            openFilePicker();
            return true;
        } else if (item.getItemId() == R.id.action_about) {
            showAboutDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ================== 数据加载与导入 ==================
    private void registerImportReceiver() {
        importReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (DataImportService.ACTION_IMPORT_PROGRESS.equals(intent.getAction())) {
                    String message = intent.getStringExtra(DataImportService.EXTRA_MESSAGE);
                    int progress = intent.getIntExtra(DataImportService.EXTRA_PROGRESS, 0);
                    if (progressDialog == null) {
                        progressDialog = new ProgressDialogFragment();
                        progressDialog.setCancelable(false);
                        progressDialog.show(getSupportFragmentManager(), "import_progress");
                    }
                    progressDialog.updateProgress(message, progress);
                } else if (DataImportService.ACTION_IMPORT_COMPLETE.equals(intent.getAction())) {
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                        progressDialog = null;
                    }
                    boolean success = intent.getBooleanExtra(DataImportService.EXTRA_SUCCESS, false);
                    String message = intent.getStringExtra(DataImportService.EXTRA_MESSAGE);
                    Toast.makeText(CraftInfoActivity.this, message, Toast.LENGTH_LONG).show();
                    if (success) {
                        // 导入成功：关闭未检测到数据对话框（如果存在）
                        if (noDataDialog != null && noDataDialog.isShowing()) {
                            noDataDialog.dismiss();
                            noDataDialog = null;
                        }
                        loadCrewData();
                        checkDataValidity();
                    }
                }
                if (DataImportService.ACTION_DATABASE_UPDATED.equals(intent.getAction())) {
                    checkDatabase();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(DataImportService.ACTION_IMPORT_PROGRESS);
        filter.addAction(DataImportService.ACTION_IMPORT_COMPLETE);
        filter.addAction(DataImportService.ACTION_DATABASE_UPDATED);
        LocalBroadcastManager.getInstance(this).registerReceiver(importReceiver, filter);
    }

    private void checkDatabase() {
        new Thread(() -> {
            DBHelper dbHelper = new DBHelper(this);
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = null;
            boolean hasData = false;
            try {
                cursor = db.rawQuery("SELECT COUNT(*) FROM " + DBHelper.TABLE_CREW, null);
                if (cursor.moveToFirst()) hasData = cursor.getInt(0) > 0;
            } finally {
                if (cursor != null) cursor.close();
                db.close();
            }
            final boolean finalHasData = hasData;
            runOnUiThread(() -> {
                if (finalHasData) {
                    // 已有数据：关闭可能残留的对话框
                    if (noDataDialog != null && noDataDialog.isShowing()) {
                        noDataDialog.dismiss();
                        noDataDialog = null;
                    }
                    loadCrewData();
                } else {
                    // 无数据：防止重复弹框
                    if (noDataDialog == null || !noDataDialog.isShowing()) {
                        noDataDialog = new AlertDialog.Builder(CraftInfoActivity.this)
                                .setTitle("提示")
                                .setMessage("未检测到数据，请先导入数据包")
                                .setPositiveButton("导入", (dialog, which) -> {
                                    openFilePicker();
                                    noDataDialog = null;
                                })
                                .setNegativeButton("取消", (dialog, which) -> {
                                    noDataDialog = null;
                                })
                                .setCancelable(false)
                                .show();
                    }
                }
            });
        }).start();
    }

    private void loadCrewData() {
        if (isDataLoaded) {
            restoreSelections();
            return;
        }
        if (loadDataThread != null && loadDataThread.isAlive()) loadDataThread.interrupt();
        loadDataThread = new Thread(() -> {
            DBHelper dbHelper = new DBHelper(CraftInfoActivity.this);
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = null;
            try {
                cursor = db.rawQuery("SELECT id, name_zh, rarity FROM " + DBHelper.TABLE_CREW, null);
                List<Crew> tempAllCrews = new ArrayList<>();
                Set<String> raritySet = new HashSet<>();
                while (cursor.moveToNext()) {
                    int id = cursor.getInt(0);
                    String name = cursor.getString(1);
                    String rarity = cursor.getString(2);
                    if (rarity == null) rarity = "未知";
                    tempAllCrews.add(new Crew(id, name, rarity));
                    raritySet.add(rarity);
                }
                cursor.close();

                List<String> fixedOrder = Arrays.asList("普通", "精英", "独特", "史诗", "英雄", "特殊", "传奇");
                List<String> tempRarities = new ArrayList<>();
                for (String r : fixedOrder) {
                    if (raritySet.contains(r)) tempRarities.add(r);
                }
                List<String> remaining = new ArrayList<>(raritySet);
                remaining.removeAll(tempRarities);
                Collections.sort(remaining);
                tempRarities.addAll(remaining);

                final List<Crew> finalAllCrews = new ArrayList<>(tempAllCrews);
                final List<String> finalRarities = new ArrayList<>(tempRarities);
                runOnUiThread(() -> {
                    allCrews.clear();
                    allCrews.addAll(finalAllCrews);
                    rarities.clear();
                    rarities.addAll(finalRarities);

                    ArrayAdapter<String> rarityAdapter = new ArrayAdapter<>(CraftInfoActivity.this,
                            android.R.layout.simple_spinner_item, rarities);
                    rarityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    raritySpinner.setAdapter(rarityAdapter);

                    raritySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            selectedRarity = (String) parent.getSelectedItem();
                            updateCrewSpinner();
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {
                        }
                    });

                    crewSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            Crew selected = (Crew) parent.getSelectedItem();
                            if (selected != null) {
                                selectedCrewId = selected.getId();
                                pagerAdapter.setCrewId(selectedCrewId);
                            }
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {
                        }
                    });

                    restoreSelections();
                    isDataLoaded = true;
                });
            } finally {
                if (cursor != null && !cursor.isClosed()) cursor.close();
                db.close();
            }
        });
        loadDataThread.start();
    }

    private void updateCrewSpinner() {
        if (selectedRarity == null || selectedRarity.isEmpty()) {
            if (raritySpinner.getSelectedItem() != null)
                selectedRarity = (String) raritySpinner.getSelectedItem();
            else if (!rarities.isEmpty()) selectedRarity = rarities.get(0);
            else return;
        }
        List<Crew> filtered = new ArrayList<>();
        for (Crew crew : allCrews) {
            if (crew.getRarity().equals(selectedRarity)) filtered.add(crew);
        }
        Collections.sort(filtered, (o1, o2) -> PinyinUtils.compare(o1.getNameZh(), o2.getNameZh()));
        ArrayAdapter<Crew> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, filtered);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        crewSpinner.setAdapter(adapter);
    }

    private void restoreSelections() {
        if (!selectedRarity.isEmpty()) {
            for (int i = 0; i < raritySpinner.getCount(); i++) {
                if (raritySpinner.getItemAtPosition(i).equals(selectedRarity)) {
                    raritySpinner.setSelection(i, false);
                    break;
                }
            }
        } else if (!rarities.isEmpty()) {
            int defaultIndex = Math.min(2, rarities.size() - 1);
            raritySpinner.setSelection(defaultIndex, false);
            selectedRarity = rarities.get(defaultIndex);
        }
        updateCrewSpinner();
        if (selectedCrewId != -1) {
            for (int i = 0; i < crewSpinner.getCount(); i++) {
                Crew crew = (Crew) crewSpinner.getItemAtPosition(i);
                if (crew.getId() == selectedCrewId) {
                    crewSpinner.setSelection(i);
                    pagerAdapter.setCrewId(selectedCrewId);
                    break;
                }
            }
        } else if (crewSpinner.getCount() > 0) {
            crewSpinner.setSelection(0);
            Crew crew = (Crew) crewSpinner.getSelectedItem();
            if (crew != null) {
                selectedCrewId = crew.getId();
                pagerAdapter.setCrewId(selectedCrewId);
            }
        }
    }

    private void checkDataValidity() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String validUntil = prefs.getString(PREFS_KEY_VALID_UNTIL, null);
        if (validUntil != null) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date expiry = sdf.parse(validUntil);
                if (expiry != null && expiry.before(new Date())) {
                    showExpiryDialog(validUntil, true);
                } else {
                    long suppressUntil = prefs.getLong(PREFS_KEY_SUPPRESS_UNTIL, 0);
                    if (System.currentTimeMillis() > suppressUntil) {
                        showExpiryDialog(validUntil, false);
                    }
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
    }

    private void showExpiryDialog(String expiryDate, boolean isExpired) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (isExpired) {
            builder.setTitle("数据过期")
                    .setMessage("当前数据有效期已过 (" + expiryDate + ")，部分数据可能不准确。\n请更新到最新数据。")
                    .setPositiveButton("确定", null);
        } else {
            builder.setTitle("数据有效")
                    .setMessage("当前数据有效期至 " + expiryDate)
                    .setPositiveButton("确定", null)
                    .setNeutralButton("一周内不再提示", (dialog, which) -> {
                        long oneWeekLater = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L);
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                .edit()
                                .putLong(PREFS_KEY_SUPPRESS_UNTIL, oneWeekLater)
                                .apply();
                        Toast.makeText(this, "一周内不再显示此提示", Toast.LENGTH_SHORT).show();
                    });
        }
        builder.show();
    }

    private void openFilePicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startFilePicker();
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_STORAGE_PERMISSION);
            } else {
                startFilePicker();
            }
        }
    }

    private void startFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        startActivityForResult(intent, REQUEST_CODE_IMPORT);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startFilePicker();
            } else {
                Toast.makeText(this, "需要存储权限才能导入数据", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_IMPORT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            DataImportService.startImport(this, uri);
        }
    }

    private void showAboutDialog() {
        String aboutMessage = "PSS船员合成工具\n\n" +
                "使用说明：\n" +
                "1. 首次使用或本地数据过期，需要导入最新的合成数据包；\n" +
                "2. 使用时，先依次选择稀有度和具体船员，结果会展示在下方；\n" +
                "3. 长按某一行数据，会复制该条合成公式到剪切板；\n" +
                "4. 双击某个船员，会显示船员信息框，点空白处可关闭该悬浮框。\n\n" +
                "遇到问题或建议请联系：\n" +
                "邮箱：2192601632@qq.com\n";

        new AlertDialog.Builder(this)
                .setTitle("关于")
                .setMessage(aboutMessage)
                .setPositiveButton("确定", null)
                .setNeutralButton("复制邮箱", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("邮箱", "2192601632@qq.com");
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
                })
                .show();
    }
}