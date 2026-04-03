package com.example.psstool260312.fragment;

/**
 * author：leeannm
 * time：2026/3/20 22:07
 */

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.psstool260312.MainActivity;
import com.example.psstool260312.viewmodel.PlanViewModel;

public abstract class BaseStepFragment extends Fragment {
    protected PlanViewModel viewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 获取共享 ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(PlanViewModel.class);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 观察数据变化，子类可以重写
        observeData();
    }

    protected void observeData() {
        // 子类可覆盖
    }

    /**
     * 当用户点击“下一步”时，由 Activity 调用，检查本页数据是否有效
     * @return true 表示数据有效，可以继续
     */
    public abstract boolean validateAndSave();

    /**
     * 当用户点击“上一步”时，由 Activity 调用，恢复页面状态（如果需要）
     */
    public void onPrevStep() {
        // 可留空
    }
}