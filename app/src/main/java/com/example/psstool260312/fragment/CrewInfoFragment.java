package com.example.psstool260312.fragment;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.psstool260312.R;
import com.example.psstool260312.utils.DBHelper;

import java.io.File;

public class CrewInfoFragment extends Fragment {
    private static final String ARG_CREW_ID = "crew_id";
    private TextView crewName;
    private ImageView crewImage;
    private TextView equipments, special, collection, training;
    private TextView h, a, r, b, p, s, e, w;

    public static CrewInfoFragment newInstance(int crewId) {
        CrewInfoFragment fragment = new CrewInfoFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_CREW_ID, crewId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_crew_info, container, false);
        initViews(view);
        return view;
    }

    private void initViews(View view) {
        crewName = view.findViewById(R.id.crew_name);
        crewImage = view.findViewById(R.id.crew_image);
        equipments = view.findViewById(R.id.equipments);
        special = view.findViewById(R.id.special);
        collection = view.findViewById(R.id.collection);
        training = view.findViewById(R.id.training);
        h = view.findViewById(R.id.h);
        a = view.findViewById(R.id.a);
        r = view.findViewById(R.id.r);
        b = view.findViewById(R.id.b);
        p = view.findViewById(R.id.p);
        s = view.findViewById(R.id.s);
        e = view.findViewById(R.id.e);
        w = view.findViewById(R.id.w);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    @SuppressLint("SetTextI18n")
    private void loadData() {
        if (getArguments() == null) return;
        int crewId = getArguments().getInt(ARG_CREW_ID);
        if (crewId <= 0) return;

        new Thread(() -> {
            DBHelper dbHelper = new DBHelper(requireContext());
            SQLiteDatabase db = dbHelper.getReadableDatabase();

            Cursor cursor = db.rawQuery(
                    "SELECT name_zh, equipments, special, collection, h, a, r, b, p, s, e, w, t FROM " + DBHelper.TABLE_CREW + " WHERE id=?",
                    new String[]{String.valueOf(crewId)}
            );

            String name = null;
            String eq = null, sp = null, col = null;
            float valH = 0, valA = 0, valR = 0, valB = 0;
            float valP = 0, valS = 0, valE = 0, valW = 0;
            int valT = 0;

            if (cursor.moveToFirst()) {
                name = cursor.getString(0);
                eq = cursor.getString(1);
                sp = cursor.getString(2);
                col = cursor.getString(3);
                valH = cursor.getFloat(4);
                valA = cursor.getFloat(5);
                valR = cursor.getFloat(6);
                valB = cursor.getFloat(7);
                valP = cursor.getFloat(8);
                valS = cursor.getFloat(9);
                valE = cursor.getFloat(10);
                valW = cursor.getFloat(11);
                valT = cursor.getInt(12);
            }
            cursor.close();
            db.close();

            // 加载图片
            File imageFile = new File(requireContext().getFilesDir(), "crew_images/" + crewId + ".png");
            Bitmap bitmap = null;
            if (imageFile.exists() && imageFile.length() > 0) {
                bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
            }
            Bitmap finalBitmap = null;
            if (bitmap != null) {
                int targetWidth = (int) (100 * getResources().getDisplayMetrics().density);
                int targetHeight = (int) (120 * getResources().getDisplayMetrics().density);
                float scale = Math.min((float) targetWidth / bitmap.getWidth(),
                        (float) targetHeight / bitmap.getHeight());
                int scaledWidth = Math.round(bitmap.getWidth() * scale);
                int scaledHeight = Math.round(bitmap.getHeight() * scale);
                finalBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, false);
            }

            final String finalName = name;
            final String finalEq = eq;
            final String finalSp = sp;
            final String finalCol = col;
            final float finalValH = valH;
            final float finalValA = valA;
            final float finalValR = valR;
            final float finalValB = valB;
            final float finalValP = valP;
            final float finalValS = valS;
            final float finalValE = valE;
            final float finalValW = valW;
            final int finalValT = valT;
            final Bitmap finalBitmapToSet = finalBitmap;

            requireActivity().runOnUiThread(() -> {
                if (finalName != null) {
                    crewName.setText(finalName);
                    equipments.setText("装备位: " + (finalEq != null ? finalEq : "无"));
                    special.setText("技能: " + (finalSp != null ? finalSp : "无"));
                    collection.setText("团队: " + (finalCol != null ? finalCol : "无"));
                    training.setText("训练上限: " + finalValT);
                    h.setText("血量: " + finalValH);
                    a.setText("攻击: " + finalValA);
                    r.setText("维修: " + finalValR);
                    b.setText("能力: " + finalValB);
                    p.setText("导航: " + finalValP);
                    s.setText("科技: " + finalValS);
                    e.setText("引擎: " + finalValE);
                    w.setText("武器: " + finalValW);
                }
                if (finalBitmapToSet != null) {
                    crewImage.setImageBitmap(finalBitmapToSet);
                    crewImage.setScaleType(ImageView.ScaleType.CENTER);
                } else {
                    crewImage.setImageResource(R.drawable.ic_launcher_foreground);
                    crewImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
                }
            });
        }).start();
    }
}