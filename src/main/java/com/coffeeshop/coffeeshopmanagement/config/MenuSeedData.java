package com.coffeeshop.coffeeshopmanagement.config;

import java.util.List;

/**
 * The starter menu bundled with the app (from the "menu.zip" set of category photos), used only
 * by {@link MenuSeeder} to populate a brand-new database once. Not meant to be edited by hand
 * after that - once seeded, the real menu lives in the database and is managed the normal way
 * through Category/Product Management. Prices here are placeholder round numbers picked by
 * category (see MenuSeeder's javadoc) - the shop should review and adjust them.
 */
final class MenuSeedData {

    record MenuItem(String category, String name, String imageResource, int price) {
    }

    static final List<MenuItem> ITEMS = List.of(
            new MenuItem("Bánh ngọt", "Berry Cloud — Cheesecake dâu", "berry-cloud-cheesecake-dau.jfif", 48000),
            new MenuItem("Bánh ngọt", "Brownie", "brownie.jfif", 35000),
            new MenuItem("Bánh ngọt", "Choco Croissant", "choco-croissant.jfif", 38000),
            new MenuItem("Bánh ngọt", "Chocolate Dream — Chocolate mousse", "chocolate-dream-chocolate-mousse.jfif", 48000),
            new MenuItem("Bánh ngọt", "Croissant", "croissant.jfif", 32000),
            new MenuItem("Bánh ngọt", "Golden Honey Cake — Bánh mật ong & hạnh nhân", "golden-honey-cake-banh-mat-ong-hanh-nhan.jfif", 45000),
            new MenuItem("Bánh ngọt", "Lunavera Tiramisu — Tiramisu cà phê", "lunavera-tiramisu-tiramisu-ca-phe.jfif", 52000),
            new MenuItem("Bánh ngọt", "Matcha Bliss — Matcha cheesecake", "matcha-bliss-matcha-cheesecake.jfif", 48000),
            new MenuItem("Bánh ngọt", "Matcha Croissant", "matcha-croissant.jfif", 38000),
            new MenuItem("Bánh ngọt", "Red Velvet Cake", "red-velvet-cake.jfif", 45000),
            new MenuItem("Cà phê", "Bạc sỉu", "bac-siu.png", 32000),
            new MenuItem("Cà phê", "Cafe dừa", "cafe-dua.png", 39000),
            new MenuItem("Cà phê", "Cafe kem phô mai", "cafe-kem-pho-mai.png", 45000),
            new MenuItem("Cà phê", "Cafe nâu", "cafe-nau.png", 29000),
            new MenuItem("Cà phê", "Cafe sữa", "cafe-sua.png", 29000),
            new MenuItem("Cà phê", "Cafe đen", "cafe-den.png", 25000),
            new MenuItem("Nước ép & Sinh tố", "Lunavera Fresh - Táo & Dứa & Chanh", "lunavera-fresh-tao-dua-chanh.jfif", 45000),
            new MenuItem("Nước ép & Sinh tố", "Nước ép cam", "nuoc-ep-cam.jfif", 35000),
            new MenuItem("Nước ép & Sinh tố", "Nước ép chanh dây", "nuoc-ep-chanh-day.jfif", 38000),
            new MenuItem("Nước ép & Sinh tố", "Nước ép dứa", "nuoc-ep-dua.png", 35000),
            new MenuItem("Nước ép & Sinh tố", "Nước ép táo", "nuoc-ep-tao.jfif", 35000),
            new MenuItem("Nước ép & Sinh tố", "Ruby Fresh - Dưa hấu & Dâu", "ruby-fresh-dua-hau-dau.jfif", 45000),
            new MenuItem("Nước ép & Sinh tố", "Sunrise Orange - Cam & Dứa", "sunrise-orange-cam-dua.jfif", 42000),
            new MenuItem("Nước ép & Sinh tố", "Tropical Glow - Dứa & Chanh dây", "tropical-glow-dua-chanh-day.jfif", 42000),
            new MenuItem("Trà", "Berry Sunset — Trà dâu hibiscus", "berry-sunset-tra-dau-hibiscus.jfif", 39000),
            new MenuItem("Trà", "Lunavera Tea - Trà nhài cam vàng", "lunavera-tea-tra-nhai-cam-vang.jfif", 39000),
            new MenuItem("Trà", "Lychee Garden — Trà vải hoa hồng", "lychee-garden-tra-vai-hoa-hong.jfif", 39000),
            new MenuItem("Trà", "Peach Blossom — Trà đào cam sả", "peach-blossom-tra-dao-cam-sa.jfif", 39000),
            new MenuItem("Trà", "Tropical Breeze — Trà dứa chanh dây", "tropical-breeze-tra-dua-chanh-day.jfif", 39000),
            new MenuItem("Trà", "Trà chanh", "tra-chanh.jfif", 25000),
            new MenuItem("Trà", "Trà dâu", "tra-dau.jfif", 32000),
            new MenuItem("Trà", "Trà vải", "tra-vai.jfif", 32000),
            new MenuItem("Trà", "Trà đào", "tra-dao.jfif", 32000)
    );

    private MenuSeedData() {
    }
}
