package com.example.psstool260312.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.example.psstool260312.service.DataImportService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {

    private static final String TAG = "UpdateChecker";
    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/dalianyizhang/PssPrestige260311/contents/?ref=data";
    private static final String PREF_NAME = "update_info";
    private static final String PREF_LAST_UPDATE_DATE = "last_update_date";

    public interface UpdateListener {
        void onCheckComplete(boolean hasUpdate, String latestDate, String downloadUrl);

        void onDownloadProgress(int progress, String message);

        void onDownloadComplete(boolean success, String message);
    }

    /**
     * 检查更新（异步）
     */
    public static void checkForUpdate(Context context, UpdateListener listener) {
        new Thread(() -> {
            try {
                URL url = new URL(GITHUB_API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    Log.e(TAG, "GitHub API 返回错误码: " + responseCode);
                    if (listener != null) listener.onCheckComplete(false, null, null);
                    return;
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                conn.disconnect();

                JSONArray jsonArray = new JSONArray(response.toString());
                String latestDate = null;
                String downloadUrl = null;

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    String name = obj.getString("name");
                    if (name.startsWith("合成数据_") && name.endsWith(".zip")) {
                        // 提取日期：合成数据_YYYYMMDD.zip
                        String datePart = name.substring(5, name.length() - 4);
                        if (datePart.length() == 8 && isNumeric(datePart)) {
                            if (latestDate == null || datePart.compareTo(latestDate) > 0) {
                                latestDate = datePart;
                                downloadUrl = obj.getString("download_url");
                            }
                        }
                    }
                }

                if (latestDate == null) {
                    Log.w(TAG, "未找到任何数据包");
                    if (listener != null) listener.onCheckComplete(false, null, null);
                    return;
                }

                SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                String localDate = prefs.getString(PREF_LAST_UPDATE_DATE, null);
                boolean hasUpdate = (localDate == null) || (latestDate.compareTo(localDate) > 0);

                Log.i(TAG, "本地版本: " + localDate + ", 远程版本: " + latestDate + ", 需要更新: " + hasUpdate);
                if (listener != null) {
                    listener.onCheckComplete(hasUpdate, latestDate, downloadUrl);
                }

            } catch (Exception e) {
                Log.e(TAG, "检查更新失败", e);
                if (listener != null) listener.onCheckComplete(false, null, null);
            }
        }).start();
    }

    /**
     * 下载数据包并导入（异步）
     */
    public static void downloadAndImport(Context context, String downloadUrl,
                                         String versionDate, UpdateListener listener) {
        new Thread(() -> {
            try {
                // 下载到外部缓存目录（无需存储权限）
                File cacheDir = context.getExternalCacheDir();
                if (cacheDir == null) cacheDir = context.getCacheDir();
                File zipFile = new File(cacheDir, "update_" + versionDate + ".zip");
                if (zipFile.exists()) zipFile.delete();

                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int totalSize = conn.getContentLength();
                InputStream input = conn.getInputStream();
                FileOutputStream output = new FileOutputStream(zipFile);

                byte[] buffer = new byte[8192];
                int read;
                int downloaded = 0;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    downloaded += read;
                    if (totalSize > 0) {
                        int progress = (int) (downloaded * 100L / totalSize);
                        if (listener != null) {
                            listener.onDownloadProgress(progress, "下载中 " + progress + "%");
                        }
                    }
                }
                output.close();
                input.close();
                conn.disconnect();

                // 授予 DataImportService 读取权限（FileProvider）
                Uri uri = FileProvider.getUriForFile(context,
                        context.getPackageName() + ".fileprovider", zipFile);
                context.grantUriPermission("com.example.psstool260312.service.DataImportService",
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

                DataImportService.startImport(context, uri);

                if (listener != null) {
                    listener.onDownloadComplete(true, "数据包下载完成，开始导入...");
                }

            } catch (Exception e) {
                Log.e(TAG, "下载或导入失败", e);
                if (listener != null) {
                    listener.onDownloadComplete(false, "失败: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * 更新本地存储的版本日期
     */
    public static void updateLocalVersion(Context context, String date) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_LAST_UPDATE_DATE, date).apply();
        Log.i(TAG, "本地版本已更新为 " + date);
    }

    private static boolean isNumeric(String str) {
        for (char c : str.toCharArray()) {
            if (!Character.isDigit(c)) return false;
        }
        return true;
    }
}