package uz.topdim.media.service;

public enum ImageVariant {
    THUMB(200), CARD(600), FULL(1600);

    private final int maxPx;

    ImageVariant(int maxPx) {
        this.maxPx = maxPx;
    }

    public int maxPx() {
        return maxPx;
    }

    public String suffix() {
        return name().toLowerCase();
    }
}
