package com.example.psstool260312.service;

/**
 * author：leeannm
 * time：2026/3/12 21:20
 */

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.psstool260312.utils.DBHelper;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DataImportService extends IntentService {
    private static final String TAG = "DataImportService";
    public static final String ACTION_IMPORT_PROGRESS = "import_progress";
    public static final String ACTION_IMPORT_COMPLETE = "import_complete";
    public static final String ACTION_DATABASE_UPDATED = "database_updated";
    public static final String EXTRA_PROGRESS = "progress";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_SUCCESS = "success";

    private static final String VALID_UNTIL_FILENAME = "valid_until.txt";
    private static final String PREFS_NAME = "data_info";
    private static final String PREFS_KEY_VALID_UNTIL = "valid_until";

    public DataImportService() {
        super("DataImportService");
    }

    public static void startImport(Context context, Uri uri) {
        Intent intent = new Intent(context, DataImportService.class);
        intent.setData(uri);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null || intent.getData() == null) return;

        Uri zipUri = intent.getData();
        sendProgress("开始导入数据", 0);

        SQLiteDatabase db = null;
        try {
            // 1. 解压ZIP文件
            File outputDir = getOutputDirectory();
            int[] totalFiles = unzipFile(zipUri, outputDir);
            sendProgress("解压完成", 10);

            String validUntil = readValidUntilFile(outputDir);
            if (!validUntil.isEmpty()) {
                saveValidUntil(validUntil);
                sendProgress("有效期设置完成", 15);
            } else {
                sendProgress("未找到有效期文件", 15);
            }

            // 2. 导入数据
            DBHelper dbHelper = new DBHelper(this);
            db = dbHelper.getWritableDatabase();

            // 禁用外键约束以提高性能
            db.execSQL("PRAGMA foreign_keys = OFF;");
            db.beginTransaction();

            // 清空 crew表 和 crew_group表
            db.delete(DBHelper.TABLE_CREW, null, null);
            db.delete(DBHelper.TABLE_CREW_GROUP, null, null);
            sendProgress("已清除旧数据", 20);

            // 导入crew表
            int crewFiles = importCrewData(db, outputDir);
            sendProgress("船员数据导入完成", 60);

            // 导入group表
            int groupFiles = importGroupData(db, outputDir);
            sendProgress("合成路径导入完成", 90);

            db.setTransactionSuccessful();
            sendProgress("数据导入成功", 100);

            // 清理临时文件
            deleteRecursive(outputDir);

            // 发送完成广播
            Intent completeIntent = new Intent(ACTION_IMPORT_COMPLETE);
            completeIntent.putExtra(EXTRA_SUCCESS, true);
            String message = "成功导入: " + crewFiles + " 船员文件, " + groupFiles + " 合成路径文件";
            if (!validUntil.isEmpty()) {
                message += "\n数据有效期至: " + validUntil;
            }
            completeIntent.putExtra(EXTRA_MESSAGE, message);
            LocalBroadcastManager.getInstance(this).sendBroadcast(completeIntent);

            // 同时发送全局广播更新菜单状态
            Intent updateIntent = new Intent(ACTION_DATABASE_UPDATED);
            LocalBroadcastManager.getInstance(this).sendBroadcast(updateIntent);
        } catch (Exception e) {
            Log.e(TAG, "导入失败", e);

            // 发送错误广播
            Intent errorIntent = new Intent(ACTION_IMPORT_COMPLETE);
            errorIntent.putExtra(EXTRA_SUCCESS, false);
            errorIntent.putExtra(EXTRA_MESSAGE, "导入失败: " + e.getMessage());
            LocalBroadcastManager.getInstance(this).sendBroadcast(errorIntent);
        } finally {
            if (db != null && db.inTransaction()) {
                db.endTransaction(); // 需要添加这行
            }

            // 关闭数据库连接
            if (db != null) {
                db.close();
            }
        }
    }

    /**
     * 获取或创建存放船员图片的目录（内部存储）
     */
    private File getCrewImageDirectory() {
        File dir = new File(getFilesDir(), "crew_images");
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "无法创建图片目录: " + dir.getAbsolutePath());
        }
        return dir;
    }

    // 读取有效期文件
    private String readValidUntilFile(File dir) {
        File validFile = new File(dir, VALID_UNTIL_FILENAME);
        if (!validFile.exists()) {
            Log.w(TAG, "有效期文件不存在: " + validFile.getAbsolutePath());
            return "";
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(validFile), StandardCharsets.UTF_8))) {

            // 读取第一行作为有效期
            String line = reader.readLine();
            if (line != null && !line.trim().isEmpty()) {
                return line.trim();
            }
            return "";
        } catch (IOException e) {
            Log.e(TAG, "读取有效期文件失败", e);
            return "";
        }
    }

    // 保存有效期到SharedPreferences
    private void saveValidUntil(String date) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(PREFS_KEY_VALID_UNTIL, date).apply();
        Log.i(TAG, "保存有效期: " + date);
    }

    private File getOutputDirectory() {
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "import_temp");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("无法创建临时目录");
        }
        return dir;
    }

    /**
     * 解压数据包，图片直接转储私有目录，csv转入待处理
     *
     * @param zipUri
     * @param outputDir
     * @return 返回图片文件数
     * @throws Exception
     */
    private int[] unzipFile(Uri zipUri, File outputDir) throws Exception {
        int fileCount = 0;
        int imageCount = 0;
        try (InputStream is = getContentResolver().openInputStream(zipUri);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is))) {

            ZipEntry entry;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                String entryName = entry.getName();
                // 判断是否为 crew/ 下的 PNG 图片
                if (entryName.startsWith("crew/") && entryName.toLowerCase().endsWith(".png")) {
                    // 提取文件名（例如 "123.png"）
                    String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
                    File imageFile = new File(getCrewImageDirectory(), fileName);

                    // 确保父目录存在（理论上已存在，但保险起见）
                    File parent = imageFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new RuntimeException("无法创建图片子目录: " + parent.getAbsolutePath());
                    }

                    // 写入图片文件
                    try (OutputStream os = new FileOutputStream(imageFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                    }
                    imageCount++;
                    Log.d(TAG, "已保存图片: " + fileName);
                } else {
                    // 其他文件（CSV、TXT）解压到临时目录
                    File outputFile = new File(outputDir, entry.getName());
                    File parent = outputFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new RuntimeException("无法创建目录: " + parent.getAbsolutePath());
                    }

                    try (OutputStream os = new FileOutputStream(outputFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                    }

                    fileCount++;
                }

                zis.closeEntry();
            }
        }
        Log.i(TAG, String.format("解压完成: 临时文件 %d 个, 图片 %d 个", fileCount, imageCount));
        return new int[]{fileCount, imageCount};
    }


    private int importCrewData(SQLiteDatabase db, File dir) throws Exception {
        int fileCount = 0;
        File[] files = dir.listFiles((d, name) -> name.startsWith("crew_") && name.endsWith(".csv"));

        if (files == null) return 0;

        for (File file : files) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
                // 跳过表头
                br.readLine();

                String line;
                int rowCount = 0;

                while ((line = br.readLine()) != null) {
                    String[] values = line.split(",");
                    if (values.length < 18) continue;

                    ContentValues cv = new ContentValues();
                    cv.put("id", parseInt(values[0]));
                    cv.put("name_zh", values[1]);
                    cv.put("rarity", values[2]);
                    cv.put("equipments", values[3]);
                    cv.put("special", values[4]);
                    cv.put("collection", values[5]);
                    cv.put("h", parseFloat(values[6]));
                    cv.put("a", parseFloat(values[7]));
                    cv.put("r", parseFloat(values[8]));
                    cv.put("b", parseFloat(values[9]));
                    cv.put("p", parseFloat(values[10]));
                    cv.put("s", parseFloat(values[11]));
                    cv.put("e", parseFloat(values[12]));
                    cv.put("w", parseFloat(values[13]));
                    cv.put("f", parseInt(values[14]));
                    cv.put("l", parseInt(values[15]));
                    cv.put("u", parseInt(values[16]));
                    cv.put("t", parseInt(values[17]));

                    db.insertWithOnConflict(DBHelper.TABLE_CREW, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                    rowCount++;
                }

                Log.i(TAG, "导入文件: " + file.getName() + ", 行数: " + rowCount);
                fileCount++;
            }
        }
        return fileCount;
    }

    private int importGroupData(SQLiteDatabase db, File dir) throws Exception {
        int fileCount = 0;
        int totalRows = 0;
        File[] files = dir.listFiles((d, name) -> name.startsWith("prestige_") && name.endsWith(".csv"));

        if (files == null) return 0;

        // 批量处理设置
        final int BATCH_SIZE = 100;
        List<ContentValues> batch = new ArrayList<>(BATCH_SIZE);

        for (File file : files) {
            Log.i(TAG, "开始导入合成路径文件: " + file.getName());
            int rowCount = 0;
            int skipped = 0;

            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                // 跳过表头
                br.readLine();

                String line;
                while ((line = br.readLine()) != null) {
                    String[] values = line.split(",", -1); // 保留空值
                    if (values.length < 3) {
                        skipped++;
                        continue;
                    }

                    // 直接获取ID值
                    int son1Id = parseInt(values[0].trim());
                    int son2Id = parseInt(values[1].trim());
                    int fatherId = parseInt(values[2].trim());

                    // 如果ID无效则跳过
                    if (son1Id == 0 || son2Id == 0 || fatherId == 0) {
                        Log.w(TAG, "无效的ID: " + line);
                        skipped++;
                        continue;
                    }

                    // 创建ContentValues
                    ContentValues cv = new ContentValues();
                    cv.put("father_id", fatherId);
                    cv.put("first_son_id", son1Id);
                    cv.put("second_son_id", son2Id);
                    batch.add(cv);
                    rowCount++;

                    // 批量插入
                    if (batch.size() >= BATCH_SIZE) {
                        insertBatch(db, batch);
                        batch.clear();
                    }
                }

                // 插入剩余批次
                if (!batch.isEmpty()) {
                    insertBatch(db, batch);
                    batch.clear();
                }

                Log.i(TAG, String.format("导入合成路径: %s, 成功行数: %d, 跳过行数: %d",
                        file.getName(), rowCount, skipped));
                fileCount++;
                totalRows += rowCount;
            } catch (Exception e) {
                Log.e(TAG, "导入文件失败: " + file.getName(), e);
                throw e;
            }
        }

        Log.i(TAG, "总计导入合成路径文件: " + fileCount + ", 总行数: " + totalRows);
        return fileCount;
    }

    private void insertBatch(SQLiteDatabase db, List<ContentValues> batch) {
        try {
            db.beginTransaction();
            for (ContentValues cv : batch) {
                db.insertWithOnConflict(DBHelper.TABLE_CREW_GROUP, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private int parseInt(String value) {
        try {
            // 空值处理
            if (value == null || value.trim().isEmpty()) {
                return 0;
            }
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            Log.w(TAG, "无法解析整数: " + value);
            return 0;
        }
    }

    private float parseFloat(String value) {
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    private void sendProgress(String message, int progress) {
        Intent intent = new Intent(ACTION_IMPORT_PROGRESS);
        intent.putExtra(EXTRA_MESSAGE, message);
        intent.putExtra(EXTRA_PROGRESS, progress);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}

