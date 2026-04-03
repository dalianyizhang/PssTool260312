package com.example.psstool260312.adapter;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.psstool260312.R;

import java.util.List;

public class ToolAdapter extends RecyclerView.Adapter<ToolAdapter.ToolViewHolder> {

    private List<ToolItem> tools;

    public ToolAdapter(List<ToolItem> tools) {
        this.tools = tools;
    }

    @NonNull
    @Override
    public ToolViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tool, parent, false);
        return new ToolViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ToolViewHolder holder, int position) {
        ToolItem tool = tools.get(position);
        holder.tvTitle.setText(tool.name);
        holder.tvDesc.setText(tool.description);
        holder.ivIcon.setImageResource(tool.iconRes);
        // 点击事件
        holder.cardView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), tool.activityClass);
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return tools.size();
    }

    static class ToolViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        ImageView ivIcon;
        TextView tvTitle;
        TextView tvDesc;

        ToolViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (CardView) itemView;
            ivIcon = itemView.findViewById(R.id.iv_icon);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvDesc = itemView.findViewById(R.id.tv_desc);
        }
    }

    // 工具项数据类
    public static class ToolItem {
        String name;
        String description;
        Class<?> activityClass;
        int iconRes;

        public ToolItem(String name, String description, Class<?> activityClass, int iconRes) {
            this.name = name;
            this.description = description;
            this.activityClass = activityClass;
            this.iconRes = iconRes;
        }
    }
}
