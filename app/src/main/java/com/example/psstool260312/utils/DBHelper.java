package com.example.psstool260312.utils;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * author：leeannm
 * time：2026/3/12 21:01
 */
public class DBHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "training_calculator.db";
    private static final int DATABASE_VERSION = 3;

    // 表名
    public static final String TABLE_CREW = "crew";
    public static final String TABLE_CREW_GROUP = "crew_group";
    public static final String TABLE_SAVED_CONFIGS = "saved_configs";

    // 列名常量（用于升级）
    private static final String COL_ID = "id";
    private static final String COL_CONFIG_NAME = "config_name";
    private static final String COL_CREW_ID = "crew_id";
    private static final String COL_CREATED_TIME = "created_time";

    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createCrewTable(db);
        createCrewGroupTable(db);
        createIndexes(db);
        createSavedConfigsTable(db);   // 直接创建完整的 saved_configs 表（版本3结构）
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i("DBHelper", "升级数据库从 " + oldVersion + " 到 " + newVersion);

        // 从版本1升级到版本2：添加 saved_configs 表（旧版本，只有基础列）
        if (oldVersion < 2) {
            createSavedConfigsTableV1(db);
            oldVersion = 2;  // 模拟升级完成
        }

        // 从版本2升级到版本3：添加新列（装备拆分和目标值）
        if (oldVersion < 3) {
            upgradeToVersion3(db);
        }
    }

    // ========== 表创建方法 ==========
    private void createCrewTable(SQLiteDatabase db) {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_CREW + " (" +
                "id INTEGER PRIMARY KEY, " +
                "name_en TEXT, " +
                "name_zh TEXT, " +
                "rarity TEXT, " +
                "equipments TEXT, " +
                "special TEXT, " +
                "collection TEXT, " +
                "h REAL, a REAL, r REAL, b REAL, p REAL, s REAL, e REAL, w REAL, " +
                "f INTEGER, l INTEGER, u INTEGER, t INTEGER)";
        db.execSQL(sql);
    }

    private void createCrewGroupTable(SQLiteDatabase db) {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_CREW_GROUP + " (" +
                "father_id INTEGER NOT NULL, " +
                "first_son_id INTEGER NOT NULL, " +
                "second_son_id INTEGER NOT NULL, " +
                "PRIMARY KEY (first_son_id, second_son_id))";
        db.execSQL(sql);
    }

    private void createIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_crew_rarity ON " + TABLE_CREW + "(rarity)");
    }

    // 版本3的完整 saved_configs 表（包含所有列）
    private void createSavedConfigsTable(SQLiteDatabase db) {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_SAVED_CONFIGS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "config_name TEXT NOT NULL, " +
                "crew_id INTEGER NOT NULL, " +
                "created_time INTEGER DEFAULT (strftime('%s','now')), " +

                // 血量 (h)
                "h_train REAL, h_eq1m REAL, h_eq1s REAL, h_eq2m REAL, h_eq2s REAL, " +
                "target_h REAL, " +

                // 攻击 (a)
                "a_train REAL, a_eq1m REAL, a_eq1s REAL, a_eq2m REAL, a_eq2s REAL, " +
                "target_a REAL, " +

                // 维修 (r)
                "r_train REAL, r_eq1m REAL, r_eq1s REAL, r_eq2m REAL, r_eq2s REAL, " +
                "target_r REAL, " +

                // 能力 (b)
                "b_train REAL, b_eq1m REAL, b_eq1s REAL, b_eq2m REAL, b_eq2s REAL, " +
                "target_b REAL, " +

                // 耐力 (st)
                "st_train REAL, st_eq1m REAL, st_eq1s REAL, st_eq2m REAL, st_eq2s REAL, " +
                "target_st REAL, " +

                // 导航 (p)
                "p_train REAL, p_eq1m REAL, p_eq1s REAL, p_eq2m REAL, p_eq2s REAL, " +
                "target_p REAL, " +

                // 科技 (s)
                "s_train REAL, s_eq1m REAL, s_eq1s REAL, s_eq2m REAL, s_eq2s REAL, " +
                "target_s REAL, " +

                // 引擎 (e)
                "e_train REAL, e_eq1m REAL, e_eq1s REAL, e_eq2m REAL, e_eq2s REAL, " +
                "target_e REAL, " +

                // 武器 (w)
                "w_train REAL, w_eq1m REAL, w_eq1s REAL, w_eq2m REAL, w_eq2s REAL, " +
                "target_w REAL, " +

                // 火抗 (f)
                "f_train REAL, f_eq1m REAL, f_eq1s REAL, f_eq2m REAL, f_eq2s REAL, " +
                "target_f REAL " +
                ")";
        db.execSQL(sql);
    }

    // 版本2的 saved_configs 表（只有基础列，用于升级）
    private void createSavedConfigsTableV1(SQLiteDatabase db) {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_SAVED_CONFIGS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "config_name TEXT NOT NULL, " +
                "crew_id INTEGER NOT NULL, " +
                "created_time INTEGER DEFAULT (strftime('%s','now'))" +
                ")";
        db.execSQL(sql);
    }

    // 从版本2升级到版本3：添加新列
    private void upgradeToVersion3(SQLiteDatabase db) {
        Log.i("DBHelper", "升级到版本3：添加装备拆分字段和目标值字段");

        // 装备拆分字段（每个属性四个装备值）
        String[] equipColumns = {"eq1m", "eq1s", "eq2m", "eq2s"};
        // 目标值字段（每个属性的目标值）
        String[] attrCols = {"h", "a", "r", "b", "st", "p", "s", "e", "w", "f"};

        // 添加装备拆分字段
        for (String col : equipColumns) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_SAVED_CONFIGS + " ADD COLUMN " + col + " REAL DEFAULT 0");
            } catch (Exception e) {
                Log.e("DBHelper", "添加列 " + col + " 失败，可能已存在", e);
            }
        }

        // 添加目标值字段
        for (String col : attrCols) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_SAVED_CONFIGS + " ADD COLUMN target_" + col + " REAL DEFAULT 0");
            } catch (Exception e) {
                Log.e("DBHelper", "添加列 target_" + col + " 失败，可能已存在", e);
            }
        }
    }
}