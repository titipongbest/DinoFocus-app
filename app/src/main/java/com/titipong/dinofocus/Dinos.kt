package com.titipong.dinofocus

import androidx.compose.ui.graphics.Color

enum class Rarity(val th: String) { COMMON("ทั่วไป"), RARE("หายาก"), EPIC("มหากาพย์"), LEGENDARY("ในตำนาน") }

/** Pixel sprite letters: a=body b=shade c=belly h=accent e=eye w=white. */
data class Species(val id: String, val shape: String, val name: String, val rarity: Rarity, val pal: Map<Char, Long>)

class Sprite(val w: Int, val h: Int, val px: List<Triple<Int, Int, Color>>)

object Dinos {
    val OUTLINE = Color(0xFF1B1B2A)

    val SHAPES: Map<String, List<String>> = mapOf(
        "rex" to listOf(
            "..........aaaaa.",
            ".........aaaaaaa",
            ".........aaweaaa",
            ".........aaaaaaa",
            ".........aaaawww",
            ".........aaaaa..",
            "..a.....aaaaa...",
            "..aa...aaaaaaa..",
            "..aaa.aaaaaccab.",
            "...aaaaaaaacc...",
            "....aaaaaaacc...",
            ".....aaaaaac....",
            "......ab.ab.....",
            "......ab.ab.....",
            ".....bbb.bbb....",
        ),
        "neck" to listOf(
            "............aaa.",
            "...........aawea",
            "...........aaaa.",
            "...........aa...",
            "..........aa....",
            "..........aa....",
            ".........aa.....",
            "...aaaaaaaa.....",
            ".aaaaaaaaaaa....",
            "aa.acccccccaa...",
            "a..accccccaab...",
            "...aab..aab.....",
            "...aab..aab.....",
            "...aab..aab.....",
            "..bbbb.bbbb.....",
        ),
        "trike" to listOf(
            "...........w....",
            "..........hhw...",
            ".........hhhaa.w",
            "...aaaaaahhaeaww",
            "..aaaaaaahhaaaa.",
            ".aaaaaaaaahaaaa.",
            "aaacccccccaaaa..",
            "a..cccccccaa....",
            "...aab...aab....",
            "...aab...aab....",
            "..bbbb..bbbb....",
        ),
        "stego" to listOf(
            "......h..h......",
            ".....hh.hh.h....",
            "....hhhhhhhhh...",
            "...aaaaaaaaaaa..",
            "..aaaaaaaaaaaaa.",
            ".aaaaaaaaaaaaaea",
            "h.acccccccccaaaa",
            "haa.ccccccc.aa..",
            "....aab...aab...",
            "....aab...aab...",
            "...bbbb..bbbb...",
        ),
        "ptero" to listOf(
            ".......hb.......",
            "......aaaaww....",
            "......aeaa......",
            "a.....aaa......a",
            "aa...aaaaa....aa",
            ".aaaaaacaaaaaaa.",
            "..aaaacccaaaaa..",
            "...aa.ccc..aa...",
            "......a.a.......",
            "......b.b.......",
        ),
        "raptor" to listOf(
            "............aaa.",
            "...........aaeaa",
            "...........aaaww",
            "..........aaa...",
            "h........aaaa...",
            "hhaaaaaaaaaaa...",
            "..aaaaaaaacca.b.",
            "......aaaacc....",
            ".......ab.ab....",
            ".......ab..ab...",
            "......bb..bbb...",
        ),
        "ankylo" to listOf(
            ".....hhhhhhh....",
            "...hhahahahahh..",
            "..aaaaaaaaaaaaa.",
            "bb.aaaaaaaaaaaea",
            "bba.ccccccccaaaa",
            "....aab..aab....",
            "...bbbb.bbbb....",
        ),
        "spino" to listOf(
            "......h.h.h.....",
            ".....hhhhhhh....",
            "....hhhhhhhhh...",
            "....aaaaaaaaaa..",
            "...aaaaaaaaaaaaa",
            "..aaaacccccaaeaa",
            ".aaa.ccccc.aaaww",
            "aa....aab.b.....",
            "......ab.ab.....",
            ".....bbb.bbb....",
        ),
        "parasaur" to listOf(
            ".........hhh....",
            "..........hhaaa.",
            "............aeaa",
            "............aaaw",
            "...........aa...",
            "..aaaaaaaaaaa...",
            ".aaaaaaaaaaaa...",
            "aa..cccccccaab..",
            "a...aab..aab....",
            "....aab..aab....",
            "...bbbb.bbbb....",
        ),
        "pachy" to listOf(
            "..........hhh...",
            ".........hhhhh..",
            ".........aaeaa..",
            ".........aaaaaw.",
            "..........aaa...",
            "a.......aaaaa...",
            "aa....aaaaaaab..",
            ".aaaaaaaaacc....",
            "...aaaaaaacc....",
            "......ab.ab.....",
            "......ab.ab.....",
            ".....bbb.bbb....",
        ),
        "grave" to listOf(
            ".....aaaaaa.....",
            "....aaaaaaaa....",
            "...aaaabbaaaa...",
            "...aaabbbbaaa...",
            "...aaaabbaaaa...",
            "...aaaabbaaaa...",
            "...aaaaaaaaaa...",
            "...aacaaaaaaa...",
            "...aaaaaaacaa...",
            "..hhhhhhhhhhhh..",
            ".hhhhhhhhhhhhhh.",
        ),
    )

