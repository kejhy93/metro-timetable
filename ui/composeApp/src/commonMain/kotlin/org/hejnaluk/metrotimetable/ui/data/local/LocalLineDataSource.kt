package org.hejnaluk.metrotimetable.ui.data.local

import androidx.compose.ui.graphics.Color

class LocalLineDataSource : LineDataSource {

    private val lines = listOf(
        MetroLine(
            id = "L991",
            name = "A",
            color = Color(0xFF00A562),
            stations = listOf(
                "Nemocnice Motol", "Petřiny", "Nádraží Veleslavín", "Bořislavka",
                "Dejvická", "Hradčanská", "Malostranská", "Staroměstská",
                "Můstek", "Muzeum", "Náměstí Míru", "Jiřího z Poděbrad",
                "Flora", "Želivského", "Strašnická", "Skalka", "Depo Hostivař"
            ),
            terminus0 = "Depo Hostivař",
            terminus1 = "Nemocnice Motol"
        ),
        MetroLine(
            id = "L992",
            name = "B",
            color = Color(0xFFFFD700),
            stations = listOf(
                "Zličín", "Stodůlky", "Luka", "Lužiny", "Hůrka",
                "Nové Butovice", "Jinonice", "Radlická", "Smíchovské nádraží",
                "Anděl", "Karlovo náměstí", "Národní třída", "Můstek",
                "Náměstí Republiky", "Florenc", "Křížíkova", "Invalidovna",
                "Kolbenova", "Hloubětín", "Rajská zahrada", "Černý Most"
            ),
            terminus0 = "Černý Most",
            terminus1 = "Zličín"
        ),
        MetroLine(
            id = "L993",
            name = "C",
            color = Color(0xFFC8102E),
            stations = listOf(
                "Háje", "Opatov", "Chodov", "Roztyly", "Kačerov",
                "Budějovická", "Pankrác", "Pražského povstání", "Vyšehrad",
                "I. P. Pavlova", "Muzeum", "Hlavní nádraží", "Florenc",
                "Vltavská", "Nádraží Holešovice", "Kobylisy", "Ládví",
                "Střížkov", "Prosek", "Letňany"
            ),
            terminus0 = "Letňany",
            terminus1 = "Háje"
        )
    )

    override fun getLines(): List<MetroLine> = lines

    override fun getStations(lineId: String): List<String> =
        lines.firstOrNull { it.id == lineId }?.stations ?: emptyList()
}
