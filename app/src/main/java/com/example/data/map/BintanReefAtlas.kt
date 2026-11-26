package com.example.data.map

import com.example.sensors.MarineGeoUtils

enum class ReefBottomType(
    val label: String,
    val shortTag: String
) {
    CORAL_REEF("Terumbu Karang Hidup", "Karang"),
    ROCKY_HEADLAND("Batu Granit & Karang Tepi", "Batu Karang"),
    DROP_OFF("Palung & Tubir Karang", "Tubir/Palung"),
    SEAGRASS_SAND("Lamun, Pasir & Muara", "Lamun/Muara")
}

enum class SpotAccessType(
    val label: String,
    val shortLabel: String
) {
    SHORE("Mancing Pinggir (Pantai/Dermaga/Batu)", "Pinggir"),
    BOAT("Perahu / Karang Lepas", "Perahu")
}

data class BintanReefZone(
    val id: String,
    val name: String,
    val centerLat: Double,
    val centerLon: Double,
    val radiusMeters: Double,
    val bottomType: ReefBottomType,
    val depthRangeLabel: String,
    val description: String
)

data class BintanHotspot(
    val id: String,
    val name: String,
    val area: String,
    val latitude: Double,
    val longitude: Double,
    val accessType: SpotAccessType,
    val bottomType: ReefBottomType,
    val depthRange: String,
    val depthMetersEstimate: Double,
    val targetFish: String,
    val bestBait: String,
    val bestTide: String,
    val castTip: String,
    val popularityNote: String,
    val isBintanCore: Boolean = true
)

/**
 * Curated geographic atlas of Pulau Bintan coral reefs, rocky drop-offs, and popular local
 * fishing hotspots (prioritizing Mancing Pinggir / shore casting + major offshore Bintan reefs).
 */
object BintanReefAtlas {