    private fun p(a: Long, b: Long, c: Long, h: Long) = mapOf('a' to a, 'b' to b, 'c' to c, 'h' to h)

    // real dinosaurs (Archaeopteryx counts: it is an early bird-dinosaur)
    val SPECIES: List<Species> = listOf(
        Species("rex", "rex", "Tyrannosaurus", Rarity.COMMON, p(0xFF6D8B3A, 0xFF3F5A1F, 0xFFD9C58F, 0xFF3F5A1F)),
        Species("neck", "neck", "Brachiosaurus", Rarity.COMMON, p(0xFF26A69A, 0xFF00695C, 0xFFB2DFDB, 0xFF00695C)),
        Species("trike", "trike", "Triceratops", Rarity.COMMON, p(0xFFFF9800, 0xFFE65100, 0xFFFFE0B2, 0xFFBF360C)),
        Species("stego", "stego", "Stegosaurus", Rarity.COMMON, p(0xFF9CCC65, 0xFF558B2F, 0xFFF1F8E9, 0xFFEF5350)),
        Species("raptor", "raptor", "Velociraptor", Rarity.RARE, p(0xFFB07D4F, 0xFF6B4423, 0xFFEFD9B4, 0xFF3F6FB5)),
        Species("ankylo", "ankylo", "Ankylosaurus", Rarity.RARE, p(0xFF8D8A5A, 0xFF4E4B2C, 0xFFD8D2A8, 0xFFC2A25A)),
        Species("parasaur", "parasaur", "Parasaurolophus", Rarity.RARE, p(0xFF5C9BD1, 0xFF2F5F8F, 0xFFE3F0FB, 0xFFE2574C)),
        Species("allo", "rex", "Allosaurus", Rarity.RARE, p(0xFFD9822B, 0xFF8A4A14, 0xFFF7D9B0, 0xFF8A4A14)),
        Species("spino", "spino", "Spinosaurus", Rarity.EPIC, p(0xFF3E7D6B, 0xFF1F4A3E, 0xFFCFE3D6, 0xFFD8573C)),
        Species("pachy", "pachy", "Pachycephalosaurus", Rarity.EPIC, p(0xFFC08A4D, 0xFF7A5228, 0xFFF2DFC0, 0xFFEFE2C8)),
        Species("diplo", "neck", "Diplodocus", Rarity.LEGENDARY, p(0xFF8796A8, 0xFF4F5D6E, 0xFFDFE5EC, 0xFF4F5D6E)),
        Species("ptero", "ptero", "Archaeopteryx", Rarity.LEGENDARY, p(0xFF3A4A6B, 0xFF1F2A40, 0xFFD9A441, 0xFFC0392B)),
    )
    val GRAVE = Species("grave", "grave", "หลุมศพ", Rarity.COMMON, p(0xFF9E9E9E, 0xFF5F5F5F, 0xFF7CB342, 0xFF795548))

    private val byId = (SPECIES + GRAVE).associateBy { it.id }
    fun get(id: String): Species = byId[id] ?: SPECIES[0]
    fun exists(id: String?) = id != null && SPECIES.any { it.id == id }

    private val cache = HashMap<String, Sprite>()

    /** 18 cells wide (16 + 1-cell outline each side), trimmed to the shape's rows. */
    fun sprite(id: String): Sprite = cache.getOrPut(id) {
        val sp = get(id)
        val g = SHAPES.getValue(sp.shape)
        val pal = HashMap<Char, Color>()
        sp.pal.forEach { (k, v) -> pal[k] = Color(v) }
        pal['e'] = OUTLINE
        pal['w'] = Color.White
        val cells = HashSet<Pair<Int, Int>>()
        g.forEachIndexed { y, row -> row.forEachIndexed { x, ch -> if (ch != '.') cells.add(x + 1 to y + 1) } }
        val out = ArrayList<Triple<Int, Int, Color>>()
        val outline = HashSet<Pair<Int, Int>>()
        for ((x, y) in cells) for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
            val n = x + dx to y + dy
            if (n !in cells) outline.add(n)
        }
        outline.forEach { out.add(Triple(it.first, it.second, OUTLINE)) }
        g.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch -> if (ch != '.') out.add(Triple(x + 1, y + 1, pal[ch] ?: pal.getValue('a'))) }
        }
        Sprite(18, g.size + 2, out)
    }
}
