package com.vn.jet.mosco.model;

public class Objet {
    private int id;
    private String imageUrl;

    public Objet(int id, String imageUrl) {
        this.id = id;
        this.imageUrl = imageUrl;
    }

    public int getId() {
        return id;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}
