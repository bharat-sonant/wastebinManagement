package com.wevois.wastebinmonitor.ModelClasses;

import java.io.File;

public class ModelForImages {
    String date, time;
    Integer category;
    File imageBitmap;

    public Integer getCategory() {
        return category;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }

    public File getImageBitmap() {
        return imageBitmap;
    }

    public ModelForImages(String date, String time, int category, File imageBitmap) {
        this.date = date;
        this.time = time;
        this.category = category;
        this.imageBitmap = imageBitmap;
    }
}
