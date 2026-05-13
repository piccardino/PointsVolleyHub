package com.volleyhub.pro;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class PlayerData {
    private String name;
    private Object num;
    private String role;

    public PlayerData() {
        // Default constructor required for Firebase
    }

    public String getName() {
        return name != null && !name.trim().isEmpty() ? name : "Giocatore";
    }

    public Object getNum() {
        return num;
    }

    public String getRole() {
        return role != null && !role.trim().isEmpty() ? role : "";
    }

    public String getDisplayNumber() {
        return num == null ? "" : String.valueOf(num);
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setNum(Object num) {
        this.num = num;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