    val reefZones: List<BintanReefZone> = listOf(
        BintanReefZone(
            id = "reef_trikora_north",
            name = "Sabuk Karang Trikora 3–4",
            centerLat = 1.150,
            centerLon = 104.614,
            radiusMeters = 1900.0,
            bottomType = ReefBottomType.CORAL_REEF,
            depthRangeLabel = "1–4 m tepi • 8–14 m tubir",
            description = "Hamparan karang datar luas khas Pantai Trikora dengan tubir karang di sisi timur."
        ),
        BintanReefZone(
            id = "reef_trikora_south",
            name = "Karang Tepi Trikora 1–2",
            centerLat = 1.078,
            centerLon = 104.636,
            radiusMeters = 2100.0,
            bottomType = ReefBottomType.CORAL_REEF,
            depthRangeLabel = "2–6 m • palung 10–15 m",
            description = "Batas karang dangkal dan alur pasir tempat ikan predator masuk saat pasang naik."
        ),
        BintanReefZone(
            id = "reef_berakit_cape",
            name = "Tanjung & Karang Berakit",
            centerLat = 1.229,
            centerLon = 104.577,
            radiusMeters = 1800.0,
            bottomType = ReefBottomType.ROCKY_HEADLAND,
            depthRangeLabel = "3–9 m tepi • 14–24 m luar",
            description = "Tanjung berbatu ujung utara Bintan yang berhadapan langsung dengan arus Laut Cina Selatan."
        ),
        BintanReefZone(
            id = "reef_berakit_north_dropoff",
            name = "Tubir Karang Utara Berakit",
            centerLat = 1.262,
            centerLon = 104.588,
            radiusMeters = 2200.0,
            bottomType = ReefBottomType.DROP_OFF,
            depthRangeLabel = "14–30 m",
            description = "Patahan karang dalam jalur migrasi Tenggiri, GT, dan Talang-talang."
        ),
        BintanReefZone(
            id = "reef_beralas_pasir",
            name = "Terumbu Pulau Beralas Pasir",
            centerLat = 1.104,
            centerLon = 104.677,
            radiusMeters = 1600.0,
            bottomType = ReefBottomType.CORAL_REEF,
            depthRangeLabel = "4–18 m",
            description = "Karang keliling pulau berpasir putih dengan air jernih dan koloni Kerapu Sunu."
        ),
        BintanReefZone(
            id = "reef_mapur",
            name = "Karang Besar Pulau Mapur",
            centerLat = 1.002,
            centerLon = 104.815,
            radiusMeters = 3200.0,
            bottomType = ReefBottomType.DROP_OFF,
            depthRangeLabel = "12–35 m",
            description = "Zona terumbu karang & tubir paling terkenal di Bintan untuk GT, Kakap Merah, dan Tenggiri."
        ),
        BintanReefZone(
            id = "reef_sumpat",
            name = "Karang Pulau Sumpat",
            centerLat = 1.192,
            centerLon = 104.538,
            radiusMeters = 1400.0,
            bottomType = ReefBottomType.ROCKY_HEADLAND,
            depthRangeLabel = "5–16 m",
            description = "Celah batu granit dan karang hidup di pesisir timur laut Bintan."
        ),
        BintanReefZone(
            id = "reef_sakera_uban",
            name = "Karang Pantai Sakera & Uban",
            centerLat = 1.076,
            centerLon = 104.218,
            radiusMeters = 1700.0,
            bottomType = ReefBottomType.ROCKY_HEADLAND,
            depthRangeLabel = "2–10 m",
            description = "Batu karang tepi barat Bintan yang menghadap Selat Riau dengan arus pasang aktif."
        ),
        BintanReefZone(
            id = "reef_lagoi_pengudang",
            name = "Karang & Lamun Pengudang",
            centerLat = 1.197,
            centerLon = 104.482,
            radiusMeters = 1600.0,
            bottomType = ReefBottomType.SEAGRASS_SAND,
            depthRangeLabel = "1–6 m",
            description = "Padang lamun alami dan karang tepi tempat Baronang dan Kakap Bakau mencari makan."
        ),
        BintanReefZone(
            id = "reef_penyengat_tpi",
            name = "Karang Tepi Penyengat & TPI",
            centerLat = 0.925,
            centerLon = 104.419,
            radiusMeters = 1400.0,
            bottomType = ReefBottomType.ROCKY_HEADLAND,
            depthRangeLabel = "3–12 m",
            description = "Alur selat dangkal berkarang di sekitar Pulau Penyengat dan Tepi Laut Tanjung Pinang."
        ),
        BintanReefZone(
            id = "reef_soreh",
            name = "Karang Pulau Soreh",
            centerLat = 0.862,
            centerLon = 104.432,
            radiusMeters = 1600.0,
            bottomType = ReefBottomType.CORAL_REEF,
            depthRangeLabel = "8–18 m",
            description = "Spot karang favorit pemancing lokal selatan Tanjung Pinang dengan struktur karang rapat."
        ),
        BintanReefZone(
            id = "reef_kijang_mantang",
            name = "Palung Selat Kijang & Mantang",
            centerLat = 0.822,
            centerLon = 104.588,
            radiusMeters = 2400.0,
            bottomType = ReefBottomType.DROP_OFF,
            depthRangeLabel = "10–24 m",
            description = "Pertemuan palung selat berarus kencang dengan karang tepi Pulau Mantang."
        ),
        // === ZONA KARANG KEPRI LAINNYA (BATAM, KARIMUN, LINGGA, ANAMBAS, NATUNA) ===
        BintanReefZone(
            id = "reef_batam_abang",
            name = "Terumbu Karang Pulau Abang (Batam)",
            centerLat = 0.568,
            centerLon = 104.224,
            radiusMeters = 2800.0,
            bottomType = ReefBottomType.CORAL_REEF,
            depthRangeLabel = "4–22 m",
            description = "Taman terumbu karang paling subur di selatan Batam — surga Kerapu, Kakap & Baronang."
        ),
        BintanReefZone(
            id = "reef_batam_nongsa",
            name = "Karang Putri & Pesisir Nongsa (Batam)",
            centerLat = 1.196,
            centerLon = 104.104,
            radiusMeters = 2000.0,
            bottomType = ReefBottomType.ROCKY_HEADLAND,
            depthRangeLabel = "3–14 m",
            description = "Karang tepi utara Batam menghadap Selat Singapura dengan arus hidup."
        ),
        BintanReefZone(
            id = "reef_karimun_pelawan",
            name = "Karang Pelawan & Pongkar (Karimun)",
            centerLat = 1.095,
            centerLon = 103.362,
            radiusMeters = 2200.0,
            bottomType = ReefBottomType.ROCKY_HEADLAND,
            depthRangeLabel = "3–16 m",
            description = "Tebing batu granit dan karang pesisir utara Pulau Karimun Besar."
        ),
        BintanReefZone(
            id = "reef_lingga_benan",
            name = "Karang Pulau Benan & Senayang (Lingga)",
            centerLat = 0.455,
            centerLon = 104.455,
            radiusMeters = 2600.0,
            bottomType = ReefBottomType.CORAL_REEF,
            depthRangeLabel = "5–20 m",
            description = "Gugusan terumbu karang jernih jalur utara Kabupaten Lingga."
        ),
        BintanReefZone(
            id = "reef_anambas_tarempa",
            name = "Karang Teluk Tarempa (Anambas)",
            centerLat = 3.225,
            centerLon = 106.215,
            radiusMeters = 3000.0,
            bottomType = ReefBottomType.DROP_OFF,
            depthRangeLabel = "10–40 m",
            description = "Tubir karang dalam perairan jernih Kepulauan Anambas, habitat GT & Kerapu Sunu."
        ),
        BintanReefZone(
            id = "reef_natuna_sindu",
            name = "Batu Sindu & Karang Ranai (Natuna)",
            centerLat = 3.968,
            centerLon = 108.404,
            radiusMeters = 2800.0,
            bottomType = ReefBottomType.DROP_OFF,
            depthRangeLabel = "8–38 m",
            description = "Tanjung granit raksasa dan karang laut lepas Ranai, Natuna."
        )
    )

