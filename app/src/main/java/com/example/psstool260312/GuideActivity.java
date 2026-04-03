package com.example.psstool260312;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.psstool260312.adapter.ToolAdapter;

import java.util.ArrayList;
import java.util.List;

public class GuideActivity extends AppCompatActivity {

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
    }
}