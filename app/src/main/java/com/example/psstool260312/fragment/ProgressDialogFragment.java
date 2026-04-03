package com.example.psstool260312.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.example.psstool260312.R;

public class ProgressDialogFragment extends DialogFragment {
    private TextView messageView;
    private ProgressBar progressBar;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setTitle("数据导入");
        builder.setView(R.layout.dialog_import_progress);
        builder.setCancelable(false);

        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            messageView = dialog.findViewById(R.id.message);
            progressBar = dialog.findViewById(R.id.progressBar);
        }
    }

    public void updateProgress(String message, int progress) {
        if (messageView != null) {
            messageView.setText(message);
        }
        if (progressBar != null) {
            progressBar.setProgress(progress);
        }
    }
}