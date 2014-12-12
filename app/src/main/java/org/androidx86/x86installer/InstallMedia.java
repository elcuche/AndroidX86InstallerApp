package org.androidx86.x86installer;

public class InstallMedia {
    private String title;
    private String description;
    private int image;
    private boolean selected;

    InstallMedia(String title, String description, int image){
        this.title = title;
        this.description = description;
        this.image = image;
        this.selected = false;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getImage() {
        return image;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