    val hotspots: List<BintanHotspot> = listOf(
        // === MANCING PINGGIR (SHORE / ROCKS / JETTY / BRIDGE) ===
        BintanHotspot(
            id = "spot_trikora_4_granit",
            name = "Batu Karang Trikora 4 (KM 50)",
            area = "Trikora Timur",
            latitude = 1.1635,
            longitude = 104.6038,
            accessType = SpotAccessType.SHORE,
            bottomType = ReefBottomType.ROCKY_HEADLAND,
            depthRange = "2–6 m",
            depthMetersEstimate = 4.0,
            targetFish = "Kerapu, Kakap Batu, Todak, Baby GT",
            bestBait = "Udang hidup, irisan tongkol, minnow 7–9 cm",
            bestTide = "Pasang naik (2 jam sebelum puncak)",
            castTip = "Lempar 35–60 m melewati tepi batu granit ke batas air biru gelap.",
            popularityNote = "Spot pinggir paling ramai akhir pekan — struktur batu granit alami."
        ),
        BintanHotspot(
            id = "spot_trikora_3_tubir",
            name = "Pantai Trikora 3 (Tubir Karang)",
            area = "Trikora Timur",
            latitude = 1.1355,
            longitude = 104.6162,
            accessType = SpotAccessType.SHORE,
            bottomType = ReefBottomType.CORAL_REEF,
            depthRange = "1.5–5 m",
            depthMetersEstimate = 3.5,
            targetFish = "Baronang, Kerapu Karang, Kakap, Selar",
            bestBait = "Udang kupas, lumut/roti (Baronang), micro jig 10–15g",
            bestTide = "Pasang naik pagi (06:00–10:00) & sore",
            castTip = "Gunakan pelampung atau timah ringan agar tidak nyangkut di terumbu dangkal.",
            popularityNote = "Terumbu karang terlihat jelas di mode Satelit Karang saat air jernih."
        ),
        BintanHotspot(
            id = "spot_trikora_2_casting",
            name = "Pantai Trikora 2 (Karang KM 38)",
            area = "Trikora Tengah",
            latitude = 1.0882,
            longitude = 104.6295,
            accessType = SpotAccessType.SHORE,
            bottomType = ReefBottomType.CORAL_REEF,
            depthRange = "2–7 m",
            depthMetersEstimate = 4.5,
            targetFish = "Baby GT, Barracuda, Kerapu, Kuwe",
            bestBait = "Minnow floating, popper kecil 12g, udang hidup",
            bestTide = "Peralihan surut ke pasang naik",
            castTip = "Incar celah pasir di antara dua hamparan karang gelap sejauh 40–70 m.",
            popularityNote = "Langganan pemancing UL & casting pinggir Pulau Bintan."
        ),
        BintanHotspot(
            id = "spot_berakit_karang",
            name = "Tanjung Berakit (Batu & Dermaga)",
            area = "Bintan Utara",
            latitude = 1.2242,
            longitude = 104.5738,
            accessType = SpotAccessType.SHORE,
            bottomType = ReefBottomType.ROCKY_HEADLAND,
            depthRange = "3–9 m",
            depthMetersEstimate = 6.0,
            targetFish = "Talang-talang, Kerapu, Kakap Merah, GT",
            bestBait = "Cumi segar, ikan kembung potong, metal jig 20–30g",
            bestTide = "Pasang naik arus sedang (hindari ombak Utara ekstrem)",
            castTip = "Cast menyamping mengikuti arus ujung tanjung, pakai leader fluorocarbon 25–30 lb.",
            popularityNote = "Spot pinggir berarus kuat — sering strike ikan predator ukuran besar."
        ),
        BintanHotspot(
            id = "spot_teluk_bakau",
            name = "Pantai Teluk Bakau (Karang Tepi)",
            area = "Gunung Kijang",
            latitude = 1.1238,
            longitude = 104.6185,
            accessType = SpotAccessType.SHORE,
            bottomType = ReefBottomType.SEAGRASS_SAND,
            depthRange = "1.5–4.5 m",
            depthMetersEstimate = 3.0,
            targetFish = "Baronang, Ketang-ketang, Kakap, Kerapu",
            bestBait = "Udang hidup, cacing laut, umpan dasar ringan",
            bestTide = "Pasang penuh hingga awal surut",
            castTip = "Targetkan tepi padang lamun 25–45 m dari bibir pantai.",
            popularityNote = "Aman & nyaman untuk mancing santai pinggir pantai."
        ),
        BintanHotspot(
            id = "spot_malang_rapat",
            name = "Batu Granit Malang Rapat",
            area = "Gunung Kijang",
            latitude = 1.1488,
            longitude = 104.6075,
            accessType = SpotAccessType.SHORE,
            bottomType = ReefBottomType.ROCKY_HEADLAND,
            depthRange = "2–6 m",
            depthMetersEstimate = 4.0,
            targetFish = "Kerapu Lumpur, Kakap, Sembilang, Kurau",
            bestBait = "Udang hidup, sotong potong, umpan dasar timah J3–J4",
            bestTide = "Pasang naik sore hingga malam",
            castTip = "Berdiri aman di batu landai, lempar tepat di ujung bayangan batu karang.",
            popularityNote = "Banyak celah karang rumah Kerapu dekat tepi."
        ),
        BintanHotspot(
            id = "spot_jembatan_dompak",
            name = "Jembatan 1 Dompak (Tiang & Tepi)",
            area = "Tanjung Pinang",
            latitude = 0.8924,
            longitude = 104.4622,
            accessType = SpotAccessType.SHORE,
            bottomType = ReefBottomType.DROP_OFF,
            depthRange = "4–10 m",
            depthMetersEstimate = 7.0,
            targetFish = "Kakap Putih (Barramundi), Kerapu, Senangin, Kurau",
            bestBait = "Udang putih hidup (pelampung/dasar), soft lure shad 3 inci",
            bestTide = "Pasang naik malam & subuh (arus pelan–sedang)",
            castTip = "Arahkan umpan hanyut menyusuri bayangan tiang jembatan & batas arus balik.",
            popularityNote = "Spot gacor paling terkenal di Tanjung Pinang untuk Kakap Putih!"
        ),
        BintanHotspot(
            id = "spot_tepi_laut_tpi",
            name = "Tepi Laut & Pelantar Tanjung Pinang",
            area = "Tanjung Pinang",
            latitude = 0.9221,
            longitude = 104.4408,
            accessType = SpotAccessType.SHORE,
            bottomType = ReefBottomType.ROCKY_HEADLAND,
            depthRange = "3–8 m",
            depthMetersEstimate = 5.5,
            targetFish = "Kakap Putih, Kerapu, Sembilang, Gelama",
            bestBait = "Udang hidup, udang kupas, cacing laut",
            bestTide = "Sore menjelang malam saat air naik",
            castTip = "Mainkan dasaran dekat turap batu atau ujung dermaga pelantar.",
            popularityNote = "Akses super mudah, ramai pemancing lokal tiap sore & malam."
        ),
        BintanHotspot(
            id = "spot_sakera_uban",
            name = "Pantai Sakera & Karang Tanjung Uban",
            area = "Tanjung Uban",
            latitude = 1.0762,
            longitude = 104.2218,
            accessType = SpotAccessType.SHORE,
            bottomType = ReefBottomType.ROCKY_HEADLAND,
            depthRange = "2–7 m",
            depthMetersEstimate = 4.5,
            targetFish = "Kerapu, Baronang, Todak, Kakap, Kuwe",
            bestBait = "Udang hidup, irisan tamban, micro jig 12g",
            bestTide = "Pasang naik pagi & sore",
            castTip = "Lempar 30–60 m ke arah barat melewati karang dangkal menuju alur Selat Riau.",
            popularityNote = "Spot andalan warga Bintan Utara & Tanjung Uban."
        ),
        BintanHotspot(
            id = "spot_pengudang_lagoi",
            name = "Pesisir & Karang Pengudang (Lagoi)",
            area = "Lagoi / Teluk Sebong",
            latitude = 1.1982,
            longitude = 104.4848,
            accessType = SpotAccessType.SHORE,
            bottomType = ReefBottomType.SEAGRASS_SAND,
            depthRange = "1.5–5 m",
            depthMetersEstimate = 3.0,
            targetFish = "Kakap Bakau (MJ), Kerapu, Baronang, Baby GT",
            bestBait = "Udang hidup, minnow kecil, soft plastic udang",
            bestTide = "Pasang naik penuh (air jernih)",
            castTip = "Susuri pinggiran mangrove dan karang tepi dengan piranti UL/light.",
            popularityNote = "Tenang, terlindung angin, cocok untuk UL & mancing keluarga."
        ),

        // === PERAHU / KARANG LEPAS (BOAT & OFFSHORE REEF) ===
        BintanHotspot(
            id = "spot_beralas_pasir",
            name = "Karang Pulau Beralas Pasir",
            area = "Lepas Trikora",
            latitude = 1.1032,
            longitude = 104.6772,
            accessType = SpotAccessType.BOAT,
            bottomType = ReefBottomType.CORAL_REEF,
            depthRange = "6–18 m",
            depthMetersEstimate = 12.0,
            targetFish = "Kerapu Sunu, Kakap Merah, GT, Kurisi",
            bestBait = "Cumi hidup, udang besar, slow jig 40–60g",
            bestTide = "Arus sedang saat pasang naik",
            castTip = "Drift di sisi tubir timur pulau (kedalaman 10–16 m) dekat dinding karang.",
            popularityNote = "15 menit perahu dari Trikora — terumbu karang sangat subur."
        ),
        BintanHotspot(
            id = "spot_pulau_mapur",
            name = "Karang Besar Pulau Mapur",
            area = "Timur Bintan (Lepas)",
            latitude = 1.0025,
            longitude = 104.8148,
            accessType = SpotAccessType.BOAT,
            bottomType = ReefBottomType.DROP_OFF,
            depthRange = "15–35 m",
            depthMetersEstimate = 24.0,
            targetFish = "GT, Tenggiri, Kakap Merah, Kerapu Naga, Ruby",
            bestBait = "Tenggiri koncer selar hidup, popper 60–90g, jig 80–120g",
            bestTide = "Arus hidup pasang naik pagi hari",
            castTip = "Popping di bibir karang dangkal atau jigging di tubir 20–32 m.",
            popularityNote = "Spot monster legendaris perairan Bintan!"
        ),
        BintanHotspot(
            id = "spot_utara_berakit",
            name = "Karang Utara Berakit (Tubir)",
            area = "Utara Bintan (Lepas)",
            latitude = 1.2585,
            longitude = 104.5852,
            accessType = SpotAccessType.BOAT,
            bottomType = ReefBottomType.DROP_OFF,
            depthRange = "14–28 m",
            depthMetersEstimate = 20.0,
            targetFish = "Tenggiri, Talang-talang, GT, Kakap Merah",
            bestBait = "Kembung/selar hidup (koncer), metal jig 60–100g",
            bestTide = "Angin selatan/barat daya (musim teduh)",
            castTip = "Pantau sambaran burung laut & arus pecah di atas tandes karang.",
            popularityNote = "Jalur pelagis kelas berat di utara Tanjung Berakit."
        ),
        BintanHotspot(
            id = "spot_pulau_sumpat",
            name = "Karang Pulau Sumpat",
            area = "Timur Laut Bintan",
            latitude = 1.1924,
            longitude = 104.5382,
            accessType = SpotAccessType.BOAT,
            bottomType = ReefBottomType.CORAL_REEF,
            depthRange = "6–16 m",
            depthMetersEstimate = 11.0,
            targetFish = "Kerapu, Jenaha, Kakap Batu, Kurisi",
            bestBait = "Cumi segar, udang kupas, dasaran rangkaian 2 kail",
            bestTide = "Pasang naik hingga puncak pasang",
            castTip = "Jatuhkan umpan 1–2 meter di atas karang agar tidak tersangkut.",
            popularityNote = "Spot dasaran favorit perahu pompong dari Berakit & Pengudang."
        ),
        BintanHotspot(
            id = "spot_pulau_soreh",
            name = "Karang Pulau Soreh",
            area = "Selatan Tanjung Pinang",
            latitude = 0.8622,
            longitude = 104.4325,
            accessType = SpotAccessType.BOAT,
            bottomType = ReefBottomType.CORAL_REEF,
            depthRange = "8–18 m",
            depthMetersEstimate = 13.0,
            targetFish = "Jenaha, Kerapu, Kakap Merah, Alu-alu",
            bestBait = "Udang hidup, sotong potong, sabiki ikan umpan",
            bestTide = "Pagi & sore saat arus tidak terlalu kencang",
            castTip = "Cari kontur karang di barat daya pulau dengan timah J6–J8.",
            popularityNote = "Dekat dari Tanjung Pinang, sangat produktif untuk dasaran."
        ),
        BintanHotspot(
            id = "spot_selat_kijang_mantang",
            name = "Selat Kijang & Karang Mantang",
            area = "Bintan Timur / Selatan",
            latitude = 0.8355,
            longitude = 104.5965,
            accessType = SpotAccessType.BOAT,
            bottomType = ReefBottomType.DROP_OFF,
            depthRange = "10–24 m",
            depthMetersEstimate = 16.0,
            targetFish = "Kakap Merah, Kerapu, Kurau, Pari, Senangin",
            bestBait = "Udang hidup besar, irisan ikan tamban, cumi utuh",
            bestTide = "Saat arus mulai melambat (peralihan pasang/surut)",
            castTip = "Gunakan timah berat J8–J12 saat arus selat kuat agar umpan menyentuh dasar.",
            popularityNote = "Palung dalam & pertemuan arus favorit pemancing Kijang."
        ),

        // === SPOT KEPRI LAINNYA (BATAM, KARIMUN, LINGGA, ANAMBAS, NATUNA) ===
        BintanHotspot(
            id = "spot_batam_barelang_4",
            name = "Jembatan 4–5 Barelang (Arus Selat)",
            area = "Batam",
            latitude = 0.8715,
            longitude = 104.1422,
            accessType = SpotAccessType.SHORE,
            bottomType = ReefBottomType.DROP_OFF,
            depthRange = "5–16 m",
            depthMetersEstimate = 10.0,
            targetFish = "Kakap Putih, Kerapu, Kurau, Talang-talang",
            bestBait = "Udang putih hidup, belanak hidup, soft lure shad",
            bestTide = "Arus pasang naik sedang / awal surut",
            castTip = "Incar pusaran air balik di sisi batu penahan jembatan atau bawah selat.",
            popularityNote = "Spot mancing paling legendaris di Batam & mudah dijangkau darat.",
            isBintanCore = false
        ),
        BintanHotspot(
            id = "spot_batam_nongsa",
            name = "Pantai Nongsa & Karang Putri",
            area = "Batam",
            latitude = 1.1965,
            longitude = 104.1045,
            accessType = SpotAccessType.SHORE,
            bottomType = ReefBottomType.ROCKY_HEADLAND,
            depthRange = "2–7 m",
            depthMetersEstimate = 4.5,
            targetFish = "Kerapu Karang, Baronang, Baby GT, Todak",
            bestBait = "Udang hidup, cumi iris, micro jig 10–15g",
            bestTide = "Pasang naik pagi & sore",
            castTip = "Lempar ke tepi karang sebelah utara pantai sebelum alur kapal.",
            popularityNote = "Spot pinggir populer di timur laut Batam menghadap Singapura.",
            isBintanCore = false
        ),
        BintanHotspot(
            id = "spot_batam_pulau_abang",
            name = "Terumbu Karang Pulau Abang",
            area = "Selatan Batam",
            latitude = 0.5682,
            longitude = 104.2245,
            accessType = SpotAccessType.BOAT,
            bottomType = ReefBottomType.CORAL_REEF,
            depthRange = "6–22 m",
            depthMetersEstimate = 13.0,
            targetFish = "Kerapu Sunu, Kakap Merah, GT, Jenaha",
            bestBait = "Cumi hidup, udang besar, slow jig 40–60g",
            bestTide = "Pasang naik hingga puncak pasang",
            castTip = "Mainkan jig ringan tepat 2 meter di atas hamparan karang hidup.",
            popularityNote = "Pusat wisata & karang paling subur di gugusan Barelang Batam.",
            isBintanCore = false
        ),
        BintanHotspot(
            id = "spot_karimun_pelawan",
            name = "Pantai Pelawan & Batu Pongkar",
            area = "Tanjung Balai Karimun",
            latitude = 1.0952,
            longitude = 103.3625,
            accessType = SpotAccessType.SHORE,
            bottomType = ReefBottomType.ROCKY_HEADLAND,
            depthRange = "2–8 m",
            depthMetersEstimate = 5.0,
            targetFish = "Kerapu, Kakap Batu, Sembilang, Talang-talang",
            bestBait = "Udang hidup, sotong potong, minnow 8 cm",
            bestTide = "Pasang naik sore hari",
            castTip = "Lempar menyusuri tanjung batu granit menuju batas arus Selat Malaka.",
            popularityNote = "Spot mancing pinggir favorit warga Tanjung Balai Karimun.",
            isBintanCore = false
        ),
        BintanHotspot(
            id = "spot_lingga_benan",
            name = "Karang Pulau Benan & Senayang",
            area = "Lingga Utara",
            latitude = 0.4555,
            longitude = 104.4552,
            accessType = SpotAccessType.BOAT,
            bottomType = ReefBottomType.CORAL_REEF,
            depthRange = "5–20 m",
            depthMetersEstimate = 12.0,
            targetFish = "Kerapu, Kakap Merah, Tenggiri, Kuwe",
            bestBait = "Selar hidup, cumi segar, metal jig 40–80g",
            bestTide = "Arus hidup pasang naik pagi",
            castTip = "Susuri sisi tubir timur Pulau Benan dengan teknik dasaran atau jigging.",
            popularityNote = "Air super jernih & terumbu karang luas di jalur Bintan–Lingga.",
            isBintanCore = false
        ),
        BintanHotspot(
            id = "spot_lingga_daik",
            name = "Muara & Pelabuhan Daik Lingga",
            area = "Pulau Lingga",
            latitude = -0.2185,
            longitude = 104.6420,
            accessType = SpotAccessType.SHORE,
            bottomType = ReefBottomType.SEAGRASS_SAND,
            depthRange = "3–9 m",
            depthMetersEstimate = 5.5,
            targetFish = "Kakap Putih, Senangin, Kurau, Sembilang",
            bestBait = "Udang putih hidup, tamban potong",
            bestTide = "Pasang naik malam & subuh",
            castTip = "Gunakan teknik pelampung hanyut di ujung dermaga saat air mulai masuk.",
            popularityNote = "Spot pinggir dermaga paling ramai di Kabupaten Lingga.",
            isBintanCore = false
        ),
        BintanHotspot(
            id = "spot_anambas_tarempa",
            name = "Karang Batu Tompak & Tarempa",
            area = "Kepulauan Anambas",
            latitude = 3.2255,
            longitude = 106.2155,
            accessType = SpotAccessType.BOAT,
            bottomType = ReefBottomType.DROP_OFF,
            depthRange = "10–40 m",
            depthMetersEstimate = 22.0,
            targetFish = "GT, Ruby Snapper, Kerapu Sunu, Tuna Gigi Anjing",
            bestBait = "Popper 80g, jig 100–150g, tongkol iris",
            bestTide = "Arus sedang pasang naik",
            castTip = "Jigging di bibir tubir laut dalam sekitar luar Teluk Tarempa.",
            popularityNote = "Spot kelas berat di Kepulauan Anambas.",
            isBintanCore = false
        ),
        BintanHotspot(
            id = "spot_natuna_batu_sindu",
            name = "Tanjung Batu Sindu (Ranai)",
            area = "Natuna",
            latitude = 3.9685,
            longitude = 108.4045,
            accessType = SpotAccessType.SHORE,
            bottomType = ReefBottomType.ROCKY_HEADLAND,
            depthRange = "4–14 m",
            depthMetersEstimate = 8.0,
            targetFish = "GT, Tenggiri, Talang-talang, Kerapu Karang",
            bestBait = "Metal jig 30–60g, stickbait, cumi segar",
            bestTide = "Musim teduh saat pasang naik pagi",
            castTip = "Rock fishing dari batu granit datar yang aman dengan leader 40 lb.",
            popularityNote = "Ikon spot rock fishing & karang terkenal di Ranai Natuna.",
            isBintanCore = false
        )
    )

    fun sortedByDistance(fromLat: Double, fromLon: Double): List<Pair<BintanHotspot, Double>> {
        return hotspots
            .map { spot ->
                spot to MarineGeoUtils.distanceMeters(fromLat, fromLon, spot.latitude, spot.longitude)
            }
            .sortedBy { it.second }
    }
}
