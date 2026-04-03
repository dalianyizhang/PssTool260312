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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MaterialFragment extends Fragment {
    private static final String ARG_CREW_ID = "crew_id";
    private RecyclerView recyclerView;
    private TextView emptyView;
    private MaterialGroupAdapter adapter;
    private List<MaterialGroup> groups = new ArrayList<>();

    public static MaterialFragment newInstance(int crewId) {
        MaterialFragment fragment = new MaterialFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_CREW_ID, crewId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_material, container, false);
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

            // 当前船员名字
            String currentCrewName = "";
            Cursor nameCursor = db.rawQuery("SELECT name_zh FROM " + DBHelper.TABLE_CREW + " WHERE id=?", new String[]{String.valueOf(crewId)});
            if (nameCursor.moveToFirst()) currentCrewName = nameCursor.getString(0);
            nameCursor.close();

            // 查询包含当前船员的合成组
            String query = "SELECT c3.name_zh AS father_name, c1.name_zh AS son1, c2.name_zh AS son2 " +
                    "FROM " + DBHelper.TABLE_CREW_GROUP + " g " +
                    "JOIN " + DBHelper.TABLE_CREW + " c1 ON g.first_son_id = c1.id " +
                    "JOIN " + DBHelper.TABLE_CREW + " c2 ON g.second_son_id = c2.id " +
                    "JOIN " + DBHelper.TABLE_CREW + " c3 ON g.father_id = c3.id " +
                    "WHERE g.first_son_id = ? OR g.second_son_id = ?";
            Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(crewId), String.valueOf(crewId)});

            Map<String, List<String>> groupMap = new HashMap<>();
            while (cursor.moveToNext()) {
                String father = cursor.getString(0);
                String son1 = cursor.getString(1);
                String son2 = cursor.getString(2);
                String material2 = son1.equals(currentCrewName) ? son2 : son1;
                if (!groupMap.containsKey(father)) groupMap.put(father, new ArrayList<>());
                groupMap.get(father).add(material2);
            }
            cursor.close();
            db.close();

            List<MaterialGroup> tempGroups = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : groupMap.entrySet()) {
                MaterialGroup group = new MaterialGroup();
                group.target = entry.getKey();
                group.material2List = entry.getValue();
                tempGroups.add(group);
            }
            Collections.sort(tempGroups, (o1, o2) -> PinyinUtils.compare(o1.target, o2.target));

            final String finalCurrentCrewName = currentCrewName;
            requireActivity().runOnUiThread(() -> {
                groups.clear();
                groups.addAll(tempGroups);
                adapter = new MaterialGroupAdapter(groups, finalCurrentCrewName, (CraftInfoActivity) requireActivity());
                recyclerView.setAdapter(adapter);
                updateEmptyView(groups.isEmpty());
            });
        }).start();
    }

    private void updateEmptyView(boolean isEmpty) {
        if (isEmpty) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            emptyView.setText("该船员没有作为材料的合成路径");
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private static class MaterialGroup {
        String target;
        List<String> material2List;
    }

    private static class MaterialGroupAdapter extends RecyclerView.Adapter<MaterialGroupAdapter.GroupViewHolder> {
        private List<MaterialGroup> groups;
        private String currentCrewName;
        private CraftInfoActivity activity;

        MaterialGroupAdapter(List<MaterialGroup> groups, String currentCrewName, CraftInfoActivity activity) {
            this.groups = groups;
            this.currentCrewName = currentCrewName;
            this.activity = activity;
        }

        @NonNull
        @Override
        public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_material_group, parent, false);
            return new GroupViewHolder(view);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
            MaterialGroup group = groups.get(position);
            holder.targetText.setText(group.target);

            // 双击目标船员显示信息
            GestureDetector targetDetector = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    activity.showCrewInfo(group.target);
                    return true;
                }
            });
            holder.targetText.setOnTouchListener((v, event) -> {
                targetDetector.onTouchEvent(event);
                return true;
            });

            holder.subItemsContainer.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(holder.itemView.getContext());

            for (String material2 : group.material2List) {
                View subRow = inflater.inflate(R.layout.item_sub_row_material, holder.subItemsContainer, false);
                TextView material2Name = subRow.findViewById(R.id.material2_name);
                material2Name.setText(material2);

                GestureDetector materialDetector = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        activity.showCrewInfo(material2);
                        return true;
                    }

                    @Override
                    public void onLongPress(MotionEvent e) {
                        String formula = currentCrewName + "+" + material2 + "→" + group.target;
                        copyToClipboard(activity, formula);
                    }
                });
                material2Name.setOnTouchListener((v, event) -> {
                    materialDetector.onTouchEvent(event);
                    return true;
                });
                holder.subItemsContainer.addView(subRow);
            }
        }

        @Override
        public int getItemCount() {
            return groups.size();
        }

        static class GroupViewHolder extends RecyclerView.ViewHolder {
            LinearLayout subItemsContainer;
            TextView targetText;

            GroupViewHolder(View itemView) {
                super(itemView);
                subItemsContainer = itemView.findViewById(R.id.sub_items_container);
                targetText = itemView.findViewById(R.id.target_text);
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