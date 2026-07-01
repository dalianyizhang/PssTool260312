package com.example.psstool260312;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.psstool260312.adapter.ToolAdapter;
import com.example.psstool260312.fragment.ProgressDialogFragment;
import com.example.psstool260312.service.DataImportService;
import com.example.psstool260312.utils.UpdateChecker;

import java.util.ArrayList;
import java.util.List;

public class GuideActivity extends AppCompatActivity {

    private String pendingUpdateDate;          // 待更新版本日期
    private AlertDialog updateDialog;          // 更新提示对话框
    private ProgressDialogFragment progressDialog;  // 进度对话框
    private UpdateChecker.UpdateListener updateListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide);

        RecyclerView recyclerView = findViewById(R.id.recycler_tools);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 工具列表
        List<ToolAdapter.ToolItem> tools = new ArrayList<>();

        tools.add(new ToolAdapter.ToolItem(
                "基础船员表",
                "多条件筛选船员显示",
                CrewFilterActivity.class,
                R.drawable.ic_filter
        ));

        tools.add(new ToolAdapter.ToolItem(
                "船员训练值计算器",
                "规划船员训练值与培养步骤",
                MainActivity.class,
                R.drawable.ic_calculator
        ));

        // 在 initTools() 或类似位置添加
        tools.add(new ToolAdapter.ToolItem(
                "船员合成查询",
                "查询船员的合成路径",
                CraftInfoActivity.class,
                R.drawable.ic_craft
        ));

        ToolAdapter adapter = new ToolAdapter(tools);
        recyclerView.setAdapter(adapter);

        // 初始化成员变量 UpdateListener
        updateListener = new UpdateChecker.UpdateListener() {
            @Override
            public void onCheckComplete(boolean hasUpdate, String latestDate, String downloadUrl) {
                if (hasUpdate && downloadUrl != null) {
                    pendingUpdateDate = latestDate;
                    runOnUiThread(() -> showUpdateDialog(latestDate, downloadUrl));
                }
            }

            @Override
            public void onDownloadProgress(int progress, String message) {
                runOnUiThread(() -> {
                    if (progressDialog == null) {
                        progressDialog = new ProgressDialogFragment();
                        progressDialog.setCancelable(false);
                        progressDialog.show(getSupportFragmentManager(), "update_progress");
                    }
                    progressDialog.updateProgress(message, progress);
                });
            }

            @Override
            public void onDownloadComplete(boolean success, String message) {
                runOnUiThread(() -> {
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                        progressDialog = null;
                    }
                    Toast.makeText(GuideActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        };

        // 注册广播接收器（监听导入完成）
        LocalBroadcastManager.getInstance(this).registerReceiver(importReceiver,
                new IntentFilter(DataImportService.ACTION_IMPORT_COMPLETE));

        // 检查数据更新
        checkForUpdate();
    }

    private BroadcastReceiver importReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(DataImportService.EXTRA_SUCCESS, false);
            if (success && pendingUpdateDate != null) {
                // 导入成功，更新本地版本
                UpdateChecker.updateLocalVersion(GuideActivity.this, pendingUpdateDate);
                pendingUpdateDate = null;
                Toast.makeText(GuideActivity.this, "数据更新成功", Toast.LENGTH_SHORT).show();
            }
        }
    };


    /**
     * 调用封装的方法，检查更新
     */
    private void checkForUpdate() {
        // 直接使用成员变量 updateListener，避免重复创建
        UpdateChecker.checkForUpdate(this, updateListener);
    }

    /**
     * 弹窗方法
     * @param versionDate
     * @param downloadUrl
     */
    private void showUpdateDialog(String versionDate, String downloadUrl) {
        if (updateDialog != null && updateDialog.isShowing()) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("发现新数据包")
                .setMessage("检测到新版本数据（" + versionDate + "），是否立即更新？")
                .setPositiveButton("更新", (dialog, which) -> {
                    updateDialog = null;
                    // 开始下载
                    UpdateChecker.downloadAndImport(GuideActivity.this, downloadUrl, versionDate, updateListener);
                })
                .setNegativeButton("暂不", (dialog, which) -> {
                    updateDialog = null;
                    // 记录本次忽略，一周内不再提示（可选）
                })
                .setCancelable(false);
        updateDialog = builder.show();
    }

    /**
     * 在 onDestroy 中取消注册
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(importReceiver);
        if (updateDialog != null) {
            updateDialog.dismiss();
            updateDialog = null;
        }
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }
}