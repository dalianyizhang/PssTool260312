package com.example.psstool260312;

import android.Manifest;
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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.psstool260312.fragment.BaseStepFragment;
import com.example.psstool260312.fragment.CrewTargetFragment;
import com.example.psstool260312.fragment.GeneratePlanFragment;
import com.example.psstool260312.fragment.PlanResultFragment;
import com.example.psstool260312.fragment.ProgressDialogFragment;
import com.example.psstool260312.service.DataImportService;
import com.example.psstool260312.utils.DBHelper;
import com.example.psstool260312.viewmodel.PlanViewModel;
import com.example.psstool260312.widget.StepIndicator;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_IMPORT = 1001;
    private static final int REQUEST_STORAGE_PERMISSION = 1002;
    private static final String PREFS_NAME = "data_info";
    private static final String PREFS_KEY_VALID_UNTIL = "valid_until";

    private ViewPager2 viewPager;
    private StepIndicator stepIndicator;
    private Button btnPrev, btnNext;

    private PlanViewModel viewModel;
    private BroadcastReceiver importReceiver;
    private ProgressDialogFragment progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewModel = new ViewModelProvider(this).get(PlanViewModel.class);

        initViews();
        setupViewPager();
        setupButtons();
        registerImportReceiver();

        // 检查数据库数据是否已导入，如果没有则提示导入
        checkDatabase();
        checkDataValidity();
    }

    private void initViews() {
        viewPager = findViewById(R.id.view_pager);
        stepIndicator = findViewById(R.id.step_indicator);
        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);
    }

    private void setupViewPager() {
        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @Override
            public int getItemCount() {
                return 3;
            }

            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0: return new CrewTargetFragment();
                    case 1: return new PlanResultFragment();
                    case 2: return new GeneratePlanFragment();
                    default: return new CrewTargetFragment();
                }
            }
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                stepIndicator.setCurrentStep(position);
                updateButtons(position);
            }
        });
    }

    private void setupButtons() {
        btnPrev.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            if (current > 0) {
                Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + current);
                if (fragment instanceof BaseStepFragment) {
                    ((BaseStepFragment) fragment).validateAndSave();
                } else {
                    // 如果 Fragment 为空或不是预期类型，记录日志
                    if (fragment == null) {
                        Log.w("MainActivity", "未找到当前页面的 Fragment: f" + current);
                    } else {
                        Log.w("MainActivity", "当前 Fragment 不是 BaseStepFragment: " + fragment.getClass().getName());
                    }
                }
                viewPager.setCurrentItem(current - 1);
            }
        });

        btnNext.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + current);
            if (fragment instanceof BaseStepFragment) {
                BaseStepFragment stepFragment = (BaseStepFragment) fragment;
                if (stepFragment.validateAndSave()) {
                    if (current < 2) {
                        viewPager.setCurrentItem(current + 1);
                    } else {
                        // 最后一个页面：复制方案
                        Fragment planFragment = getSupportFragmentManager().findFragmentByTag("f2");
                        if (planFragment instanceof GeneratePlanFragment) {
                            String fullPlanText = ((GeneratePlanFragment) planFragment).getFullPlanText();
                            if (!fullPlanText.isEmpty()) {
                                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                ClipData clip = ClipData.newPlainText("训练方案", fullPlanText);
                                clipboard.setPrimaryClip(clip);
                                Toast.makeText(this, "方案已复制到剪贴板", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, "没有可复制的方案", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
            } else {
                // Fragment 为空或不是预期类型，直接切换页面（如果在前两步）
                if (fragment == null) {
                    Log.w("MainActivity", "未找到当前页面的 Fragment: f" + current);
                } else {
                    Log.w("MainActivity", "当前 Fragment 不是 BaseStepFragment: " + fragment.getClass().getName());
                }
                if (current < 2) {
                    viewPager.setCurrentItem(current + 1);
                } else {
                    // 同样处理复制
                    Fragment planFragment = getSupportFragmentManager().findFragmentByTag("f2");
                    if (planFragment instanceof GeneratePlanFragment) {
                        String fullPlanText = ((GeneratePlanFragment) planFragment).getFullPlanText();
                        if (!fullPlanText.isEmpty()) {
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("训练方案", fullPlanText);
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(this, "方案已复制到剪贴板", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "没有可复制的方案", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
        });
    }

    private void updateButtons(int position) {
        btnPrev.setEnabled(position > 0);
        if (position == 2) {
            btnNext.setText("复制");
        } else {
            btnNext.setText("下一步");
        }
    }

    // ================== 数据导入相关 ==================
    private void registerImportReceiver() {
        importReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (DataImportService.ACTION_IMPORT_PROGRESS.equals(action)) {
                    String msg = intent.getStringExtra(DataImportService.EXTRA_MESSAGE);
                    int progress = intent.getIntExtra(DataImportService.EXTRA_PROGRESS, 0);
                    if (progressDialog == null) {
                        progressDialog = new ProgressDialogFragment();
                        progressDialog.setCancelable(false);
                        progressDialog.show(getSupportFragmentManager(), "import");
                    }
                    progressDialog.updateProgress(msg, progress);
                } else if (DataImportService.ACTION_IMPORT_COMPLETE.equals(action)) {
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                        progressDialog = null;
                    }
                    boolean success = intent.getBooleanExtra(DataImportService.EXTRA_SUCCESS, false);
                    String msg = intent.getStringExtra(DataImportService.EXTRA_MESSAGE);
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                    if (success) {
                        // 数据导入成功，刷新船员选择
                        viewModel.reset();
                        Fragment fragment = getSupportFragmentManager().findFragmentByTag("f0");
                        if (fragment instanceof CrewTargetFragment) {
                            ((CrewTargetFragment) fragment).refreshData();
                        }
                        checkDataValidity();
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(DataImportService.ACTION_IMPORT_PROGRESS);
        filter.addAction(DataImportService.ACTION_IMPORT_COMPLETE);
        LocalBroadcastManager.getInstance(this).registerReceiver(importReceiver, filter);
    }

    private void checkDatabase() {
        new Thread(() -> {
            DBHelper helper = new DBHelper(this);
            SQLiteDatabase db = helper.getReadableDatabase();
            Cursor cursor = null;
            try {
                cursor = db.rawQuery("SELECT COUNT(*) FROM " + DBHelper.TABLE_CREW, null);
                boolean hasData = cursor.moveToFirst() && cursor.getInt(0) > 0;
                if (!hasData) {
                    runOnUiThread(() -> {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("提示")
                                .setMessage("未检测到数据，请先导入数据包")
                                .setPositiveButton("导入", (dialog, which) -> openFilePicker())
                                .setNegativeButton("取消", null)
                                .setCancelable(false)
                                .show();
                    });
                }
            } finally {
                if (cursor != null) cursor.close();
                db.close();
            }
        }).start();
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
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
    }

    private void showExpiryDialog(String expiryDate, boolean isExpired) {
        new AlertDialog.Builder(this)
                .setTitle(isExpired ? "数据过期" : "数据有效")
                .setMessage(isExpired ? "当前数据有效期已过 (" + expiryDate + ")，部分数据可能不准确。\n请更新到最新数据。" :
                        "当前数据有效期至 " + expiryDate)
                .setPositiveButton("确定", null)
                .show();
    }

    // ================== 菜单 ==================
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_import) {
            openFilePicker();
            return true;
        } else if (item.getItemId() == R.id.action_credits) {
            showCreditsDialog();
            return true;
        } else if (item.getItemId() == R.id.action_about) {
            showAboutDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private void showCreditsDialog() {
        String creditsText = "感谢以下人员对本工具的贡献：\n\n" +
                "软件开发：有容大鱼，爪哇泡面\n" +
                "主要思路：靓仔05 - 国服\n" +
                "数据参考：茜尔莎 - 中舰小人计算器";
        new AlertDialog.Builder(this)
                .setTitle("致谢")
                .setMessage(creditsText)
                .setPositiveButton("确定", null)
                .show();
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
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_IMPORT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            DataImportService.startImport(this, uri);
        }
    }

    private void showAboutDialog() {
        String aboutMessage = "PSS船员训练值计算器\n\n" +
                "使用说明：\n" +
                "  首次使用需要导入船员数据。\n" +
                "  问题反馈邮箱：2192601632@qq.com\n";
        new AlertDialog.Builder(this)
                .setTitle("关于")
                .setMessage(aboutMessage)
                .setPositiveButton("确定", null)
                .setNeutralButton("复制邮箱", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("反馈邮箱", "2192601632@qq.com");
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "已复制邮箱", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(importReceiver);
    }
}