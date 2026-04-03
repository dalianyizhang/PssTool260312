package com.example.psstool260312.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.psstool260312.R;

public class StepIndicator extends LinearLayout {
    private int stepCount = 3;
    private String[] stepTitles = {"配置参数", "检查加点", "选择方案"};
    private int currentStep = 0;
    private Paint linePaint;
    private int lineColor;
    private int activeColor;
    private int inactiveColor;
    private int textSize;

    public StepIndicator(Context context) {
        this(context, null);
    }

    public StepIndicator(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setOrientation(HORIZONTAL);
        setWillNotDraw(false);

        // 读取自定义属性
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.StepIndicator);
        stepCount = ta.getInt(R.styleable.StepIndicator_stepCount, 4);
        activeColor = ta.getColor(R.styleable.StepIndicator_activeColor, 0xFF007bff);
        inactiveColor = ta.getColor(R.styleable.StepIndicator_inactiveColor, 0xFFdddddd);
        textSize = ta.getDimensionPixelSize(R.styleable.StepIndicator_textSize, 14);
        ta.recycle();

        // 初始化画笔
        linePaint = new Paint();
        linePaint.setStrokeWidth(2);
        linePaint.setAntiAlias(true);

        // 动态添加步骤视图
        for (int i = 0; i < stepCount; i++) {
            addStepView(i);
        }
    }

    private void addStepView(int index) {
        View stepView = inflate(getContext(), R.layout.item_step_indicator, null);
        TextView tvNumber = stepView.findViewById(R.id.tv_step_number);
        TextView tvTitle = stepView.findViewById(R.id.tv_step_title);
        View line = stepView.findViewById(R.id.step_line);

        tvNumber.setText(String.valueOf(index + 1));
        tvTitle.setText(stepTitles[index]);
        tvTitle.setTextSize(textSize);

        // 设置线条显示逻辑（最后一个不显示线条）
        if (index == stepCount - 1) {
            line.setVisibility(View.GONE);
        }

        addView(stepView);
        updateStepStyle(index);
    }

    public void setCurrentStep(int step) {
        if (step < 0 || step >= stepCount) return;
        int oldStep = currentStep;
        currentStep = step;
        updateStepStyle(oldStep);
        updateStepStyle(currentStep);
        invalidate(); // 重绘线条
    }

    private void updateStepStyle(int index) {
        View stepView = getChildAt(index);
        if (stepView == null) return;
        TextView tvNumber = stepView.findViewById(R.id.tv_step_number);
        TextView tvTitle = stepView.findViewById(R.id.tv_step_title);
        View line = stepView.findViewById(R.id.step_line);

        boolean isActive = index <= currentStep;
        int bgColor = isActive ? activeColor : inactiveColor;
        int textColor = isActive ? 0xFFFFFFFF : 0xFF999999;
        tvNumber.setBackgroundColor(bgColor);
        tvNumber.setTextColor(textColor);
        tvTitle.setTextColor(isActive ? activeColor : 0xFF666666);

        // 设置线条颜色
        if (line != null && index < stepCount - 1) {
            line.setBackgroundColor(isActive ? activeColor : inactiveColor);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 如果需要绘制连接线（可选）
    }
}
