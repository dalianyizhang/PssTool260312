package com.example.psstool260312.adapter;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.psstool260312.fragment.CrewInfoFragment;
import com.example.psstool260312.fragment.MaterialFragment;
import com.example.psstool260312.fragment.ProductFragment;

public class CrewPagerAdapter extends FragmentStateAdapter {
    private int crewId = -1;

    public CrewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setCrewId(int crewId) {
        this.crewId = crewId;
        notifyDataSetChanged();
    }

    public int getCrewId() {
        return crewId;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return MaterialFragment.newInstance(crewId);
            case 1:
                return ProductFragment.newInstance(crewId);
            case 2:
                return CrewInfoFragment.newInstance(crewId);
            default:
                throw new IllegalArgumentException("Invalid position: " + position);
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }

    @Override
    public long getItemId(int position) {
        return (crewId * 10L) + position;
    }

    @Override
    public boolean containsItem(long itemId) {
        int position = (int) (itemId % 10);
        int id = (int) (itemId / 10);
        return id == crewId && position >= 0 && position < getItemCount();
    }
}