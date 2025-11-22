package ru.clouddonate.shop;

public final class ShopRecord {
    private final String shopKey;
    private final int shopId, serverId;

    public ShopRecord(String shopKey, int shopId, int serverId) {
        this.shopKey = shopKey;
        this.shopId = shopId;
        this.serverId = serverId;
    }

    public int getServerId() {
        return serverId;
    }

    public int getShopId() {
        return shopId;
    }

    public String getShopKey() {
        return shopKey;
    }
}
