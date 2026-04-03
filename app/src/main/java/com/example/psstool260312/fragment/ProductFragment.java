package com.example.psstool260312.fragment;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.psstool260312.CraftInfoActivity;
import com.example.psstool260312.R;
import com.example.psstool260312.utils.DBHelper;
import com.example.psstool260312.utils.PinyinUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProductFragment extends Fragment {
    private static final String ARG_CREW_ID = "crew_id";
    private RecyclerView recyclerView;
    private TextView emptyView;
    private ProductGroupAdapter adapter;
    private List<ProductGroup> groups = new ArrayList<>();

    public static ProductFragment newInstance(int crewId) {
        ProductFragment fragment = new ProductFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_CREW_ID, crewId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_product, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        emptyView = view.findViewById(R.id.emptyView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        if (getArguments() == null) return;
        int crewId = getArguments().getInt(ARG_CREW_ID);
        if (crewId <= 0) return;

        new Thread(() -> {
            DBHelper dbHelper = new DBHelper(requireContext());
            SQLiteDatabase db = dbHelper.getReadableDatabase();

            // 当前船员名字（目标）
            String targetName = "";
            Cursor nameCursor = db.rawQuery("SELECT name_zh FROM " + DBHelper.TABLE_CREW + " WHERE id=?", new String[]{String.valueOf(crewId)});
            if (nameCursor.moveToFirst()) targetName = nameCursor.getString(0);
            nameCursor.close();

            // 查询当前船员作为产物的所有组
            String query = "SELECT c1.name_zh AS son1, c2.name_zh AS son2 " +
                    "FROM " + DBHelper.TABLE_CREW_GROUP + " g " +
                    "JOIN " + DBHelper.TABLE_CREW + " c1 ON g.first_son_id = c1.id " +
                    "JOIN " + DBHelper.TABLE_CREW + " c2 ON g.second_son_id = c2.id " +
                    "WHERE g.father_id = ?";
            Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(crewId)});

            Map<String, List<String>> groupMap = new HashMap<>();
            while (cursor.moveToNext()) {
                String son1 = cursor.getString(0);
                String son2 = cursor.getString(1);
                String[] materials = new String[]{son1, son2};
                Arrays.sort(materials, (a, b) -> PinyinUtils.compare(a, b));
                String material1 = materials[0];
                String material2 = materials[1];
                if (!groupMap.containsKey(material1)) groupMap.put(material1, new ArrayList<>());
                groupMap.get(material1).add(material2);
            }
            cursor.close();
            db.close();

            List<ProductGroup> tempGroups = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : groupMap.entrySet()) {
                ProductGroup group = new ProductGroup();
                group.material1 = entry.getKey();
                group.material2List = entry.getValue();
                tempGroups.add(group);
            }
            Collections.sort(tempGroups, (o1, o2) -> PinyinUtils.compare(o1.material1, o2.material1));

            final String finalTargetName = targetName;
            requireActivity().runOnUiThread(() -> {
                groups.clear();
                groups.addAll(tempGroups);
                adapter = new ProductGroupAdapter(groups, finalTargetName, (CraftInfoActivity) requireActivity());
                recyclerView.setAdapter(adapter);
                updateEmptyView(groups.isEmpty());
            });
        }).start();
    }

    private void updateEmptyView(boolean isEmpty) {
        if (isEmpty) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            emptyView.setText("该船员没有作为产物的合成路径");
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private static class ProductGroup {
        String material1;
        List<String> material2List;
    }

    private static class ProductGroupAdapter extends RecyclerView.Adapter<ProductGroupAdapter.GroupViewHolder> {
        private List<ProductGroup> groups;
        private String targetName;
        private CraftInfoActivity activity;

        ProductGroupAdapter(List<ProductGroup> groups, String targetName, CraftInfoActivity activity) {
            this.groups = groups;
            this.targetName = targetName;
            this.activity = activity;
        }

        @NonNull
        @Override
        public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product_group, parent, false);
            return new GroupViewHolder(view);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
            ProductGroup group = groups.get(position);
            holder.material1Text.setText(group.material1);

            GestureDetector material1Detector = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    activity.showCrewInfo(group.material1);
                    return true;
                }
            });
            holder.material1Text.setOnTouchListener((v, event) -> {
                material1Detector.onTouchEvent(event);
                return true;
            });

            holder.subItemsContainer.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(holder.itemView.getContext());

            for (String material2 : group.material2List) {
                TextView material2View = (TextView) inflater.inflate(R.layout.item_sub_row_product, holder.subItemsContainer, false);
                material2View.setText(material2);

                GestureDetector material2Detector = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        activity.showCrewInfo(material2);
                        return true;
                    }

                    @Override
                    public void onLongPress(MotionEvent e) {
                        String formula = group.material1 + "+" + material2 + "→" + targetName;
                        copyToClipboard(activity, formula);
                    }
                });
                material2View.setOnTouchListener((v, event) -> {
                    material2Detector.onTouchEvent(event);
                    return true;
                });
                holder.subItemsContainer.addView(material2View);
            }
        }

        @Override
        public int getItemCount() {
            return groups.size();
        }

        static class GroupViewHolder extends RecyclerView.ViewHolder {
            TextView material1Text;
            LinearLayout subItemsContainer;

            GroupViewHolder(View itemView) {
                super(itemView);
                material1Text = itemView.findViewById(R.id.material1_name);
                subItemsContainer = itemView.findViewById(R.id.sub_items_container);
            }
        }

        private void copyToClipboard(Context context, String text) {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("合成配方", text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, "已复制: " + text, Toast.LENGTH_SHORT).show();
        }
    }
}