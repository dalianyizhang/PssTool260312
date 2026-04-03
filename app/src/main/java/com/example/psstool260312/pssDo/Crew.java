package com.example.psstool260312.pssDo;

public class Crew {
    private int id;
    private String nameZh;
    private String rarity;

    public Crew(int id, String nameZh, String rarity) {
        this.id = id;
        this.nameZh = nameZh;
        this.rarity = rarity;
    }

    public int getId() { return id; }
    public String getNameZh() {
        return nameZh != null ? nameZh : "未知船员";
    }

    public String getRarity() {
        return rarity != null ? rarity : "未知等级";
    }

    @Override
    public String toString() {
        return nameZh != null ? nameZh : String.valueOf(id);
    }
}