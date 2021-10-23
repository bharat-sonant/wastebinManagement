package com.wevois.wastebinmonitor.ModelClasses;

public class ModelWastebinTypes {
    String type;
    boolean visibility;

    public void setVisibility(boolean visibility) {
        this.visibility = visibility;
    }

    public String getType() {
        return type;
    }

    public boolean isVisibility() {
        return visibility;
    }

    public ModelWastebinTypes(String type, boolean visibility) {
        this.type = type;
        this.visibility = visibility;
    }
}
