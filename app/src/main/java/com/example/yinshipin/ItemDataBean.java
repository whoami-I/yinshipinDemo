package com.example.yinshipin;

class ItemDataBean {

    public String mItemName;
    public Class mClazz;
    public int mColor;


    public ItemDataBean(String itemName, Class clazz, int color) {
        this.mItemName = itemName;
        this.mClazz = clazz;
        this.mColor = color;
    }

    public String getItemName() {
        return mItemName;
    }

    public int getColor() {
        return mColor;
    }

    public void setColor(int color) {
        this.mColor = color;
    }

    public void setmItemName(String itemName) {
        this.mItemName = itemName;
    }

    public Class getClazz() {
        return mClazz;
    }


    public void setClazz(Class clazz) {
        this.mClazz = clazz;
    }
}
